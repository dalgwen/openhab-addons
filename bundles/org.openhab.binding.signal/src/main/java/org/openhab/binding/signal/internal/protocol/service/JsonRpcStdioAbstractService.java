package org.openhab.binding.signal.internal.protocol.service;
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
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.SignalAccountManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public abstract class JsonRpcStdioAbstractService extends JsonRpcAbstractSignalService {

    private final Logger logger = LoggerFactory.getLogger(JsonRpcStdioAbstractService.class);

    private static final String[] DEFAULT_ARGS = new String[] {"--trust-new-identities", "always", "-o", "json", "jsonRpc", "--send-read-receipts", "--ignore-stories"};

    @Nullable
    private Process process;
    @Nullable
    private BufferedReader reader;
    @Nullable
    private BufferedReader errorReader;
    @Nullable
    private PrintWriter writer;

    public JsonRpcStdioAbstractService(SignalAccountManager signalAccountManager) {
        super(signalAccountManager);
    }

    public abstract Path getBinaryPath();
    public abstract Optional<Path> getUserDataPath();
    public abstract Map<String, String> getEnvVariable();

    @Override
    public void internalStart() throws IOException, UnrecoverableException {
        Process processLocal = process;
        if (reader != null && writer != null && processLocal != null && processLocal.isAlive()) {
            return; // already running
        }

        List<String> args = new ArrayList<>();
        args.add(getBinaryPath().toAbsolutePath().toString());
        getUserDataPath().ifPresent(userDataPath -> {
            args.add("--config");
            args.add(userDataPath.toAbsolutePath().toString());
        });
        args.addAll(List.of(DEFAULT_ARGS));

        logger.debug("Starting process with args: {}", args);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().putAll(getEnvVariable());
//        pb.redirectErrorStream(true);
        try {
            processLocal = pb.start();
        } catch (IOException e) {
            throw new UnrecoverableException("Cannot start process", e);
        }
        process = processLocal;

        // Check if the process has already exited unexpectedly
        try {
            Thread.sleep(4000); // Give the process a moment to potentially crash
            if (!processLocal.isAlive()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(processLocal.getErrorStream()))) {
                    String errorOutput = reader.lines().collect(Collectors.joining("\n"));
                    throw new UnrecoverableException("Process exited unexpectedly with code " + processLocal.exitValue() + ". Error output: " + errorOutput);
                } catch (IOException e) {
                    throw new UnrecoverableException("Process exited immediately after start with code " + processLocal.exitValue());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnrecoverableException("Interrupted while checking process status", e);
        }

        reader = new BufferedReader(new InputStreamReader(processLocal.getInputStream()));
        errorReader = new BufferedReader(new InputStreamReader(processLocal.getErrorStream()));
        writer = new PrintWriter(processLocal.getOutputStream(), true);
    }

    @Override
    public void internalStop() {
        Process processLocal = process;
        if (processLocal != null) {
            processLocal.destroy();
            try {
                if (!processLocal.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    processLocal.destroyForcibly();
                }
            } catch (InterruptedException e) {
                processLocal.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        BufferedReader readerLocal = reader;
        if (readerLocal != null) {
            try {
                readerLocal.close();
            } catch (IOException e) {
                logger.debug("Error closing reader: {}", e.getMessage());
            }
        }
        PrintWriter writerLocal = writer;
        if (writerLocal != null) {
            writerLocal.close();
        }

        process = null;
        reader = null;
        writer = null;
    }
    
    @Override
    public Optional<String> readErrorLines() throws IOException {
        BufferedReader readerLocal = errorReader;
        if (readerLocal == null) {
            return Optional.of("Process not started.");
        } else if (readerLocal.ready()) {
            List<String> result = new ArrayList<>();
            while (readerLocal.ready()) {
                result.add(readerLocal.readLine());
            }
            return Optional.of(String.join("\n", result));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public @Nullable String readLine() throws IOException {
        BufferedReader readerLocal = reader;
        if (readerLocal == null) {
            throw new IOException("Process not started, cannot read");
        }
        return readerLocal.readLine();
    }

    @Override
    public void writeLine(String line) throws IOException {
        PrintWriter writerLocal = writer;
        if (writerLocal == null) {
            throw new IOException("Process not started, cannot write");
        }
        writerLocal.println(line);
    }
}
