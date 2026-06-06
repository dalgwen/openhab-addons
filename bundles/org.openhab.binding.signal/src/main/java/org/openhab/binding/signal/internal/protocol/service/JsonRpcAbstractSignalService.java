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

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.SignalService;
import org.openhab.binding.signal.internal.downloader.VersionDownloaderException;
import org.openhab.binding.signal.internal.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public abstract class JsonRpcAbstractSignalService extends SignalService {

    private final Logger logger = LoggerFactory.getLogger(JsonRpcAbstractSignalService.class);

    private static final int MAX_BACKOFF_COUNTER = 9;
    public static final String NOTE_TO_SELF = "SELF";
    public static final String RECEIVE_METHOD = "receive";
    public static final String ENVELOPE = "envelope";

    private final Gson gson;
    private final SignalAccountManager signalAccountManager;
    private final Lock lock = new ReentrantLock();


    private SignalAccountEventListener.ConnectionState currentConnectionState = SignalAccountEventListener.ConnectionState.DISCONNECTED;

    @Nullable
    private ReceivingThread messageReceiverThread;
    @SuppressWarnings("rawtypes")
    private final Map<String, CompletableFuture<JsonResponse>> waitingReceivers = new ConcurrentHashMap<>();

    public JsonRpcAbstractSignalService(SignalAccountManager signalAccountManager) {
        this.gson = new Gson();
        this.signalAccountManager = signalAccountManager;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean exists(String phoneNumber) throws JsonResponseException, IOException {
        @SuppressWarnings("rawtypes")
        JsonResponse<List> response = sendRequest(phoneNumber, "listAccounts", null, List.class);
        boolean found = false;
        List<Object> results = response.getResult();
        if (results != null) {
            for (Object result : results) {
                String existingNumber = ((Map<String, String>) result).get("number");
                if (existingNumber != null && existingNumber.equals(phoneNumber)) {
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public DeliveryReport sendMessage(String account, String recipient, String message, @Nullable String attachment) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", message);
        if (attachment != null) {
            parameters.put("attachment", attachment);
        }
        if (NOTE_TO_SELF.equalsIgnoreCase(recipient)) {
            parameters.put("recipient", account);
            parameters.put("noteToSelf", true);
            parameters.put("notifySelf", false);
        } else if (account.equals(recipient)) {
            parameters.put("recipient", account);
            parameters.put("noteToSelf", true);
            parameters.put("notifySelf", true);
        } else {
            parameters.put("recipient", recipient);
        }
        try {
            JsonResponse<Object> response = sendRequest(account, "send", parameters, Object.class);
            Object result = response.getResult();
            logger.debug("Response after send: {}", result);
            if (result instanceof Map<?, ?>) {
                Optional<String> status = getValueAt(String.class, (Map) result, "results", "type");
                if ("SUCCESS".equalsIgnoreCase(status.orElse("UNKNOWN"))) {
                    return new DeliveryReport(DeliveryStatus.SENT, recipient);
                } else {
                    return new DeliveryReport(DeliveryStatus.FAILED, recipient);
                }
            } else {
                return new DeliveryReport(DeliveryStatus.FAILED, recipient);
            }
        } catch (JsonResponseException jsonResponseException) {
            logger.warn("Cannot send message to {}, Signal error message is: {}", recipient, jsonResponseException.getMessage());
            return new DeliveryReport(DeliveryStatus.FAILED, recipient);
        } catch (IOException e) {
            logger.warn("Cannot send message to {}", recipient, e);
            return new DeliveryReport(DeliveryStatus.FAILED, recipient);
        }
    }

    private <T> Optional<T> getValueAt(Class<T> clazz, Map<String, ?> parameters, String... keyPaths) {
        Object value = parameters;
        for (String keyPath : keyPaths) {
            if (value instanceof List<?> list) {
                value = list.getFirst();
            }
            if (value instanceof Map<?, ?> valueMap) {
                value = valueMap.get(keyPath);
            }
            if (value == null) {
                return Optional.empty();
            }
        }
        if (clazz.isAssignableFrom(value.getClass())) {
            return Optional.of(clazz.cast(value));
        }
        else {
            logger.warn("Cannot cast {} of type {} to {}", value, value.getClass().getCanonicalName(), clazz);
            return Optional.empty();
        }
    }

    @Override
    public void stop() {
        connectionStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, "Signal service stopped" );
        lock.lock();
        try {
            Optional.ofNullable(messageReceiverThread).ifPresent(ReceivingThread::stopReceiving);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void start() {
        lock.lock();
        try {
            ReceivingThread newMessageReceiverThread = new ReceivingThread();
            connectionStateEvent(SignalAccountEventListener.ConnectionState.CONNECTING, null);
            this.messageReceiverThread = newMessageReceiverThread;
            newMessageReceiverThread.start();
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("rawtypes")
    private CompletableFuture<JsonResponse> registerReceiver(String requestId) {
        CompletableFuture<JsonResponse> future = new CompletableFuture<>();
        waitingReceivers.put(requestId, future);
        return future;
    }

    private class ReceivingThread extends Thread {

        private volatile boolean shouldRun = false;
        private final CountDownLatch waitingForStart = new CountDownLatch(1);

        public ReceivingThread() {
            this.setName("OH-binding-signal-socket-reading");
            this.setDaemon(true);
        }

        public void stopReceiving() {
            shouldRun = false;
            internalStop();
            interrupt();
        }

        @Override
        public void start() {
            super.start();
            try {
                boolean await = waitingForStart.await(waitTime(), TimeUnit.SECONDS);
                if (!await) {
                    logger.warn("Too much wait for receiving thread to start and connect");
                }
            } catch (InterruptedException ignored) {
                logger.warn("Cannot wait for receiving thread to start and connect (interrupted). Inconsistent state could occur");
            }
        }

        @Override
        public void run() {
            shouldRun = true;
            int backOffCounter = 0;
            try {
                while (!Thread.currentThread().isInterrupted() && shouldRun) {
                    try {
                        connectionStateEvent(SignalAccountEventListener.ConnectionState.CONNECTING, null);
                        logger.debug("starting signal cli service...");
                        internalStart();
                        waitingForStart.countDown();
                        logger.debug("Waiting for messages...");
                        // Assume connected
                        String line;
                        while ((line = readLine()) != null) {
                            try {
                                if (line.isBlank()) {
                                    logger.warn("Received null or empty JSON response : {}", line);
                                } else if (!handleNormalMessage(line) && !handleResponse(line) && !handleMessage(line)) {
                                    if (line.startsWith("WARN")) {
                                        logger.warn("Received warning message : {}", line);
                                    } else if (line.startsWith("ERROR")) {
                                        logger.error("Received error message : {}", line);
                                    } else {
                                        logger.debug("Received unhandled message : {}", line);
                                    }
                                }
                            } catch (JsonSyntaxException e) {
                                logger.warn("Received invalid JSON message : {}", line);
                            }
                        }
                        throw new IOException("Connection end unexpectedly with null line.");
                    } catch (IOException e) {
                        if (!shouldRun) {
                            logger.debug("Connection closed but quitting, ignoring exception :");
                            return;
                        }
                        logger.error("Exception when connecting with signal-cli", e);
                        connectionStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, e.toString());
                        final long sleepMilliseconds = 1000 * (long) Math.pow(2, backOffCounter);
                        backOffCounter = Math.min(backOffCounter + 1, MAX_BACKOFF_COUNTER);
                        if (shouldRun) {
                            logger.debug("Connection closed unexpectedly, reconnecting in {} ms", sleepMilliseconds);
                            //noinspection BusyWait
                            Thread.sleep(sleepMilliseconds);
                        }
                    } finally {
                        readErrorLines().ifPresent(errorLine -> logger.warn("Error output from signal-cli : {}", errorLine));
                        internalStop();
                    }
                }
                connectionStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, null);
            } catch (InterruptedException e) {
                if (shouldRun) {
                    logger.info("Interruption while trying to read/wait message");
                    logger.debug("Exception details :", e);
                }
                connectionStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, null);
            } catch (Exception e) {
                if (shouldRun) {
                    connectionStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, e.getMessage());
                    logger.error("Fatal exception inside the signal receiving thread for. Will not try to start again," +
                            "unless manually restarted.", e);
                }
                shouldRun = false;
            } finally {
                internalStop();
                logger.info("Receiving thread stopped...");
            }
        }
    }

    private boolean handleNormalMessage(String line) {
        return line.startsWith("Picked up JAVA_TOOL_OPTIONS");
    }

    @SuppressWarnings("rawtypes")
    private boolean handleMessage(String line) throws JsonSyntaxException {
        JsonRequest jsonRequest = gson.fromJson(line, JsonRequest.class);
        if (jsonRequest == null) { // cannot be null here but checkstyle complains
            return false;
        }

        Map<String, ?> params = jsonRequest.getParams();
        if (RECEIVE_METHOD.equals(jsonRequest.getMethod()) && params != null) {

            String accountNumber = getValueAt(String.class, params, "account").orElse(null);
            if (accountNumber == null) {
                logger.warn("Received message without account number");
                return false;
            }
            SignalAccount signalAccount = signalAccountManager.getSignalAccount(accountNumber);
            if (signalAccount == null) {
                logger.warn("Received message for unknown or disabled account {}", accountNumber);
                return false;
            }

            String source = getValueAt(String.class, params, ENVELOPE, "source").orElse(null);
            if (source == null) {
                source = getValueAt(String.class, params, ENVELOPE, "sourceNumber").orElse(null);
            }
            String sourceUuid = getValueAt(String.class, params, ENVELOPE, "sourceUuid").orElse(null);
            CorrespondentAddress correspondentAddress;
            try {
                correspondentAddress = new CorrespondentAddress(source, sourceUuid);
            } catch (IllegalArgumentException e) {
                logger.warn("Cannot parse recipient address from source {} and uuid {}", source, sourceUuid);
                return false;
            }

            String message = getValueAt(String.class, params, ENVELOPE, "dataMessage", "message").orElse(null);
            if (message != null && ! message.isBlank()) {
                boolean shouldReturnReadReceipt = signalAccount.messageReceived(correspondentAddress, message);
                if (shouldReturnReadReceipt) {
                    Map<String, Object> receiptParameters = new HashMap<>();
                    receiptParameters.put("type", "read");
                    String recipient = correspondentAddress.uuid().orElse("");
                    receiptParameters.put("recipient", recipient != null ? recipient : ""); // cannot be null but checkstyle complains
                    Double targetTimestampD = getValueAt(Double.class, params, ENVELOPE, "timestamp").orElse(0D);
                    long targetTimestamp = targetTimestampD != null ? targetTimestampD.longValue() : 0L; // not null but checkstyle complains
                    receiptParameters.put("targetTimestamp", targetTimestamp);
                    try {
                        sendRequest(accountNumber,"sendReceipt", receiptParameters, Object.class, -1 );
                    } catch (JsonResponseException | IOException e) {
                        logger.warn("Cannot send read receipt for account {}", accountNumber, e);
                    }
                }
                return true;
            }

            Map reactionEmoji = getValueAt(Map.class, params, ENVELOPE, "dataMessage", "reaction").orElse(null);
            if (reactionEmoji != null) {
                @SuppressWarnings("unchecked")
                boolean isRemove = (boolean) reactionEmoji.getOrDefault("isRemove", false);
                String emoji = (String) reactionEmoji.get("emoji");
                if (emoji != null) {
                    Reaction reaction = new Reaction(isRemove, emoji);
                    signalAccount.reactionReceived(correspondentAddress, reaction);
                } else {
                    logger.warn("Cannot parse reaction emoji from {}", params);
                }
                return true;
            }

            // message from self :
            Optional<String> messageFromSelf = getValueAt(String.class, params, ENVELOPE, "syncMessage", "sentMessage", "message");
            if (messageFromSelf.isPresent()) {
                signalAccount.messageReceived(correspondentAddress, messageFromSelf.get());
                return true;
            }

            Optional<Map> receiptOptional = getValueAt(Map.class, params, ENVELOPE, "receiptMessage");
            if (receiptOptional.isPresent()) {
                Map receipt = receiptOptional.get();
                if ( Boolean.TRUE == receipt.get("isDelivery")) {
                    signalAccount.deliveryStatusReceived(new DeliveryReport(DeliveryStatus.DELIVERED, correspondentAddress));
                    return true;
                } else if ( Boolean.TRUE == receipt.get("isRead")) {
                    signalAccount.deliveryStatusReceived(new DeliveryReport(DeliveryStatus.READ, correspondentAddress));
                    return true;
                } else if ( Boolean.TRUE == receipt.get("isViewed")) { // for vocal message and maybe image
                    signalAccount.deliveryStatusReceived(new DeliveryReport(DeliveryStatus.READ, correspondentAddress));
                    return true;
                }
            }

        }
        return false;
    }

    private boolean handleResponse(String line) throws JsonSyntaxException {
        try {
            @SuppressWarnings("rawtypes")
            JsonResponse jsonResponse = gson.fromJson(line, JsonResponse.class);
            if (jsonResponse == null) { // cannot be null here but checkstyle complains
                return false;
            }
            String responseId = jsonResponse.getId();
            if (responseId == null) { // not a response
                return false;
            }
            @SuppressWarnings("rawtypes")
            CompletableFuture<JsonResponse> waitingReceiver = waitingReceivers.remove(responseId);
            if (waitingReceiver != null) {
                if (jsonResponse.getError() != null) {
                    String message = jsonResponse.getError().getMessage() + " (" + jsonResponse.getError().getCode() + ")";
                    waitingReceiver.completeExceptionally(
                            new JsonResponseException(message, jsonResponse.getError()));
                } else {
                    waitingReceiver.complete(jsonResponse);
                }
            } else {
                logger.debug("Received message for unwaited request ID: {}", responseId);
                if (jsonResponse.getError() != null) {
                    logger.warn("Unknown response is in error: {}", jsonResponse.getError().getMessage());
                } else {
                    logger.debug("unknown response full line: {}", line);
                }
            }
        } catch (JsonIOException e) {
            logger.warn("Received invalid JSON response : {}", line, e);
        }
        return true;
    }

    private void connectionStateEvent(SignalAccountEventListener.ConnectionState connectionState, @Nullable String detailedMessage) {
        if (currentConnectionState != connectionState) {
            logger.debug("Connection state changed to {}. {}", connectionState, detailedMessage != null ? detailedMessage : "");
            currentConnectionState = connectionState;
            for (SignalAccount signalAccount : signalAccountManager.getAllAccounts()) {
                signalAccount.newStateEvent(connectionState, detailedMessage);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> JsonResponse<T> sendRequest(@Nullable String account, String method, @Nullable Map<String, Object> parameters, Class<T> responseType, int waitTime) throws JsonResponseException, IOException {
        String requestId = null;
        try {
            @SuppressWarnings("rawtypes")
            CompletableFuture<JsonResponse> jsonResponseCompletableFuture = null;
            // this try block writes the request
            lock.lock();
            try {
                JsonRequest request = new JsonRequest(account, method, parameters);
                requestId = request.getId();
                String jsonRequest = gson.toJson(request);
                if (waitTime > 0) {
                    jsonResponseCompletableFuture = registerReceiver(requestId);
                }
                logger.debug("Sending request : {}", jsonRequest);
                writeLine(jsonRequest);
            } finally {
                lock.unlock();
            }
            // this try block waits for the response
            if (jsonResponseCompletableFuture != null) {
                try {
                    return (JsonResponse<T>) jsonResponseCompletableFuture.get(waitTime, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new IOException("Interruption while waiting for response");
                } catch (ExecutionException e) {
                    switch (e.getCause()) {
                        case JsonResponseException jsonResponseException -> throw jsonResponseException;
                        case null, default -> throw new IOException(e);
                    }
                } catch (TimeoutException e) {
                    throw new IOException("Timeout while waiting for response");
                } catch (ClassCastException e) {
                    throw new IOException("Cannot cast response to expected type " + responseType.getSimpleName());
                }
            } else {
                return JsonResponse.none(responseType);
            }
        } finally {
            if (requestId != null) {
                waitingReceivers.remove(requestId);
            }
        }
    }

    public abstract void internalStart() throws IOException, VersionDownloaderException, UnrecoverableException;
    public abstract void internalStop();

    public abstract @Nullable String readLine() throws IOException;
    public abstract void writeLine(String jsonRequest) throws IOException;
    public abstract Optional<String> readErrorLines() throws IOException;
    public int waitTime() {
        return 10;
    }
}