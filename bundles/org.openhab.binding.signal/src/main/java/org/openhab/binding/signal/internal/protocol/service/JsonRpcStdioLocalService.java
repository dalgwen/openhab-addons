package org.openhab.binding.signal.internal.protocol.service;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.signal.internal.protocol.SignalAccountManager;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@NonNullByDefault
public class JsonRpcStdioLocalService extends JsonRpcStdioAbstractService {
    private final Path binaryPath;
    private final Path userDataPath;
    private final Map<String, String> envVariable;

    @Override
    public Path getBinaryPath() {
        return binaryPath;
    }

    @Override
    public Map<String, String> getEnvVariable() {
        return envVariable;
    }

    @Override
    public Optional<Path> getUserDataPath() {
        return Optional.of(userDataPath);
    }

    public JsonRpcStdioLocalService(SignalAccountManager manager, Path userDataPath, String configuration) {
        super(manager);

        String[] binaryPathParts = configuration.trim().split(" ");
        String binary = binaryPathParts[binaryPathParts.length - 1];
        Map<String, String> parsedEnv = new HashMap<>();
        for (int i = 0; i < binaryPathParts.length - 1; i++) {
            String arg = binaryPathParts[i];
            if (arg.contains("=")) {
                String[] keyValue = arg.split("=", 2);
                if (keyValue.length > 1) {
                    parsedEnv.put(keyValue[0], keyValue[1]);
                }
            }
        }
        this.envVariable = parsedEnv;

        this.binaryPath = Path.of(binary);
        this.userDataPath = userDataPath;
    }
}
