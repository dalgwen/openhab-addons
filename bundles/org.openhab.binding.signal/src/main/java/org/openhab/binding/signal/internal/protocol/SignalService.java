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
package org.openhab.binding.signal.internal.protocol;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.StateListener.ConnectionState;
import org.openhab.binding.signal.internal.protocol.store.Context;
import org.openhab.binding.signal.internal.protocol.store.WhisperTrustStore;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SealedSessionCipher;
import org.signal.libsignal.metadata.SealedSessionCipher.DecryptionResult;
import org.signal.libsignal.metadata.SelfSendException;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PlaintextContent;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalServiceMessageSender.IndividualSendEvents;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.SignalWebSocket.MessageReceivedCallback;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceMetadata;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote.InvalidQuoteFormatException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto.Builder;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import com.google.protobuf.InvalidProtocolBufferException;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * Central service to exchange message
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalService implements Consumer<WebSocketConnectionState> {

    private final Logger logger = LoggerFactory.getLogger(SignalService.class);

    private final Context context;
    @NonNullByDefault({})

    private static final int MAX_BACKOFF_COUNTER = 9;
    // this counter help asking only sometimes -not always- for pre keys count to the server
    private int refreshCounter = 0;

    private static final MessageReceivedCallback EMPTY_CALLBACK = (a) -> {
    };

    private ReceivingThread messageReceiverThread = new ReceivingThread();
    @NonNullByDefault({})
    private SignalServiceMessageSender messageSender;
    private ScheduledExecutorService scheduler;
    @NonNullByDefault({})
    SignalWebSocket websocket;

    @Nullable
    private StateListener connectionStateListener;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    @Nullable
    private ConnectionState futureConnectionState;
    @Nullable
    Future<?> connectionStateChangeEventFuture;

    @Nullable
    private MessageListener messageListener;

    Map<String, ACI> aciByPhoneNumber = new HashMap<>();

    private static final KeyStore IAS_KEYSTORE;
    private static final CertificateValidator VALIDATOR;
    static {
        Security.addProvider(new BouncyCastleProvider());
        TrustStore contactTrustStore = new WhisperTrustStore("ias.store");
        try {
            VALIDATOR = new CertificateValidator(Curve.decodePoint(Context.UNIDENTIFIED_SENDER_TRUST_ROOT, 0));
            KeyStore keyStore = KeyStore.getInstance("BKS");
            keyStore.load(contactTrustStore.getKeyStoreInputStream(),
                    contactTrustStore.getKeyStorePassword().toCharArray());
            IAS_KEYSTORE = keyStore;
        } catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException
                | InvalidKeyException e) {
            throw new AssertionError("Cannot load key store or validator !", e);
        }
    }

    public SignalService(Context context, ScheduledExecutorService scheduler, MessageListener messageListener,
            StateListener connectivityListener) {
        this.context = context;
        this.scheduler = scheduler;
        this.messageListener = messageListener;
        this.connectionStateListener = connectivityListener;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * This method will prevent micro changes in connection state by establishing delayed notification to
     * the listener. There is many little disconnections with whisper system.
     *
     * @param connectionState
     */
    private void setConnectionState(ConnectionState newConnectionState) {
        logger.debug("Connection state change: {} -> {}", connectionState, newConnectionState);
        if (newConnectionState == futureConnectionState) {
            // we are already aware or on the brink of notifying this state, so return
            return;
        }
        // if an announce is waiting, it is out of date, so cancel it :
        ConnectionState couldHaveBeenAnnounced = cancelConnectionStateChangeEventFuture();
        if (newConnectionState == connectionState) {
            return;
        }

        boolean shouldDelayChange = false;
        // compute if a delay should be applied before sending the new state :
        if (connectionState == ConnectionState.CONNECTED && (newConnectionState == ConnectionState.DISCONNECTED
                || newConnectionState == ConnectionState.CONNECTING) && // the next test should prevent potential cycle
                                                                        // between connecting <-> disconnected :
                !(couldHaveBeenAnnounced == ConnectionState.CONNECTING
                        && newConnectionState == ConnectionState.DISCONNECTED)) {
            shouldDelayChange = true;
        }

        Runnable changeStateAndnotify = () -> {
            SignalService.this.connectionState = newConnectionState;
            var listener = SignalService.this.connectionStateListener;
            if (listener != null) {
                listener.newStateEvent(newConnectionState);
            }
        };
        if (shouldDelayChange) { // run later
            futureConnectionState = newConnectionState;
            connectionStateChangeEventFuture = scheduler.schedule(changeStateAndnotify, 5, TimeUnit.SECONDS);
        } else { // run now
            changeStateAndnotify.run();
        }
    }

    /**
     * Cancel the task announcing state connection change.
     *
     * @return the value that could have been announced
     */
    private @Nullable ConnectionState cancelConnectionStateChangeEventFuture() {
        Future<?> connectionEventWarningFutureFinal = connectionStateChangeEventFuture;
        ConnectionState couldHaveBeenAnnounced = null;
        if (connectionEventWarningFutureFinal != null) {
            couldHaveBeenAnnounced = futureConnectionState;
            connectionEventWarningFutureFinal.cancel(false);
            futureConnectionState = null;
            connectionStateChangeEventFuture = null;
        }
        return couldHaveBeenAnnounced;
    }

    private synchronized void refreshPreKeysIfNeeded()
            throws IOException, InvalidKeyException, IncompleteRegistrationException {
        refreshCounter++;
        if (context.needsKeysRecreation() || (refreshCounter > 10
                && context.getOrCreateAccountManager().getPreKeysCount() < Context.PREKEY_MINIMUM_SIZE)) {
            logger.info("Generating keys for {} ...", context.getId());
            int initialPreKeyId = new SecureRandom().nextInt(Medium.MAX_VALUE);
            List<PreKeyRecord> records = Utils.generatePreKeyRecords(initialPreKeyId, Context.PREKEY_BATCH_SIZE);
            records.forEach((v) -> context.getProtocolStore().storePreKey(v.getId(), v));
            int signedPreKeyId = new SecureRandom().nextInt(Medium.MAX_VALUE);
            IdentityKeyPair identityKeyPair = context.getIdentityKeyPair();
            SignedPreKeyRecord signedPreKey = Utils.generateSignedPreKeyRecord(identityKeyPair, signedPreKeyId);
            context.getProtocolStore().storeSignedPreKey(signedPreKey.getId(), signedPreKey);
            context.getOrCreateAccountManager().setPreKeys(identityKeyPair.getPublicKey(), signedPreKey, records);
            context.setNeedsKeysRecreation(false);
            refreshCounter = 0;
        }
    }

    public synchronized void start()
            throws IOException, InvalidInputException, InvalidKeyException, IncompleteRegistrationException {
        // check registration state
        RegistrationState registrationState = context.getRegistrationState();
        switch (registrationState) {
            case NO_USER:
            case CAPTCHA_NEEDED:
                throw new IncompleteRegistrationException(registrationState);
            case VERIFICATION_CODE_NEEDED:
                context.verification();
                throw new IncompleteRegistrationException(RegistrationState.VERIFICATION_CODE_NEEDED);
            case QR_CODE_NEEDED:
            case REGISTER_NEEDED:
                context.register(connectionStateListener);
                break;
            case REGISTERED:
                break;
        }

        refreshPreKeysIfNeeded();

        websocket = createWebsocket(context);
        websocket.connect();

        // setting receiving service :
        this.messageReceiverThread = new ReceivingThread();
        this.messageReceiverThread.setName("OH-binding-signal-" + context.getId());
        this.messageReceiverThread.setDaemon(true);
        this.messageReceiverThread.start();

        // setting sending service :
        SignalSessionLock test = () -> () -> { // dumb lock
        };
        ClientZkProfileOperations clientZkOperation = new ClientZkOperations(
                new ServerPublicParams(context.getConfig().getZkGroupServerPublicParams())).getProfileOperations();
        this.messageSender = new SignalServiceMessageSender(context.getConfig(), context, context.getProtocolStore(),
                test, Context.USER_AGENT, websocket, Optional.absent(), clientZkOperation, scheduler, 0L, false);
    }

    private synchronized DeliveryReport send(String address, SignalServiceDataMessage message)
            throws UntrustedIdentityException, IOException {
        int tryNumber = 0;
        boolean interrupted = false;
        boolean success = false;
        SendMessageResult sendDataMessageResult = null;
        IOException exceptionEncountered = null;
        String aciString = null;
        String e164String = null;
        while (!success && tryNumber <= 3 && !interrupted) {
            tryNumber++;
            exceptionEncountered = null;
            try {
                SignalServiceAddress signalServiceAddress = null;
                if (!Utils.isUUID(address)) {
                    e164String = address;
                    ACI aciAddress = getACIAddress(e164String);
                    if (aciAddress == null) {
                        return new DeliveryReport(DeliveryStatus.UNKNOWN_RECIPIENT, null, e164String);
                    } else {
                        aciString = aciAddress.toString();
                        signalServiceAddress = new SignalServiceAddress(aciAddress, e164String);
                    }
                } else {
                    aciString = address;
                    signalServiceAddress = new SignalServiceAddress(ACI.parseOrThrow(address), Optional.absent());
                }
                sendDataMessageResult = messageSender.sendDataMessage(signalServiceAddress, Optional.absent(),
                        ContentHint.IMPLICIT, message, IndividualSendEvents.EMPTY);
                success = sendDataMessageResult.isSuccess();
                if (sendDataMessageResult.isUnregisteredFailure()) { // wrong recipient. does he have changed number ?
                    logger.warn("Recipient unregistered !");
                    aciByPhoneNumber.remove(address); // remove. next try will find the new number
                }
            } catch (IllegalStateException e) { // workaround, maybe stored session is not in a good state ?
                if (aciString != null) {
                    // delete it before retrying :
                    logger.warn("Session with recipient inconsistent, deleting it before retrying");
                    context.getProtocolStore().deleteAllSessions(aciString);
                }
            } catch (IOException e) { // could be a network failure, retry
                try {
                    logger.debug("Network error, retrying");
                    exceptionEncountered = e;
                    Thread.sleep(1000);
                } catch (InterruptedException e1) { // stop trying
                    interrupted = true;
                }
            }
        }
        if (!success) {
            logger.warn("Cannot send message, stop trying");
            if (exceptionEncountered != null) {
                throw exceptionEncountered;
            }
            if (sendDataMessageResult != null && sendDataMessageResult.isUnregisteredFailure()) {
                return new DeliveryReport(DeliveryStatus.UNKNOWN_RECIPIENT, aciString, e164String);
            } else {
                return new DeliveryReport(DeliveryStatus.FAILED, aciString, e164String);
            }
        } else {
            return new DeliveryReport(DeliveryStatus.SENT, aciString, e164String);
        }
    }

    private @Nullable ACI getACIAddress(String address) throws IOException {
        ACI aci = aciByPhoneNumber.get(address);
        if (aci != null) {
            return aci;
        }
        try {
            Map<String, ACI> aciMap = context.getOrCreateAccountManager().getRegisteredUsers(IAS_KEYSTORE,
                    Set.of(address), Context.MR_ENCLAVE);
            aciByPhoneNumber.putAll(aciMap);
            aci = aciByPhoneNumber.get(address);
            if (aci != null) {
                return aci;
            } else {
                return null;
            }
        } catch (SignatureException | IOException | InvalidQuoteFormatException | UnauthenticatedQuoteException
                | UnauthenticatedResponseException | IncompleteRegistrationException | InvalidKeyException
                | IllegalArgumentException e) {
            throw new IOException("Cannot get uuid from phone number with the whisper server", e);
        }
    }

    public DeliveryReport send(String address, String message) {
        SignalServiceDataMessage messageData = SignalServiceDataMessage.newBuilder().withBody(message).build();
        logger.debug("Trying to send message: {} to {}", message, address);
        try {
            return send(address, messageData);
        } catch (UntrustedIdentityException | IOException e) {
            return new DeliveryReport(DeliveryStatus.FAILED, address, address);
        }
    }

    public void stop() {
        messageReceiverThread.stopReceiving();
        this.messageListener = null;
        this.connectionStateListener = null;
    }

    public boolean isReceivingThreadRunning() {
        return messageReceiverThread.isRunning;
    }

    private class ReceivingThread extends Thread {

        private boolean shouldRun = false;
        private boolean isRunning = false;

        public void stopReceiving() {
            shouldRun = false;
            messageReceiverThread.interrupt();
        }

        @Override
        public void run() {
            shouldRun = true;
            isRunning = true;
            int backOffCounter = 0;

            try {
                while (!Thread.currentThread().isInterrupted() && shouldRun) {
                    try {
                        logger.debug("Waiting for messages...");

                        var result = websocket.readOrEmpty(60000, EMPTY_CALLBACK);

                        backOffCounter = 0;

                        SignalServiceEnvelope envelope;
                        if (!result.isPresent()) {
                            logger.debug("Received indicator that server queue is empty");
                            continue;
                        }
                        envelope = result.get();
                        if (envelope.isPreKeySignalMessage()) {
                            refreshPreKeysIfNeeded();
                            logger.debug("Pre keys message, count: {}",
                                    context.getOrCreateAccountManager().getPreKeysCount());
                        }
                        SignalServiceContent message = decrypt(envelope);
                        if (message == null) {
                            continue;
                        }
                        SignalServiceAddress sender = message.getSender();
                        SignalServiceDataMessage messageData = message.getDataMessage().orNull();
                        if (messageData == null) {
                            continue;
                        }
                        MessageListener messageListenerFinal = messageListener;
                        if (messageListenerFinal != null) {
                            messageListenerFinal.messageReceived(sender, messageData);
                        }
                    } catch (IOException | TimeoutException e) {
                        logger.debug("websocket unexpectedly unavailable: {}", e.getMessage());
                        if (e instanceof WebSocketUnavailableException && "Connection closed!".equals(e.getMessage())
                                || e instanceof TimeoutException) {
                            final var sleepMilliseconds = 100 * (long) Math.pow(2, backOffCounter);
                            backOffCounter = Math.min(backOffCounter + 1, MAX_BACKOFF_COUNTER);
                            logger.debug("Connection closed unexpectedly, reconnecting in {} ms", sleepMilliseconds);
                            Thread.sleep(sleepMilliseconds);
                            websocket.connect();
                        }
                        continue;
                    } catch (AssertionError e) { // (assertion error could be an interrupted
                                                 // exception wrapped by signal library
                        Throwable cause = e.getCause();
                        if (cause != null && cause instanceof InterruptedException) {
                            throw ((InterruptedException) cause);
                        }
                        logger.error("Cannot wait for message", e);
                        throw e;
                    } catch (UnsupportedDataMessageException | NoSessionException | SelfSendException
                            | InvalidMetadataMessageException | InvalidMessageStructureException
                            | DuplicateMessageException | LegacyMessageException | InvalidMessageException
                            | InvalidKeyIdException | InvalidKeyException
                            | org.whispersystems.libsignal.UntrustedIdentityException | InvalidVersionException
                            | InvalidMetadataVersionException | ProtocolUntrustedIdentityException
                            | ProtocolInvalidKeyIdException | ProtocolDuplicateMessageException
                            | ProtocolInvalidVersionException | ProtocolLegacyMessageException
                            | ProtocolNoSessionException | ProtocolInvalidKeyException
                            | ProtocolInvalidMessageException decryptError) {
                        logger.error("Message decryption error", decryptError);
                        final var sleepMilliseconds = 100 * (long) Math.pow(2, backOffCounter);
                        backOffCounter = Math.min(backOffCounter + 1, MAX_BACKOFF_COUNTER);
                        Thread.sleep(sleepMilliseconds);
                    }
                }
            } catch (IncompleteRegistrationException | InterruptedException e) {
                logger.warn("Unrecoverable error or interruption while trying to process message");
                // we should let an option to investigate :
                logger.debug("Exception details :", e);
                stopReceiving();
            } finally {
                isRunning = false;
                logger.info("Receiving thread stopped...");
            }
        }
    }

    /**
     * Decrypt a received {@link SignalServiceEnvelope}
     *
     * @param envelope The received SignalServiceEnvelope
     *
     * @return a decrypted SignalServiceContent
     * @throws InvalidMessageStructureException
     * @throws InvalidVersionException
     * @throws org.whispersystems.libsignal.UntrustedIdentityException
     * @throws InvalidKeyException
     * @throws InvalidKeyIdException
     * @throws InvalidMessageException
     * @throws LegacyMessageException
     * @throws DuplicateMessageException
     * @throws SelfSendException
     * @throws InvalidMetadataVersionException
     * @throws ProtocolUntrustedIdentityException
     * @throws ProtocolInvalidKeyIdException
     * @throws ProtocolDuplicateMessageException
     * @throws ProtocolInvalidVersionException
     * @throws ProtocolLegacyMessageException
     * @throws ProtocolNoSessionException
     * @throws ProtocolInvalidKeyException
     * @throws ProtocolInvalidMessageException
     * @throws NoSessionException
     * @throws InvalidProtocolBufferException
     * @throws UnsupportedDataMessageException
     */
    @Nullable
    private SignalServiceContent decrypt(SignalServiceEnvelope envelope)
            throws InvalidMetadataMessageException, InvalidMessageStructureException, DuplicateMessageException,
            LegacyMessageException, InvalidMessageException, InvalidKeyIdException, InvalidKeyException,
            org.whispersystems.libsignal.UntrustedIdentityException, InvalidVersionException,
            ProtocolInvalidMessageException, ProtocolInvalidKeyException, ProtocolNoSessionException,
            ProtocolLegacyMessageException, ProtocolInvalidVersionException, ProtocolDuplicateMessageException,
            ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException, InvalidMetadataVersionException,
            SelfSendException, NoSessionException, InvalidProtocolBufferException, UnsupportedDataMessageException {
        if (!envelope.hasLegacyMessage() && !envelope.hasContent()) {
            return null;
        }
        boolean isLegacy = envelope.hasLegacyMessage();

        if (!envelope.hasSourceUuid() && !envelope.isUnidentifiedSender()) {
            throw new InvalidMessageStructureException("Non-UD envelope is missing a UUID!");
        }

        byte[] ciphertext = isLegacy ? envelope.getLegacyMessage() : envelope.getContent();
        byte[] paddedMessage;
        SignalServiceMetadata metadata;
        if (envelope.isPreKeySignalMessage()) {
            SignalProtocolAddress sourceAddress = new SignalProtocolAddress(envelope.getSourceUuid().get(),
                    envelope.getSourceDevice());
            try {
                refreshPreKeysIfNeeded();
            } catch (IOException | InvalidKeyException | IncompleteRegistrationException e) {
                logger.warn("Cannot refresh prekey but receiving message to do so... Will retry later");
            }
            context.getProtocolStore().clearSenderKeySharedWith(Collections.singleton(sourceAddress));
            return null;
        } else if (envelope.isSignalMessage()) {
            SignalProtocolAddress sourceAddress = new SignalProtocolAddress(envelope.getSourceUuid().get(),
                    envelope.getSourceDevice());
            SessionCipher sessionCipher = new SessionCipher(context.getProtocolStore(), sourceAddress);
            paddedMessage = sessionCipher.decrypt(new SignalMessage(ciphertext));
            metadata = new SignalServiceMetadata(envelope.getSourceAddress(), envelope.getSourceDevice(),
                    envelope.getTimestamp(), envelope.getServerReceivedTimestamp(),
                    envelope.getServerDeliveredTimestamp(), false, envelope.getServerGuid(), Optional.absent());
        } else if (envelope.isPlaintextContent()) {
            paddedMessage = new PlaintextContent(ciphertext).getBody();
            metadata = new SignalServiceMetadata(envelope.getSourceAddress(), envelope.getSourceDevice(),
                    envelope.getTimestamp(), envelope.getServerReceivedTimestamp(),
                    envelope.getServerDeliveredTimestamp(), false, envelope.getServerGuid(), Optional.absent());
        } else if (envelope.isUnidentifiedSender()) {
            ACI aci = context.getAci();
            SealedSessionCipher sealedSessionCipher = new SealedSessionCipher(context.getProtocolStore(),
                    aci != null ? aci.uuid() : null, context.getE164(), SignalServiceAddress.DEFAULT_DEVICE_ID);
            DecryptionResult result;
            try {
                result = sealedSessionCipher.decrypt(VALIDATOR, ciphertext, envelope.getServerReceivedTimestamp());
            } catch (ProtocolNoSessionException e) {
                logger.debug("No session with unidentified sender", e);
                return null;
            }
            SignalServiceAddress resultAddress = new SignalServiceAddress(ACI.parseOrThrow(result.getSenderUuid()),
                    result.getSenderE164());
            Optional<byte[]> groupId = result.getGroupId();
            boolean needsReceipt = true;

            if (envelope.hasSourceUuid()) {
                needsReceipt = false;
            }

            if (result.getCiphertextMessageType() == CiphertextMessage.PREKEY_TYPE) {
                context.getProtocolStore().clearSenderKeySharedWith(
                        Collections.singleton(new SignalProtocolAddress(result.getSenderUuid(), result.getDeviceId())));
            }

            paddedMessage = result.getPaddedMessage();
            metadata = new SignalServiceMetadata(resultAddress, result.getDeviceId(), envelope.getTimestamp(),
                    envelope.getServerReceivedTimestamp(), envelope.getServerDeliveredTimestamp(), needsReceipt,
                    envelope.getServerGuid(), groupId);
        } else {
            throw new InvalidMetadataMessageException("Unknown type: " + envelope.getType());
        }

        byte[] data = Utils.getStrippedPaddingMessageBody(paddedMessage);

        Builder builder = SignalServiceContentProto.newBuilder()
                .setLocalAddress(SignalServiceAddressProtobufSerializer.toProtobuf(context.getSignalServiceAddress()))
                .setMetadata(SignalServiceMetadataProtobufSerializer.toProtobuf(metadata));

        SignalServiceContentProto contentProto;
        if (isLegacy) {
            SignalServiceProtos.DataMessage dataMessage = SignalServiceProtos.DataMessage.parseFrom(data);
            contentProto = builder.setLegacyDataMessage(dataMessage).build();
        } else {
            SignalServiceProtos.Content content = SignalServiceProtos.Content.parseFrom(data);
            contentProto = builder.setContent(content).build();
        }

        return SignalServiceContent.createFromProto(contentProto);
    }

    public synchronized SignalWebSocket createWebsocket(@Nullable CredentialsProvider credentialsProvider) {
        final var webSocketFactory = new WebSocketFactory() {
            @Override
            public WebSocketConnection createWebSocket() {
                return new WebSocketConnection("normal", context.getConfig(), Optional.of(credentialsProvider),
                        Context.USER_AGENT, null);
            }

            @Override
            public WebSocketConnection createUnidentifiedWebSocket() {
                return new WebSocketConnection("unidentified", context.getConfig(), Optional.absent(),
                        Context.USER_AGENT, null);
            }
        };
        SignalWebSocket signalWebSocket = new SignalWebSocket(webSocketFactory);
        signalWebSocket.getWebSocketState().forEach(this);
        return signalWebSocket;
    }

    @Override
    public void accept(WebSocketConnectionState webSocketConnectionState) {
        ConnectionState newConnectionState;
        switch (webSocketConnectionState) {
            case AUTHENTICATION_FAILED:
                newConnectionState = ConnectionState.AUTH_FAILED;
                break;
            case CONNECTED:
                newConnectionState = ConnectionState.CONNECTED;
                break;
            case CONNECTING:
                newConnectionState = ConnectionState.CONNECTING;
                break;
            case DISCONNECTED:
                newConnectionState = ConnectionState.DISCONNECTED;
                break;
            case DISCONNECTING:
                newConnectionState = ConnectionState.DISCONNECTED;
                break;
            case FAILED:
                newConnectionState = ConnectionState.DISCONNECTED;
                break;
            case RECONNECTING:
                newConnectionState = ConnectionState.CONNECTING;
                break;
            default:
                newConnectionState = ConnectionState.DISCONNECTED;
                break;
        }
        setConnectionState(newConnectionState);
    }
}
