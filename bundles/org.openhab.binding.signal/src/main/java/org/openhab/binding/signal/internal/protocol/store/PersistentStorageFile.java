/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Persistent storage backed by a directory
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class PersistentStorageFile implements PersistentStorage {

    public final Path rootPath;

    private Map<String, String> cacheKeysInRoot = new HashMap<String, String>();

    public PersistentStorageFile(Path rootPath) {
        super();
        this.rootPath = rootPath;
        if (!rootPath.toFile().exists()) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                throw new PersistenceException(
                        "Cannot create signal persistent storage for the account " + rootPath.toString(), e);
            }
        }
        if (!(rootPath.toFile().isDirectory() && rootPath.toFile().canRead() && rootPath.toFile().canWrite())) {
            throw new PersistenceException("The path is either not a directory or the service can't access it");
        }
    }

    private Path getFile(StoreType store, String key) {
        Path filePath = getDirectory(store).resolve(sanitize(key));
        // check if the resulting file is under the root path
        if (!filePath.toAbsolutePath().normalize().startsWith(rootPath)) {
            throw new PersistenceException("Key is dangerous and points to another directory");
        }
        return filePath;
    }

    private Path getDirectory(StoreType store) {
        Path directory = rootPath.resolve(store.name());
        if (!directory.toFile().exists()) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new PersistenceException("Cannot create directory for store " + store.name(), e);
            }
        }
        return directory;
    }

    @Override
    public void deleteStore(StoreType store) {
        File[] allContents = getDirectory(store).toFile().listFiles();
        if (allContents != null) {
            try {
                for (File file : allContents) {
                    Files.deleteIfExists(file.toPath());
                }
            } catch (IOException e) {
                throw new PersistenceException("Cannot delete store " + store.name(), e);
            }
        }
    }

    @Override
    public void delete(StoreType store, String key) {
        Path file = getFile(store, sanitize(key));
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new PersistenceException("Cannot delete a file for the key " + key + " in store " + store.name());
        }
    }

    @Override
    public @Nullable Map<String, String> load(StoreType store) {
        Map<String, String> storeMap = new HashMap<>();
        for (File file : getDirectory(store).toFile().listFiles()) {
            String key = restoreKeyName(file.getName());
            try {
                String value = Files.readString(file.toPath());
                storeMap.put(key, value);
            } catch (IOException e) {
                throw new PersistenceException("Cannot load store " + store.name(), e);
            }
        }
        return storeMap;
    }

    @Override
    public void save(StoreType store, String key, String value) {
        Path path = getFile(store, sanitize(key));
        try {
            Files.write(path, value.getBytes());
        } catch (IOException e) {
            throw new PersistenceException("Cannot save file for key " + key + " in store " + store.name(), e);
        }
    }

    @Override
    public void save(String key, String value) {
        Path path = rootPath.resolve(sanitize(key));
        cacheKeysInRoot.put(key, value);
        try {
            Files.write(path, value.getBytes());
        } catch (IOException e) {
            throw new PersistenceException("Cannot write a file for storing the key " + key);
        }
    }

    @Override
    @Nullable
    public String get(String key) {
        // first try in cache :
        String value = cacheKeysInRoot.get(key);
        if (value != null) {
            return value;
        }
        try { // second try in files storage
            Path path = rootPath.resolve(sanitize(key));
            if (path.toFile().exists()) {
                value = Files.readString(rootPath.resolve(key));
                cacheKeysInRoot.put(key, value);
                return value;
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new PersistenceException("Cannot get key " + key, e);
        }
    }

    @Override
    public void deleteEverything() {
        boolean deleteOk = deleteDirectory(rootPath.toFile());
        if (!deleteOk) {
            throw new PersistenceException("Cannot delete the directory " + rootPath.toString());
        }
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        boolean deleteOk = true;
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteOk &= deleteDirectory(file);
            }
        }
        // delete only under root path
        if (directoryToBeDeleted.toPath().toAbsolutePath().normalize().startsWith(rootPath)) {
            return deleteOk &= directoryToBeDeleted.delete();
        }
        return false;
    }

    /**
     * Replace forbidden character in file name
     *
     * @param key The key to sanitize for a file usage
     * @return a proper file name
     */
    private static String sanitize(String key) {
        return key.replaceAll("\"", "###");
    }

    /**
     * Restore the original key name
     *
     * @param fileName the filename possibly previously sanitized
     * @return the original key
     */
    private static String restoreKeyName(String fileName) {
        return fileName.replaceAll("###", "\"");
    }
}
