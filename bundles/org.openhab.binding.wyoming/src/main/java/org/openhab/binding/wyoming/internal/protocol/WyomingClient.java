/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.wyoming.internal.protocol;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingMessageHeader;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingMessageListener;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingRawMessage;
import org.openhab.binding.wyoming.internal.protocol.message.data.InfoData;
import org.openhab.binding.wyoming.internal.protocol.message.data.WyomingData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WyomingClient is responsible for managing the communication with a Wyoming server.
 * It handles sending and receiving messages according to the Wyoming protocol, as well as
 * maintaining connection and resource management.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class WyomingClient {

    private static final Logger logger = LoggerFactory.getLogger(WyomingClient.class);
    public static final int MB = 1024 * 1024;

    private final String host;
    private final int port;
    @Nullable
    private InfoData infoData;
    @Nullable
    private Socket socket;
    @Nullable
    private InputStream in;
    @Nullable
    private BufferedOutputStream out;
    private final ReentrantLock running = new ReentrantLock();
    private boolean shouldRun = false;
    private WyomingStateListener.WyomingState currentState = WyomingStateListener.WyomingState.Disconnected;

    public static final Charset ENCODING = StandardCharsets.ISO_8859_1;

    private final Map<WyomingMessageListener, Set<MessageType>> listeners = new ConcurrentHashMap<>();
    private final Set<WyomingStateListener> stateListeners = Collections.synchronizedSet(new HashSet<>());

    @Nullable
    private Thread messagesReadingThread;

    public WyomingClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Probes the Wyoming server by sending a Describe message and waiting for a response.
     * This method sends a Describe message and waits for the
     * server's response within the specified timeout. If no response is received within
     * the timeout or if any error occurs during the process, a {@code WyomingProtocolException}
     * is thrown.
     *
     * @throws WyomingProtocolException If no info data is received within the timeout.
     */
    public InfoData probe() throws WyomingProtocolException {
        WyomingRawMessage askDescribe = new WyomingRawMessage(MessageType.Describe);
        try {
            var infoDataLocal = askAndWaitResponse(askDescribe, MessageType.Info, 500).decodeData(InfoData.class);
            infoData = infoDataLocal;
            return infoDataLocal;
        } catch (IOException e) {
            notifyStateListeners(WyomingStateListener.WyomingState.ProtocolError,
                    "Cannot probe the wyoming component: " + e.getMessage());
            throw new WyomingProtocolException("Cannot connect to probe the wyoming component", e);
        }
    }

    /**
     * Get detailed information data. Probe if necessary
     * 
     * @return Information data
     * @throws WyomingProtocolException If we cannot get infodata
     */
    public InfoData getInfoData() throws WyomingProtocolException {
        @Nullable
        InfoData infoDataLocal = infoData;
        return infoDataLocal == null ? probe() : infoDataLocal;
    }

    public void registerStateListener(WyomingStateListener stateListener) {
        stateListeners.add(stateListener);
    }

    public void unregisterStateListener(WyomingStateListener stateListener) {
        stateListeners.remove(stateListener);
    }

    private void notifyStateListeners(WyomingStateListener.WyomingState newState) {
        notifyStateListeners(newState, null);
    }

    private void notifyStateListeners(WyomingStateListener.WyomingState newState, @Nullable String detailledMessage) {
        if (currentState != newState) {
            currentState = newState;
            synchronized (stateListeners) {
                for (WyomingStateListener listener : stateListeners) {
                    listener.onState(newState, detailledMessage);
                }
            }
        }
    }

    /**
     * Connect to the wyoming partner and start a thread to read messages
     *
     * @throws IOException When cannot connect to host
     */
    public synchronized void connectAndListen(boolean forceReconnect) throws IOException, WyomingProtocolException {

        shouldRun = true;

        Thread messagesReadingThreadLocal = messagesReadingThread;
        if (messagesReadingThreadLocal != null && messagesReadingThreadLocal.isAlive() && !forceReconnect) {
            return;
        }

        connect();
        CountDownLatch ready = new CountDownLatch(1);
        messagesReadingThreadLocal = new Thread(() -> readAndProcessMessages(ready));
        messagesReadingThread = messagesReadingThreadLocal;
        messagesReadingThreadLocal.start();
        try {
            if (!ready.await(200, TimeUnit.MILLISECONDS)) {
                notifyStateListeners(WyomingStateListener.WyomingState.ProtocolError,
                        "Timeout while waiting for receiving thread");
                throw new WyomingProtocolException("Timeout while waiting for receiving thread");
            }
            getInfoData();
        } catch (InterruptedException ignored) {
        }
    }

    private void connect() throws IOException {
        disconnect();
        logger.info("Connecting to {}:{}", host, port);
        var socketLocal = new Socket(host, port);
        socket = socketLocal;
        in = socketLocal.getInputStream();
        out = new BufferedOutputStream(socketLocal.getOutputStream());
    }

    /**
     * Disconnect and don't restart
     */
    public synchronized void disconnectAndStop() {
        this.shouldRun = false;
        disconnect();
    }

    /**
     * Release resources
     */
    private void disconnect() {

        Optional.ofNullable(messagesReadingThread).ifPresent(Thread::interrupt);

        messagesReadingThread = null;
        notifyStateListeners(WyomingStateListener.WyomingState.Disconnected);

        Optional.ofNullable(socket).ifPresent((localSock -> {
            try {
                if (!localSock.isClosed()) {
                    localSock.close();
                }
            } catch (IOException e) {
                logger.debug("Cannot close socket");
            }
        }));
        socket = null;

        Optional.ofNullable(in).ifPresent(localIn -> {
            try {
                localIn.close();
            } catch (IOException e) {
                logger.debug("Cannot close input stream");
            }
        });
        in = null;

        Optional.ofNullable(out).ifPresent(localOut -> {
            try {
                localOut.close();
            } catch (IOException e) {
                logger.debug("Cannot close output stream");
            }
        });
        out = null;
    }

    private WyomingRawMessage readMessage() throws IOException, WyomingProtocolException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        int nextByte;
        InputStream localIn = Optional.ofNullable(in)
                .orElseThrow(() -> new WyomingProtocolException("Cannot read, input stream is null"));
        while ((nextByte = localIn.read()) != -1) {
            if (nextByte == '\n') { // End of line
                break;
            }
            lineBuffer.write(nextByte);
            if (lineBuffer.size() > MB) {
                throw new WyomingProtocolException("Line too long");
            }
        }
        String line = lineBuffer.toString(ENCODING);
        line = validateMessageLine(line);
        logger.debug("Message received {}", line);
        WyomingMessageHeader messageHeader = WyomingMessageHeader.from(line);
        WyomingRawMessage wyomingMessage = new WyomingRawMessage(messageHeader);
        Integer dataLength = messageHeader.getDataLength();
        if (dataLength != null && dataLength > 0) {
            logger.debug("Reading additional data of length {}", dataLength);
            byte[] additionalDataBytes = new byte[dataLength];

            int bytesRead = localIn.readNBytes(additionalDataBytes, 0, dataLength);
            if (bytesRead != dataLength) {
                logger.error("Expected to read {} bytes for data, but read {}", dataLength, bytesRead);
            }
            String additionalDataString = new String(additionalDataBytes, ENCODING);
            logger.trace("Additional data: {}", additionalDataString);
            Map<String, Object> additionalData = GsonUtils.fromJsonToMap(additionalDataString);
            wyomingMessage.setAdditionalData(additionalData);
        }
        Integer payloadLength = messageHeader.getPayloadLength();
        if (payloadLength != null && payloadLength > 0) {
            logger.debug("Reading payload of length {}", payloadLength);
            byte[] payloadDataBytes = new byte[payloadLength];
            int bytesRead = localIn.readNBytes(payloadDataBytes, 0, payloadLength);
            if (bytesRead != payloadLength) {
                logger.error("Expected to read {} bytes for payload, but read {}", payloadLength, bytesRead);
            }
            wyomingMessage.setPayload(payloadDataBytes);
        }
        return wyomingMessage;
    }

    private static String validateMessageLine(String line) throws WyomingProtocolException {
        if (line.isEmpty()) {
            throw new WyomingProtocolException("Empty line");
        }
        if (line.charAt(line.length() - 1) != '}') {
            throw new WyomingProtocolException("Message not terminated by '}'");
        }
        if (line.charAt(0) != '{') {
            int lastBraceIndex = line.lastIndexOf("{\"type");
            if (lastBraceIndex == -1) {
                throw new WyomingProtocolException("Message does not contain '{'");
            }
            logger.error("Message does not start with '{'. Ditching some bytes");
            line = line.substring(lastBraceIndex);
        }
        return line;
    }

    public void sendMessage(MessageType messageType) throws IOException {
        sendMessage(messageType, null, null);
    }

    public void sendMessage(MessageType messageType, @Nullable WyomingData data) throws IOException {
        sendMessage(messageType, data, null);
    }

    public void sendMessage(MessageType messageType, @Nullable WyomingData data, byte @Nullable [] payload)
            throws IOException {
        WyomingMessageHeader wyomingMessageHeader = new WyomingMessageHeader(messageType);
        WyomingRawMessage wyomingRawMessage = new WyomingRawMessage(wyomingMessageHeader, data, payload);
        sendMessage(wyomingRawMessage);
    }

    public synchronized void sendMessage(WyomingRawMessage message) throws IOException {
        logger.debug("Sending message: {}, {}", message.getHeader().getType(), message.getMergedData());
        byte[] dataToSend = message.toBytes();
        BufferedOutputStream localOut = Optional.ofNullable(out)
                .orElseThrow(() -> new IOException("Cannot send, output stream is null"));
        localOut.write(dataToSend);
        localOut.flush();
    }

    /**
     * Register a listener for all types of messages
     *
     * @param listener The listener will receive relevant messages
     */
    public void registerAll(WyomingMessageListener listener) {
        register(new HashSet<>(Arrays.asList(MessageType.values())), listener);
    }

    /**
     * Register a listener for a set of message type
     *
     * @param types A set of message types
     * @param listener This listener will receive messages
     */
    public void register(Set<MessageType> types, WyomingMessageListener listener) {
        listeners.put(listener, types);
    }

    public WyomingRawMessage askAndWaitResponse(WyomingRawMessage message, MessageType responseTypeWanted,
            int maxWaitMs) throws IOException, WyomingProtocolException {

        // use the raw array quirk as a mutable container
        WyomingRawMessage[] messageReceived = new WyomingRawMessage[1];
        CountDownLatch responseLatch = new CountDownLatch(1);
        WyomingMessageListener responseHandler = new WyomingMessageListener() {
            @Override
            public void onMessage(WyomingRawMessage message) {
                messageReceived[0] = message;
                responseLatch.countDown();
                unregister(this);
            }
        };
        register(Collections.singleton(responseTypeWanted), responseHandler);
        sendMessage(message);
        try {
            if (responseLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)) {
                return messageReceived[0];
            } else {
                throw new WyomingProtocolException("Message not received in due time");
            }
        } catch (InterruptedException e) {
            throw new WyomingProtocolException("Interrupted during wait for response message");
        }
    }

    /**
     * Remove a listener
     *
     * @param listener The listener to remove
     */
    public void unregister(WyomingMessageListener listener) {
        listeners.remove(listener);
    }

    /**
     * Read messages in a loop until shouldRun is false or there is an Exception
     */
    private void readAndProcessMessages(@Nullable CountDownLatch ready) {
        if (running.isLocked()) {
            return;
        }
        try {
            running.lock();
            boolean shouldReconnect = false;
            boolean firstConnection = true;
            while (shouldRun) {
                try {
                    if (shouldReconnect) {
                        connect();
                        shouldReconnect = false;
                        logger.info("Reconnected to {}", host);
                    }
                    if (firstConnection && ready != null) {
                        firstConnection = false;
                        // optimistic behaviour for the first connection:
                        notifyStateListeners(WyomingStateListener.WyomingState.Ready);
                        ready.countDown();
                    }
                    WyomingRawMessage message = readMessage();
                    notifyStateListeners(WyomingStateListener.WyomingState.Ready);
                    for (Map.Entry<WyomingMessageListener, Set<MessageType>> entry : listeners.entrySet()) {
                        WyomingMessageListener listener = entry.getKey();
                        Set<MessageType> types = entry.getValue();
                        if (types.contains(message.getHeader().getType())) {
                            listener.onMessage(message);
                        }
                    }
                } catch (WyomingProtocolException e) {
                    notifyStateListeners(WyomingStateListener.WyomingState.ProtocolError, e.getMessage());
                    if (!shouldRun) {
                        return;
                    }
                    @Nullable
                    String jsonExc = e.getJson();
                    if (jsonExc != null) {
                        logger.warn("Error decoding message {}, cause {} ", jsonExc, e.getMessage());
                    }
                } catch (IOException e) {
                    if (!shouldRun) {
                        return;
                    }
                    logger.error("Error reading message", e);
                    shouldReconnect = true;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        } finally {
            running.unlock();
        }
    }
}
