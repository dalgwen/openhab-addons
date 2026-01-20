/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.signal.internal.downloader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.openhab.binding.signal.internal.protocol.service.JsonRpcStdioManagedService.SIGNAL_CLI_RELEASE_DIRECTORY;

/**
 * Utility class for managing version download and use
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class VersionManager {

    private final Logger logger = LoggerFactory.getLogger(VersionManager.class);

    private static final String VERSIONING_FILE_NAME = "current_version.txt";
    private static final String BASE_URL = "https://openhab.jfrog.io/artifactory/api/storage/libs-runtime-deps/AsamK/signal-cli/";
    private final Path signalDataPath;
    private final HttpClient httpClient;

    public VersionManager(Path signalDataPath, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.signalDataPath = signalDataPath;
    }

    private String getSignalCliBinaryName() {
        ArchitectureDetector.Architecture archi = ArchitectureDetector.detect();
        if (archi == ArchitectureDetector.Architecture.WINDOWS_X86_64) {
            return "signal-cli.bat";
        } else {
            return "signal-cli";
        }
    }

    public boolean isVersionReady(String version) {
        logger.debug("Checking if version {} is ready", version);
        Path versioningFilePath = signalDataPath.resolve(VERSIONING_FILE_NAME);
        Path signalBinaryPath = signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY).resolve("bin").resolve(getSignalCliBinaryName());
        if (versioningFilePath.toFile().exists() && signalBinaryPath.toFile().exists()) {
            String currentVersion = null;
            try {
                currentVersion = Files.readString(versioningFilePath);
            } catch (IOException e) {
                logger.error("Cannot read version file", e);
                return false;
            }
            boolean versionEquals = currentVersion.equals(version);
            if (!versionEquals) {
                logger.debug("Version {} is not ready, current version is {}", version, currentVersion);
            }
            return versionEquals;
        }
        logger.debug("Cannot found signal-cli binary {} and version file {}", signalBinaryPath, versioningFilePath);
        return false;
    }


    public void manageWantedVersion(String version) throws VersionDownloaderException {
        if (isVersionReady(version)) {
            return;
        }
        try {
            moveOldVersion();
        } catch (IOException e) {
            throw new VersionDownloaderException("Cannot move old version", e);
        }
        try {
            downloadVersion(version);
        } catch (VersionDownloaderException e) {
            try {
                restoreOldVersion();
            } catch (IOException ex) {
                throw new VersionDownloaderException("Cannot restore old version {}", e);
            }
            throw e;
        }
        try {
            Files.writeString(signalDataPath.resolve(VERSIONING_FILE_NAME), version, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new VersionDownloaderException("Cannot write version file !?", e);
        }
        try {
            deleteOldVersion();
        } catch (IOException e) {
            logger.error("Cannot delete old version !", e);
        }
        if (!isVersionReady(version)) {
            throw new VersionDownloaderException("Version downloaded but not ready: " + version);
        }
    }

    private void moveOldVersion() throws IOException {
        logger.debug("Moving old version");
        Path releaseDir = signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY);
        deleteOldVersion();
        if (releaseDir.toFile().exists()) {
            Files.move(releaseDir, signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY + ".old"));
        }
    }

    private void restoreOldVersion() throws IOException {
        logger.info("Restoring old version");
        Path releaseDir = signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY + ".old");
        if (releaseDir.toFile().exists()) {
            Files.move(releaseDir, signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY));
        }
    }

    private void deleteOldVersion() throws IOException {
        Path oldVersionDir = signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY + ".old");
        if (oldVersionDir.toFile().exists()) {
            try (Stream<Path> walk = Files.walk(oldVersionDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete {}", path, e);
                            }
                        });
            }
        }
    }

    public void downloadVersion(String version) throws VersionDownloaderException {
        List<ArtifactoryChild> releases = null;
        try {
            releases = getChildren(version);
            ArtifactoryChild release = releases.stream().filter(ar -> ar.uri().contains("signal-cli")).findFirst().orElseThrow(() -> new VersionDownloaderException("No releases of version " + version + " found"));
            ArchitectureDetector.Architecture archi = ArchitectureDetector.detect();
            ArtifactoryChild nativeLib = releases.stream().filter(ar -> ar.uri().contains(archi.downloadDiscriminant)).findFirst().orElseThrow(() -> new VersionDownloaderException("No signal lib found for arch " + archi + "/" + archi.downloadDiscriminant));
            downloadAndUncompress(release, signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY));
            downloadAndUncompress(nativeLib, signalDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY).resolve("bin"));
        } catch (ExecutionException | InterruptedException | TimeoutException | IOException e) {
            throw new VersionDownloaderException("Cannot download version " + version, e);
        }

    }

    private List<ArtifactoryChild> getChildren(String version) throws ExecutionException, InterruptedException, TimeoutException, VersionDownloaderException {
        String url = BASE_URL + version;
        ContentResponse response = httpClient.GET(url);

        if (response.getStatus() != 200) {
            throw new VersionDownloaderException("Cannot download version " + version + ". Status code to " + url + " is " + response.getStatus());
        }

        String json = response.getContentAsString();

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray children = root.getAsJsonArray("children");

        List<ArtifactoryChild> result = new ArrayList<>();
        for (JsonElement element : children) {
            JsonObject child = element.getAsJsonObject();
            String uri = child.get("uri").getAsString();
            result.add(new ArtifactoryChild(uri, url + uri));
        }
        return result;
    }

    private void downloadAndUncompress(ArtifactoryChild child, Path destinationDir) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        InputStreamResponseListener listener = new InputStreamResponseListener();
        String downloadUri = toDownloadUri(child.fullUri());
        httpClient.newRequest(downloadUri).followRedirects(true).send(listener);
        listener.get(60, TimeUnit.SECONDS);
        try (InputStream downloadStream = listener.getInputStream();
             GZIPInputStream gzipStream = new GZIPInputStream(downloadStream);
             TarArchiveInputStream tarStream = new TarArchiveInputStream(gzipStream)) {
            TarArchiveEntry entry = tarStream.getNextEntry();
            while (entry != null) {
                Path targetPath = destinationDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (OutputStream out = Files.newOutputStream(targetPath)) {
                        tarStream.transferTo(out);
                    }
                    // Set file permissions from tar entry if POSIX is supported
                    if (targetPath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                        try {
                            int mode = entry.getMode();
                            Set<PosixFilePermission> permissions = modeToPermissions(mode);
                            Files.setPosixFilePermissions(targetPath, permissions);
                        } catch (UnsupportedOperationException e) {
                            logger.debug("Cannot set POSIX permissions on {}", targetPath);
                        }
                    }
                }
                entry = tarStream.getNextEntry();
            }
        }
    }

    private Set<PosixFilePermission> modeToPermissions(int mode) {
        StringBuilder perms = new StringBuilder(9);
        // Owner
        perms.append((mode & 0400) != 0 ? 'r' : '-');
        perms.append((mode & 0200) != 0 ? 'w' : '-');
        perms.append((mode & 0100) != 0 ? 'x' : '-');
        // Group
        perms.append((mode & 0040) != 0 ? 'r' : '-');
        perms.append((mode & 0020) != 0 ? 'w' : '-');
        perms.append((mode & 0010) != 0 ? 'x' : '-');
        // Others
        perms.append((mode & 0004) != 0 ? 'r' : '-');
        perms.append((mode & 0002) != 0 ? 'w' : '-');
        perms.append((mode & 0001) != 0 ? 'x' : '-');
        return PosixFilePermissions.fromString(perms.toString());
    }

    private String toDownloadUri(String apiUri) throws ExecutionException, InterruptedException, TimeoutException {
        ContentResponse response = httpClient.GET(apiUri);
        String json = response.getContentAsString();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.get("downloadUri").getAsString();
    }

    public record ArtifactoryChild(String uri, String fullUri) {
    }
}

