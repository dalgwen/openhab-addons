package org.openhab.binding.wyoming.internal.protocol;

import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingMessageHeader;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingMessageListener;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingRawMessage;
import org.openhab.binding.wyoming.internal.protocol.message.data.InfoData;
import org.openhab.binding.wyoming.internal.protocol.message.data.WyomingData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class WyomingClient {

    private static final Logger logger = LoggerFactory.getLogger(WyomingClient.class);
    public static final int MB = 1024 * 1024;

    private final String host;
    private final int port;
    private InfoData infoData;
    private Socket socket;
    private InputStream in;
    private BufferedOutputStream out;
    private final ReentrantLock running = new ReentrantLock();
    private boolean shouldRun = false;

    public static final Charset ENCODING = StandardCharsets.ISO_8859_1;

    private final Map<WyomingMessageListener, Set<MessageType>> listeners = new ConcurrentHashMap<>();
    private Thread messagesReadingThread;

    public WyomingClient(String host, int port) {
        this(host, port, null);
    }

    public WyomingClient(String host, int port, InfoData infoData) {
        this.host = host;
        this.port = port;
        this.infoData = infoData;
    }

    /**
     * Probes the Wyoming server by sending a describe message and waiting for a response.
     * This method sends a describe message, and waits for the
     * server's response within the specified timeout. If no response is received within
     * the timeout or if any error occurs during the process, a {@code WyomingProtocolException}
     * is thrown.
     *
     * @throws WyomingProtocolException If no info data is received within the timeout.
     * @throws IOException If connection fails
     */
    public void probe() throws WyomingProtocolException, IOException {
        WyomingRawMessage askDescribe = new WyomingRawMessage(MessageType.Describe);
        infoData = askAndWaitResponse(askDescribe, MessageType.Info, 500).decodeData(InfoData.class);
        if (infoData == null) {
            throw new WyomingProtocolException("Never received infoData during the allocated time");
        }
    }

    /**
     * Get detailed information data
     * @return Information data
     * @throws WyomingProtocolException If info data is not ready
     */
    public InfoData getInfoData() throws WyomingProtocolException {
        if (infoData == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            if (infoData == null) {
                throw new WyomingProtocolException("Info data not yet received");
            }
        }
        return infoData;
    }

    /**
     * Connect to the wyoming partner and start a thread to read messages
     *
     * @throws IOException When cannot connect to host
     */
    public synchronized void connectAndListen() throws IOException, WyomingProtocolException {
        connect();
        shouldRun = true;
        messagesReadingThread = new Thread(this::readAndProcessMessages);
        messagesReadingThread.start();
        probe();
    }

    private void connect() throws IOException {
        disconnect();
        logger.info("Connecting to {}:{}", host, port);
        socket = new Socket(host, port);
        in = socket.getInputStream();
        out = new BufferedOutputStream(socket.getOutputStream());
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
        Thread localThread = messagesReadingThread;
        if (localThread != null) {
            localThread.interrupt();
        }
        messagesReadingThread = null;
        try {
            Socket localSocket = socket;
            if (localSocket != null && !localSocket.isClosed()) {
                localSocket.close();
            }
        } catch (IOException e) {
            logger.debug("Cannot close socket");
        }
        socket = null;
        try {
            InputStream localIn = in;
            if (localIn != null) {
                localIn.close();
            }
        } catch (IOException e) {
            logger.debug("Cannot close input stream");
        }
        in = null;
        BufferedOutputStream localOut = out;
        try {
            if (localOut != null) {
                localOut.close();
            }
        } catch (IOException e) {
            logger.debug("Cannot close output stream");
        }
        out = null;
    }

    private WyomingRawMessage readMessage() throws IOException {
        try {
            ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
            int nextByte;
            while ((nextByte = in.read()) != -1) {
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

                int bytesRead = in.readNBytes(additionalDataBytes, 0, dataLength);
                if (bytesRead != dataLength) {
                    logger.error("Expected to read {} bytes for data, but read {}", dataLength, bytesRead);
                }
                String additionalDataString = new String(additionalDataBytes, ENCODING);
                Map<String, Object> additionalData = GsonUtils.fromJsonToMap(additionalDataString);
                wyomingMessage.setAdditionalData(additionalData);
            }
            Integer payloadLength = messageHeader.getPayloadLength();
            if (payloadLength != null && payloadLength > 0) {
                logger.debug("Reading payload of length {}", payloadLength);
                byte[] payloadDataBytes = new byte[payloadLength];
                int bytesRead = in.readNBytes(payloadDataBytes, 0, payloadLength);
                if (bytesRead != payloadLength) {
                    logger.error("Expected to read {} bytes for payload, but read {}", payloadLength, bytesRead);
                }
                wyomingMessage.setPayload(payloadDataBytes);
            }
            return wyomingMessage;
        } catch (WyomingProtocolException e) {
            if (e.getJson() != null) {
                logger.warn("Error decoding message {}, cause {} ", e.getJson(), e.getMessage());
            } else {
                logger.warn("Error decoding message, cause {}", e.getMessage());
            }
            return null;
        }
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

    public void sendMessage(MessageType messageType, WyomingData data) throws IOException {
        sendMessage(messageType, data, null);
    }

    public void sendMessage(MessageType messageType, WyomingData data, byte[] payload) throws IOException {
        WyomingMessageHeader wyomingMessageHeader = new WyomingMessageHeader(messageType);
        WyomingRawMessage wyomingRawMessage = new WyomingRawMessage(wyomingMessageHeader, data, payload);
        sendMessage(wyomingRawMessage);
    }

    public synchronized void sendMessage(WyomingRawMessage message) throws IOException {
        logger.debug("Sending message: {}, {}", message.getHeader().getType(), message.getMergedData());
        byte[] dataToSend = message.toBytes();
        out.write(dataToSend);
        out.flush();
    }

    /**
     * Register a listener for all type of messages
     *
     * @param listener The listener will receive relevant messages
     */
    public void registerAll(WyomingMessageListener listener) {
        register(new HashSet<>(Arrays.asList(MessageType.values())), listener);
    }

    /**
     * Register a listener for a set of message type
     *
     * @param types    A set of message types
     * @param listener This listener will receive messages
     */
    public void register(Set<MessageType> types, WyomingMessageListener listener) {
        listeners.put(listener, types);
    }

    public WyomingRawMessage askAndWaitResponse(WyomingRawMessage message, MessageType responseTypeWanted, int milliseconds) throws IOException {
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
        boolean received = false;
        try {
            received = responseLatch.await(milliseconds, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for response");
            return null;
        }
        return received ? messageReceived[0] : null;
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
    private void readAndProcessMessages() {
        if (running.isLocked()) {
            return;
        }
        try {
            running.lock();
            boolean shouldReconnect = false;
            while (shouldRun) {
                try {
                    if (shouldReconnect) {
                        connect();
                        shouldReconnect = false;
                        logger.info("Reconnected to {}", host);
                    }
                    WyomingRawMessage message = readMessage();
                    if (message != null) {
                        for (Map.Entry<WyomingMessageListener, Set<MessageType>> entry : listeners.entrySet()) {
                            WyomingMessageListener listener = entry.getKey();
                            Set<MessageType> types = entry.getValue();
                            if (types.contains(message.getHeader().getType())) {
                                listener.onMessage(message);
                            }
                        }
                    }
                } catch (IOException e) {
                    if (! shouldRun) {
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
