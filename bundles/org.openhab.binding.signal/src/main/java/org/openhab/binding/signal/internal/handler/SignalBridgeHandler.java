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
package org.openhab.binding.signal.internal.handler;

import static org.openhab.binding.signal.internal.SignalBindingConstants.PHOTO_EXTENSIONS;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.signal.internal.SignalBindingConstants;
import org.openhab.binding.signal.internal.SignalBridgeConfiguration;
import org.openhab.binding.signal.internal.SignalConversationDiscoveryService;
import org.openhab.binding.signal.internal.actions.SignalActionsLinked;
import org.openhab.binding.signal.internal.actions.SignalActionsMain;
import org.openhab.binding.signal.internal.protocol.*;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SignalBridgeHandler} is responsible for handling
 * communication with the signal server.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalBridgeHandler extends BaseBridgeHandler implements SignalAccountEventListener {

    private final Logger logger = LoggerFactory.getLogger(SignalBridgeHandler.class);

    private final ThingTypeUID thingTypeUID;
    private final HttpClient httpClient;

    public static final List<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = List.of(
            SignalBindingConstants.SIGNALACCOUNTBRIDGE_THING_TYPE,
            SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE);

    /**
     * The signal account responsible for the communication with whisper
     */
    protected SignalAccountManager signalAccountManager;
    @Nullable
    protected SignalAccount signalAccount;
    private final ReentrantLock lockStartAndStop = new ReentrantLock();

    private @NonNullByDefault({}) SignalBridgeConfiguration config;

    // we keep a list of sender for autodiscovery
    private final Set<String> senders = new HashSet<>();
    private @Nullable SignalConversationDiscoveryService discoveryService;

    private final AtomicBoolean isStarting = new AtomicBoolean(false);


    @Override
    public void dispose() {
        scheduler.execute(this::stopAccount);
    }

    public SignalBridgeHandler(Bridge bridge, ThingTypeUID thingTypeUID, HttpClient httpClient, SignalAccountManager signalAccountManager) {
        super(bridge);
        this.thingTypeUID = thingTypeUID;
        this.httpClient = httpClient;
        this.signalAccountManager = signalAccountManager;
    }

    @Override
    public void initialize() {
        config = getConfigAs(SignalBridgeConfiguration.class);
        ProvisionType provisionType = thingTypeUID.equals(SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE)
                ? ProvisionType.LINKED
                : ProvisionType.MAIN;

        signalAccount = signalAccountManager.getSignalAccount(this, config.phoneNumber, config.deviceName, provisionType);

        scheduler.execute(this::checkAccount);
    }

    public void register(String captcha, RegistrationType registrationType) throws IOException {
        SignalAccount signalAccountLocal = signalAccount;
        if (signalAccountLocal == null) {
            throw new IOException("Cannot register if service not ready");
        }
        signalAccountLocal.register(captcha, registrationType);
    }


    public void registerLinked() throws IOException, IncompleteRegistrationException {
        SignalAccount signalAccountLocal = signalAccount;
        if (signalAccountLocal == null) {
            throw new IOException("Cannot register if service not ready");
        }
        String deviceLinkUri = signalAccountLocal.registerLinked();
        qrCodeToScan(deviceLinkUri);
        scheduler.execute(() -> {
            try {
                signalAccountLocal.finishLink(deviceLinkUri);
                updateStatus(ThingStatus.ONLINE);
            } catch (IOException | IncompleteRegistrationException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Not registered. Did you scan the QR code? (" + e.getMessage() + ")" );
                cleanQRcode();
            }
        });
    }

    public void verify(String verificationCode) throws IncompleteRegistrationException, IOException {
        SignalAccount signalAccountLocal = signalAccount;
        if (signalAccountLocal == null) {
            throw new IOException("Cannot register if service not ready");
        }
        signalAccountLocal.verify(verificationCode);
    }

    public void checkAccount() {
        lockStartAndStop.lock();
        try {
            //TODO simplify ?
            if (!isStarting.getAndSet(true)) {
                SignalAccount signalAccountFinal = signalAccount;
                if (signalAccountFinal != null) {
                    logger.debug("Now trying to start Signal for account {}", getId());
                    signalAccountFinal.check();
                    updateStatus(ThingStatus.ONLINE);
                }
            }
        } catch (IncompleteRegistrationException e) {
            logger.debug("Incomplete registration: {}", e.getMessage());
            String message = "Incomplete registration: " + e.getMessage();
            getConfig().remove(SignalBindingConstants.PROPERTY_QRCODE);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
        } catch (IOException e) {
            logger.error("Error during initialization", e);
            String message = e.getClass().getSimpleName() + " - " + e.getMessage();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        } finally {
            isStarting.set(false);
            lockStartAndStop.unlock();
        }

    }

    @SuppressWarnings("null")
    private Set<SignalConversationHandler> getChildHandlers() {
        return getThing().getThings().stream().map(Thing::getHandler).filter(Objects::nonNull)
                .map(handler -> (SignalConversationHandler) handler).collect(Collectors.toSet());
    }

    private void stopAccount() {
        lockStartAndStop.lock();
        try {
            signalAccountManager.removeSignalAccount(config.phoneNumber);
        } finally {
            lockStartAndStop.unlock();
        }
    }


    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public boolean messageReceived(@Nullable CorrespondentAddress senderAddress, String messageData) {
        // dispatch to conversation :
        if (senderAddress != null) {
            for (SignalConversationHandler child : getChildHandlers()) {
                child.checkAndReceive(senderAddress, messageData);
            }
        }

        String sender = null;
        if (senderAddress != null) {
            sender = senderAddress.number().orElse(null);
            if (sender == null) {
                sender = senderAddress.uuid().orElse(null);
            }
        }
        logger.debug("Receiving new message from {}", sender != null ? sender : "unknown");

        // channel trigger
        String recipientAndMessage = (sender != null ? sender : "unknown") + "|" + messageData;
        triggerChannel(SignalBindingConstants.CHANNEL_TRIGGER_SIGNAL_RECEIVE, recipientAndMessage);

        // we return a read receipt if the thing is a signal account bridge (dedicated).
        // or if it is a note to itself
        boolean returnReadReceipt = shouldReturnReadReceipt(senderAddress);

        // prepare discovery service
        if (sender != null) {
            senders.add(sender);
            final SignalConversationDiscoveryService finalDiscoveryService = discoveryService;
            if (finalDiscoveryService != null) {
                finalDiscoveryService.buildByAutoDiscovery(sender);
            }
        }

        return returnReadReceipt;
    }

    private boolean shouldReturnReadReceipt(@Nullable CorrespondentAddress correspondentAddress) {
        boolean isDedicated = getThing().getThingTypeUID().equals(SignalBindingConstants.SIGNALACCOUNTBRIDGE_THING_TYPE);
        boolean isNoteToSelf = correspondentAddress != null && correspondentAddress.number().isPresent()
                && correspondentAddress.number().map( address -> address.equals(config.phoneNumber)).orElse(false);
        return isDedicated || isNoteToSelf;
    }

    @Override
    public void reactionReceived(@Nullable CorrespondentAddress sender, Reaction reaction) {
        if (config.enableReaction) {
            String newMessageBody;
            if (!reaction.isRemove()) {
                newMessageBody = "#REACTION_ADDED#" + reaction.getEmoji();
            } else {
                newMessageBody = "#REACTION_REMOVED#" + reaction.getEmoji();
            }
            messageReceived(sender, newMessageBody);
        }
    }

    @Override
    public void deliveryStatusReceived(DeliveryReport deliveryReport) {
        // dispatch to conversation :
        for (SignalConversationHandler child : getChildHandlers()) {
            child.checkAndUpdateDeliveryStatus(deliveryReport);
        }
        String sender = Optional.ofNullable(deliveryReport.getE164())
                .orElse(Optional.ofNullable(deliveryReport.getAci()).orElse("unknown"));
        logger.debug("Receiving delivery status from {}", sender);
    }

    /**
     * Send a message
     *
     * @param recipient The recipient for the message
     * @param text The message content
     */
    public DeliveryReport send(String recipient, String text) {
        logger.debug("Sending message to {}", recipient);
        SignalAccount signalAccountLocal = signalAccount;
        if (signalAccountLocal != null) {
            DeliveryReport deliveryReport = signalAccountLocal.send(recipient, text, null);
            deliveryStatusReceived(deliveryReport);
            return deliveryReport;
        } else {
            throw new IllegalStateException("Cannot send message if service not ready");
        }
    }

    /**
     * Send an image
     *
     * @param recipient The recipient for the message
     * @param image The image to send. Use a scheme at the beginning (either file:, http:, base64
     */
    public DeliveryReport sendImage(String recipient, String image, @Nullable String text) {
        logger.debug("Sending photo message to {}", recipient);
        SignalAccount signalAccountFinal = signalAccount;

        if (signalAccountFinal == null) {
            throw new IllegalStateException("Cannot send message if service not ready");
        }

        String lowerCasePhotoUrl = image.toLowerCase();

        String attachment;

        try {
            if (lowerCasePhotoUrl.startsWith("http")) {
                logger.debug("Http based URL for photo provided.");
                attachment = AttachmentUtils.createAttachmentFromHttp(httpClient, image);
            } else if (image.startsWith("data:")) { // direct support of data URI scheme
                attachment = image;
            } else if (PHOTO_EXTENSIONS.stream().anyMatch(lowerCasePhotoUrl::endsWith)
                    || lowerCasePhotoUrl.startsWith("file:")) {
                String imageSafe = image;
                if (lowerCasePhotoUrl.startsWith("file:")) {
                    imageSafe = image.substring(5);
                }
                logger.debug("Read file from local file system: {}", imageSafe);
                attachment = imageSafe;
            } else {
                throw new AttachmentCreationException("Scheme not supported for attachment "
                        + image.substring(0, Math.min(image.length(), 100)));
            }
        } catch (AttachmentCreationException e) {
            logger.debug("Cannot attach image: {}", e.getMessage());
            signalAccountFinal.send(recipient, "Cannot attach image: " + e.getMessage(), null);
            return new DeliveryReport(DeliveryStatus.FAILED, recipient);
        }

        return signalAccountFinal.send(recipient, text == null ? "" : text, attachment);
    }

    /**
     * Used by the scanning discovery service to create conversation
     *
     * @return All senders of the received messages since the last start
     */
    public Set<String> getAllSender() {
        return new HashSet<>(senders);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {

        ProvisionType provisionType = thingTypeUID.equals(SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE)
                ? ProvisionType.LINKED
                : ProvisionType.MAIN;
        return switch (provisionType) {
            case MAIN -> Set.of(SignalActionsMain.class);
            case LINKED -> Set.of(SignalActionsLinked.class);
        };
    }

    public String getId() {
        String phoneNumber = getConfigAs(SignalBridgeConfiguration.class).phoneNumber;
        if (!phoneNumber.isBlank()) {
            return phoneNumber;
        } else {
            return getConfigAs(SignalBridgeConfiguration.class).deviceName;
        }
    }

    @Override
    public void newStateEvent(ConnectionState connectionState, @Nullable String detailedMessage) {
        switch (connectionState) {
            case AUTH_FAILED:
                String message = "Signal library reported an authentication error on the account " + getId()
                        + ((detailedMessage != null) ? ". " + detailedMessage : "");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
                break;
            case CONNECTED:
                logger.debug("Signal library reported the service for {} is connected", getId());
                updateStatus(ThingStatus.ONLINE);
                break;
            case CONNECTING:
                logger.debug("Signal library reported the service for {} is starting", getId());
                break;
            case DISCONNECTED:
                logger.debug("Signal library reported the service for {} is stopped", getId());
                if (thing.getStatus() != ThingStatus.OFFLINE) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, detailedMessage);
                }
                break;
        }
    }

    public void setDiscoveryService(SignalConversationDiscoveryService signalConversationDiscoveryService) {
        this.discoveryService = signalConversationDiscoveryService;
    }

    @Override
    public void qrCodeToScan(String qrCode) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Waiting for QR code scan. You have 60 seconds!");
        Configuration configuration = getConfig();
        configuration.put(SignalBindingConstants.PROPERTY_QRCODE, qrCode);
        updateConfiguration(configuration);
    }

    public void cleanQRcode() {
        Configuration configuration = getConfig();
        configuration.put(SignalBindingConstants.PROPERTY_QRCODE, "");
        updateConfiguration(configuration);
    }

    @Override
    public void handleRemoval() {
        @Nullable SignalAccount signalAccountLocal = signalAccount;
        if (signalAccountLocal != null) {
            try {
                signalAccountLocal.deleteAccount();
            } catch (Exception e) { //TODO avoir generic exception
                logger.warn("Error during signal account removal", e);
            }
        }
        // do it AFTER deletion, as we need the underlying signal-cli to run in order to delete it
        // (stopping means the service could stop beforehand)
        stopAccount();
        updateStatus(ThingStatus.REMOVED);
    }

    public void updateProfile() throws IOException {
        @Nullable SignalAccount signalAccountLocal = signalAccount;
        if (signalAccountLocal != null) {
            signalAccountLocal.updateProfile();
        }
    }
}
