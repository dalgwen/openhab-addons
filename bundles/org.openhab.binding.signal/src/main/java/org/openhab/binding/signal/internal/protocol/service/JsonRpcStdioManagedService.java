package org.openhab.binding.signal.internal.protocol.service;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.signal.internal.downloader.VersionManager;
import org.openhab.binding.signal.internal.protocol.SignalAccountManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@NonNullByDefault
public class JsonRpcStdioManagedService extends JsonRpcStdioAbstractService {

    private static final String DEFAULT_VERSION = "0.14.3";
    public static final String SIGNAL_CLI_RELEASE_DIRECTORY = "signal-cli-release";
    private final Path userDataPath;
    private final VersionManager versionManager;
    private final String version;
    private final Map<String, String> envVariable;

    public JsonRpcStdioManagedService(SignalAccountManager manager, Path userDataPath, String signalCliConnectionConfiguration, HttpClient httpClient) {
        super(manager);
        this.userDataPath = userDataPath;
        this.versionManager = new VersionManager(userDataPath, httpClient);

        String[] binaryPathParts = signalCliConnectionConfiguration.trim().split(" ");
        String lastNonEnvArg = "";
        Map<String, String> envVar = new HashMap<>();
        for (String arg : binaryPathParts) {
            if (arg.contains("=")) {
                String[] keyValue = arg.split("=", 2);
                if (keyValue.length > 1) {
                    envVar.put(keyValue[0], keyValue[1]);
                }
            } else {
                lastNonEnvArg = arg;
            }
        }
        version = lastNonEnvArg.trim().isEmpty() || lastNonEnvArg.trim().equals("auto") ? DEFAULT_VERSION : lastNonEnvArg.trim();
        this.envVariable = envVar;
    }

    @Override
    public int waitTime() {
        return 60; // arbitrary wait for download, etc.
    }

    @Override
    public void internalStart() throws IOException, UnrecoverableException {
        versionManager.manageWantedVersion(version);
        super.internalStart();
    }

    @Override
    public Path getBinaryPath() {
        return userDataPath.resolve(SIGNAL_CLI_RELEASE_DIRECTORY).resolve(versionManager.getSignalCliBinaryName());
    }


    @Override
    public Optional<Path> getUserDataPath() {
        return Optional.of(userDataPath);
    }

    @Override
    public Map<String, String> getEnvVariable() {
        return envVariable;
    }
}
