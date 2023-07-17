/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Security;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.SignalAccountFiles;
import org.asamk.signal.manager.api.AccountCheckException;
import org.asamk.signal.manager.api.AlreadyReceivingException;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.InvalidNumberException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.NotRegisteredException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.RecipientIdentifier.Single;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.StateListener.ConnectionState;
import org.openhab.core.OpenHAB;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

/**
 * Central service to exchange message
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalService {

    private final Logger logger = LoggerFactory.getLogger(SignalService.class);

    private static final String SIGNAL_DIRECTORY = "signal";
    private static final int MAX_BACKOFF_COUNTER = 9;
    private static final String USER_AGENT = "Signal-Android/5.51.7 signal-cli";
    public static final String NOTE_TO_SELF = "SELF";
    private static final ServiceEnvironment SERVICE_ENVIRONMENT = ServiceEnvironment.LIVE;

    {
        // Security.insertProviderAt(new SecurityProvider(), 1);
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final Settings SETTINGS = new Settings(TrustNewIdentity.ALWAYS, false);

    private final String phoneNumber;
    private final String deviceName;
    @Nullable
    private final String captcha;
    @Nullable
    private final String verificationCode;
    private final ProvisionType provisionType;
    private final RegistrationType verificationCodeMethod;

    @Nullable
    private ReceivingThread messageReceiverThread;
    private final MessageListener messageListener;
    private StateListener connectionStateListener;

    @Nullable
    private Manager manager;
    @Nullable
    private static SignalAccountFiles signalAccountsFiles = null;
    private static ReentrantLock initializeLock = new ReentrantLock();

    public SignalService(MessageListener messageListener, StateListener connectionStateListener, String phoneNumber,
            @Nullable String captcha, @Nullable String verificationCode,
            @Nullable RegistrationType verificationCodeMethod, String deviceName, ProvisionType provisionType)
            throws IOException {
        this.messageListener = messageListener;
        this.connectionStateListener = connectionStateListener;
        this.phoneNumber = phoneNumber;
        this.captcha = captcha == null ? null : captcha.isBlank() ? null : captcha;
        this.verificationCode = verificationCode == null ? verificationCode
                : verificationCode.isBlank() ? null : verificationCode;
        this.verificationCodeMethod = verificationCodeMethod == null ? RegistrationType.TextMessage
                : verificationCodeMethod;
        this.deviceName = deviceName;
        this.provisionType = provisionType;

        initializeLock.lock(); // class wide lock. init this just once :
        try {
            if (signalAccountsFiles == null) {
                signalAccountsFiles = new SignalAccountFiles(new File(OpenHAB.getUserDataFolder(), SIGNAL_DIRECTORY),
                        SERVICE_ENVIRONMENT, USER_AGENT, SETTINGS);
            }
        } finally {
            initializeLock.unlock();
        }
    }

    public void start() throws IncompleteRegistrationException, IOException {
        synchronized (this) {
            Manager newManager;
            SignalAccountFiles signalAccountsFilesFinal = signalAccountsFiles;
            if (signalAccountsFilesFinal == null) {
                throw new IOException(
                        "Trying to start a manager but the storage files are not ready ! Should not happen");
            }
            try {
                newManager = signalAccountsFilesFinal.initManager(this.phoneNumber);
                // ignore stories attachments, and send read receipt
                newManager.setReceiveConfig(new ReceiveConfig(true, true, true));
            } catch (NotRegisteredException e) {
                if (provisionType == ProvisionType.MAIN) {
                    this.registerMain(signalAccountsFilesFinal);
                } else {
                    this.registerLinked(signalAccountsFilesFinal);
                }
                try { // As we just try to register, we could try again
                    newManager = signalAccountsFilesFinal.initManager(this.phoneNumber);
                } catch (NotRegisteredException again) {
                    throw new IncompleteRegistrationException(RegistrationState.REGISTER_NEEDED,
                            "Cannot register ! Reason : " + again.getMessage());
                } catch (AccountCheckException again) {
                    throw new IOException(again);
                }
            } catch (AccountCheckException e) {
                throw new IOException(e);
            }
            this.manager = newManager;
            updateProfileIfNecessary();
            ReceivingThread oldMessageReceiverThread = messageReceiverThread;
            if (oldMessageReceiverThread != null) {
                oldMessageReceiverThread.stopReceiving();
            }
            ReceivingThread newMessageReceiverThread = new ReceivingThread();
            connectionStateListener.newStateEvent(ConnectionState.CONNECTING, null);
            this.messageReceiverThread = newMessageReceiverThread;
            newMessageReceiverThread.start();
        }
    }

    private void updateProfileIfNecessary() {
        try {
            if (provisionType == ProvisionType.MAIN) {
                final Manager managerFinal = manager;
                if (managerFinal == null) {
                    throw new IllegalStateException("Manager cannot be null when updating profile");
                }
                String contactOrProfileName = managerFinal
                        .getContactOrProfileName(Single.fromString(phoneNumber, phoneNumber));
                if (!deviceName.equals(contactOrProfileName)) {
                    logger.info("Updating signal profile for {}", phoneNumber);
                    managerFinal.updateProfile(UpdateProfile.newBuilder().withGivenName(deviceName)
                            .withAvatar(encodeOpenHABAvatarAsRFC2397()).build());
                }
            }
        } catch (InvalidNumberException | IOException e) {
            logger.warn("Cannot update signal profile for {} to set name {}. Cause : {}", phoneNumber, deviceName,
                    e.getMessage());
        }
    }

    public static String encodeOpenHABAvatarAsRFC2397() throws IOException {
        try (InputStream resource = SignalService.class.getResourceAsStream("/openhabavatar.png")) {
            if (resource == null) {
                throw new IllegalStateException("Avatar is not there, shouldn't happen !");
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(resource.readAllBytes());
        }
    }

    private void registerLinked(SignalAccountFiles signalAccountFiles)
            throws IOException, IncompleteRegistrationException {
        ProvisioningManager provisioningManager = signalAccountFiles.initProvisioningManager();
        try {
            URI deviceLinkUri = provisioningManager.getDeviceLinkUri();
            connectionStateListener.qrCodeToScan(deviceLinkUri.toString());
            String numberLinked = provisioningManager.finishDeviceLink(deviceName);
            if (!this.phoneNumber.equals(numberLinked)) {
                String detailledMessage = "Wrong number linked ! You tried to link the account " + this.phoneNumber
                        + " with " + numberLinked;
                throw new IncompleteRegistrationException(RegistrationState.NO_VALID_USER, detailledMessage);
            }
        } catch (TimeoutException e) {
            throw new IncompleteRegistrationException(RegistrationState.QR_CODE_NEEDED, e.getMessage());
        } catch (UserAlreadyExistsException e) {
            throw new IncompleteRegistrationException(RegistrationState.NO_VALID_USER, e.getMessage());
        }
    }

    private void registerMain(SignalAccountFiles signalAccountFiles)
            throws IncompleteRegistrationException, IOException {
        try (RegistrationManager registrationManager = signalAccountFiles.initRegistrationManager(this.phoneNumber)) {
            if (verificationCode != null && !verificationCode.isBlank()) {
                try {
                    registrationManager.verifyAccount(verificationCode, null);
                } catch (PinLockedException | IncorrectPinException e) {
                    throw new IncompleteRegistrationException(RegistrationState.VERIFICATION_CODE_NEEDED,
                            e.getMessage());
                }
            } else {
                if (this.verificationCodeMethod == RegistrationType.PhoneCall) {
                    registrationManager.register(false, this.captcha);
                    Thread.sleep(62000);
                    registrationManager.register(true, this.captcha);
                } else {
                    registrationManager.register(false, this.captcha);
                }
                throw new IncompleteRegistrationException(RegistrationState.VERIFICATION_CODE_NEEDED);
            }
        } catch (RateLimitException e) {
            throw new IncompleteRegistrationException(RegistrationState.RATE_LIMIT);
        } catch (CaptchaRequiredException e) {
            throw new IncompleteRegistrationException(RegistrationState.CAPTCHA_NEEDED);
        } catch (NonNormalizedPhoneNumberException e) {
            throw new IncompleteRegistrationException(RegistrationState.NO_VALID_USER, e.getMessage());
        } catch (AuthorizationFailedException e) {
            throw new IncompleteRegistrationException(RegistrationState.VERIFICATION_CODE_NEEDED,
                    e.getMessage() + ". Incorrect code ? Delete it to request another one.");
        } catch (InterruptedException e) {
            throw new IncompleteRegistrationException(RegistrationState.RATE_LIMIT, "interrupted");
        }
    }

    public void stop() {
        ReceivingThread finalMessageReceiverThread = messageReceiverThread;
        if (finalMessageReceiverThread != null) {
            finalMessageReceiverThread.stopReceiving();
        }
        try {
            Manager managerFinal = manager;
            if (managerFinal != null) {
                managerFinal.close();
            }
        } catch (IOException e) {
            logger.info("Error closing signal manager for {}. Reason : {}", phoneNumber, e.getMessage());
        }
    }

    public DeliveryReport send(String address, String message, @Nullable String attachment) {
        Manager managerFinal = manager;
        if (managerFinal == null) {
            logger.warn("Cannot send message to {}, cause : no manager running/initialized", address);
            return new DeliveryReport(DeliveryStatus.FAILED, address);
        }

        RecipientIdentifier recipient;
        if (NOTE_TO_SELF.equalsIgnoreCase(address.trim())) {
            recipient = RecipientIdentifier.NoteToSelf.INSTANCE;
        } else {
            try {
                recipient = RecipientIdentifier.Single.fromString(address, address);
            } catch (InvalidNumberException e) {
                logger.warn("Cannot send message to {}, cause {}", address, e.getMessage());
                return new DeliveryReport(DeliveryStatus.FAILED, address);
            }
        }
        SendMessageResults sendResults;
        try {
            List<String> attachments = attachment == null ? List.of() : List.of(attachment);
            sendResults = managerFinal
                    .sendMessage(
                            new Message(message, attachments, Collections.emptyList(), Optional.empty(),
                                    Optional.empty(), Collections.emptyList(), Optional.empty()),
                            Collections.singleton(recipient));
        } catch (IOException | AttachmentInvalidException | NotAGroupMemberException | GroupNotFoundException
                | GroupSendingNotAllowedException | UnregisteredRecipientException | InvalidStickerException e) {
            logger.warn("Cannot send message to {}, cause {}", address, e.getMessage());
            return new DeliveryReport(DeliveryStatus.FAILED, address);
        }
        if (sendResults.results() != null && !sendResults.results().values().isEmpty()) {
            List<SendMessageResult> resultsForRecipient = sendResults.results().get(recipient);
            if (resultsForRecipient != null) {
                SendMessageResult result = resultsForRecipient.get(0);
                if (!result.isSuccess()) {
                    logger.warn("Cannot send message to {}, cause {}", address, result.isIdentityFailure() ? "identity"
                            : result.isNetworkFailure() ? "network"
                                    : result.isRateLimitFailure() ? "rate"
                                            : result.isUnregisteredFailure() ? "recipient unregistered" : "unknown");
                    return new DeliveryReport(DeliveryStatus.FAILED, address);
                }
            }
        }

        return new DeliveryReport(DeliveryStatus.SENT, address);
    }

    public boolean isReceivingThreadRunning() {
        var managerFinal = manager;
        if (managerFinal != null) {
            return managerFinal.isReceiving();
        } else {
            return false;
        }
    }

    private class ReceivingThread extends Thread implements Manager.ReceiveMessageHandler {

        private boolean shouldRun = false;

        public void stopReceiving() {
            shouldRun = false;
            interrupt();
        }

        public ReceivingThread() {
            this.setName("OH-binding-signal-" + phoneNumber);
            this.setDaemon(true);
        }

        @Override
        public void run() {
            shouldRun = true;
            int backOffCounter = 0;

            try {
                while (!Thread.currentThread().isInterrupted() && shouldRun) {
                    try {
                        logger.debug("Waiting for messages...");
                        // CONNECTED
                        SignalService.this.connectionStateListener.newStateEvent(ConnectionState.CONNECTED, null);
                        Manager managerFinal = manager;
                        if (managerFinal == null) {
                            logger.error("Manager should not be null !?! Cannot receive message !");
                        } else {
                            managerFinal.receiveMessages(Optional.empty(), Optional.empty(), this);
                        }
                    } catch (IOException e) {
                        SignalService.this.connectionStateListener.newStateEvent(ConnectionState.DISCONNECTED,
                                e.getMessage());
                        final var sleepMilliseconds = 100 * (long) Math.pow(2, backOffCounter);
                        backOffCounter = Math.min(backOffCounter + 1, MAX_BACKOFF_COUNTER);
                        logger.debug("Connection closed unexpectedly, reconnecting in {} ms", sleepMilliseconds);
                        Thread.sleep(sleepMilliseconds);
                        continue;
                    }
                }
                SignalService.this.connectionStateListener.newStateEvent(ConnectionState.DISCONNECTED, null);
            } catch (InterruptedException e) {
                logger.info("Interruption while trying to process message");
                // we should let an option to investigate :
                logger.debug("Exception details :", e);
                SignalService.this.connectionStateListener.newStateEvent(ConnectionState.DISCONNECTED, null);
            } catch (AlreadyReceivingException e) {
                logger.error("Already processing message. Should not happen", e);
                SignalService.this.connectionStateListener.newStateEvent(ConnectionState.DISCONNECTED, null);
            } catch (Exception e) {
                SignalService.this.connectionStateListener.newStateEvent(ConnectionState.DISCONNECTED, e.getMessage());
                logger.error("Fatal exception inside the signal receiving thread for {}. Cannot wait for message",
                        phoneNumber);
                throw e;
            } finally {
                logger.info("Receiving thread stopped...");
            }
        }

        @Override
        public void handleMessage(@Nullable MessageEnvelope envelope, @Nullable Throwable exception) {
            synchronized (messageListener) {
                handleMessageInternal(envelope, exception);
            }
        }

        private void handleMessageInternal(@Nullable MessageEnvelope envelope, @Nullable Throwable exception) {
            if (envelope != null) {
                RecipientAddress source = envelope.sourceAddress().orElse(null);

                if (envelope.data().isPresent()) {
                    MessageEnvelope.Data message = envelope.data().get();
                    if (message.body().isPresent()) {
                        messageListener.messageReceived(source, message.body().get());
                    } else {
                        logger.debug("empty message from {}",
                                source != null ? source.getLegacyIdentifier() : "unknown");
                    }
                    // if (message.groupContext().isPresent()) {
                    // writer.println("Group info:");
                    // final var groupContext = message.groupContext().get();
                    // printGroupInfo(writer.indentedWriter(), groupContext.getId());
                    // }
                } else if (envelope.sync().isPresent() && envelope.sync().get().sent().isPresent()
                        && envelope.sync().get().sent().get().destination().isPresent() && envelope.sync().get().sent()
                                .get().destination().get().getLegacyIdentifier().equals(phoneNumber)) {
                    // message from the account to the account, handles this like a standard message
                    MessageEnvelope.Data message = envelope.sync().get().sent().get().message().get();
                    if (message.body().isPresent()) {
                        messageListener.messageReceived(envelope.sync().get().sent().get().destination().get(),
                                message.body().get());
                    } else {
                        logger.debug("empty message from {}",
                                source != null ? source.getLegacyIdentifier() : "unknown");
                    }
                }
                if (envelope.receipt().isPresent() && source != null) {
                    MessageEnvelope.Receipt receiptMessage = envelope.receipt().get();
                    DeliveryStatus status;
                    switch (receiptMessage.type()) {
                        case DELIVERY:
                            status = DeliveryStatus.DELIVERED;
                            break;
                        case READ:
                            status = DeliveryStatus.READ;
                            break;
                        case VIEWED:
                            status = DeliveryStatus.READ;
                            break;
                        case UNKNOWN:
                        default:
                            status = DeliveryStatus.UNKNOWN;
                            break;
                    }
                    messageListener.deliveryStatusReceived(new DeliveryReport(status, source));
                }
            }
            if (exception != null) {
                if (exception instanceof ProtocolDuplicateMessageException) { // suppressing some messages
                    logger.debug("Duplicate message: {}. Ignoring it", exception.getMessage());
                } else {
                    logger.warn("Signal get an exception while receiving message: {}", exception.getMessage());
                }
            }
        }
    }

    public synchronized void deleteAccount() throws IOException {
        logger.debug("Trying to remove signal account {}", phoneNumber);

        SignalAccountFiles signalAccountsFilesFinal = signalAccountsFiles;
        if (signalAccountsFilesFinal == null) {
            throw new IOException("Cannot delete account when files store is broken");
        }
        if (signalAccountsFilesFinal.getAllLocalAccountNumbers().stream().anyMatch(n -> n.equals(this.phoneNumber))) {
            try {
                // unregister
                Manager managerLocal = this.manager;
                if (managerLocal == null) {
                    managerLocal = signalAccountsFilesFinal.initManager(this.phoneNumber);
                }
                managerLocal.unregister();
            } catch (Exception e) {
                logger.warn("Cannot unregister signal number {}. Cause : {}", this.phoneNumber, e.getMessage());
            }
            stop();
            signalAccountsFilesFinal.initRegistrationManager(this.phoneNumber).deleteLocalAccountData();
        }
    }
}
