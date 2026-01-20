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
package org.openhab.binding.signal.internal.protocol.service;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.SignalAccountManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Optional;

/**
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class JsonRpcTcpService extends JsonRpcAbstractSignalService {

    private final Logger logger = LoggerFactory.getLogger(JsonRpcTcpService.class);

    @Nullable
    private Socket socket;
    @Nullable
    private BufferedReader bufferedReader;
    @Nullable
    private PrintWriter printWriter;
    @Nullable
    private InputStreamReader inputStreamReader;

    private final String hostname;
    private final int port;

    public JsonRpcTcpService(SignalAccountManager manager, String configuration) {
        super(manager);
        this.hostname = getNetworkAddress(configuration);
        this.port = getNetworkPort(configuration);
    }


    /**
     * Extract the network address from the configuration string (e.g., "localhost:5467" returns "localhost")
     *
     * @return the network address, or empty string if no colon is found
     */
    public static String getNetworkAddress(String configuration) {
        int colonIndex = configuration.indexOf(':');
        if (colonIndex > 0) {
            return configuration.substring(0, colonIndex);
        }
        return configuration;
    }

    /**
     * Extract the network port from the configuration string (e.g., "localhost:5467" returns 5467)
     *
     * @return the network port, or 0 if no valid port is found
     */
    public static int getNetworkPort(String configuration) {
        int colonIndex = configuration.indexOf(':');
        if (colonIndex > 0 && colonIndex < configuration.length() - 1) {
            try {
                return Integer.parseInt(configuration.substring(colonIndex + 1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }


    @Override
    public void internalStart() throws IOException {
        if (bufferedReader != null && printWriter != null) {
            return;
        }
        var socketLocal = new Socket(hostname, port);
        this.inputStreamReader = new InputStreamReader(socketLocal.getInputStream());
        this.bufferedReader = new BufferedReader(inputStreamReader);
        this.printWriter = new PrintWriter(socketLocal.getOutputStream(), true);
        this.socket = socketLocal;
    }

    @Override
    public void internalStop() {
        BufferedReader bufferedReaderLocal = bufferedReader;
        if (bufferedReaderLocal != null) {
            try { bufferedReaderLocal.close();} catch (IOException ignored) {}
        }
        InputStreamReader inLocal = inputStreamReader;
        if (inLocal != null) {
            try { inLocal.close();} catch (IOException ignored) {}
        }
        PrintWriter printWriterLocal = printWriter;
        if (printWriterLocal != null) {
            printWriterLocal.close();
        }
        Socket socketLocal = socket;
        if (socketLocal != null) {
            try {
                socketLocal.close();
            } catch (IOException e) {
                logger.debug("Cannot close socket (may be not open ?): {}", e.getMessage());
            }
        }
        this.bufferedReader = null;
        this.printWriter = null;
    }


    @Override
    public @Nullable String readLine() throws IOException {
        BufferedReader bufferedReaderLocal = bufferedReader;
        if (bufferedReaderLocal == null) {
            throw new IOException("Socket not connected, cannot read");
        }
        return bufferedReaderLocal.readLine();
    }

    @Override
    public void writeLine(String line) throws IOException {
        PrintWriter printWriterLocal = printWriter;
        if (printWriterLocal == null) {
            throw new IOException("Socket not connected, cannot write");
        }
        printWriterLocal.println(line);
    }

    @Override
    public Optional<String> readErrorLines() {
        return Optional.empty();
    }
}
