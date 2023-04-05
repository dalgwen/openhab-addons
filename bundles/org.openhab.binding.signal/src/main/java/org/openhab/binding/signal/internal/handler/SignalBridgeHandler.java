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
package org.openhab.binding.signal.internal.handler;

import static org.openhab.binding.signal.internal.SignalBindingConstants.PHOTO_EXTENSIONS;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.asamk.signal.manager.api.RecipientAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.signal.internal.SignalBindingConstants;
import org.openhab.binding.signal.internal.SignalBridgeConfiguration;
import org.openhab.binding.signal.internal.SignalConversationDiscoveryService;
import org.openhab.binding.signal.internal.actions.SignalActions;
import org.openhab.binding.signal.internal.protocol.AttachmentCreationException;
import org.openhab.binding.signal.internal.protocol.AttachmentUtils;
import org.openhab.binding.signal.internal.protocol.DeliveryReport;
import org.openhab.binding.signal.internal.protocol.DeliveryStatus;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.MessageListener;
import org.openhab.binding.signal.internal.protocol.ProvisionType;
import org.openhab.binding.signal.internal.protocol.SignalService;
import org.openhab.binding.signal.internal.protocol.StateListener;
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
public class SignalBridgeHandler extends BaseBridgeHandler implements StateListener, MessageListener {

    private final Logger logger = LoggerFactory.getLogger(SignalBridgeHandler.class);

    private final ThingTypeUID thingTypeUID;

    private HttpClient httpClient;

    public static final List<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = List.of(
            SignalBindingConstants.SIGNALACCOUNTBRIDGE_THING_TYPE,
            SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE);

    /**
     * The signal account responsible for the communication with whispersystem
     */
    @Nullable
    protected SignalService signalService;
    private ReentrantLock lockStartAndStop = new ReentrantLock();

    /**
     * A scheduled watchdog check
     */
    private @NonNullByDefault({}) ScheduledFuture<?> checkScheduled;

    // we keep a list of sender for autodiscovery
    private Set<String> senders = new HashSet<String>();
    private @Nullable SignalConversationDiscoveryService discoveryService;

    private boolean shouldRun = false;
    private AtomicBoolean isStarting = new AtomicBoolean(false);

    @Override
    public void dispose() {
        shouldRun = false;
        scheduler.execute(this::stopService);
    }

    public SignalBridgeHandler(Bridge bridge, ThingTypeUID thingTypeUID, HttpClient httpClient) {
        super(bridge);
        this.thingTypeUID = thingTypeUID;
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        SignalBridgeConfiguration config = getConfigAs(SignalBridgeConfiguration.class);
        ProvisionType provisionType = thingTypeUID.equals(SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE)
                ? ProvisionType.LINKED
                : ProvisionType.MAIN;
        try {
            this.signalService = new SignalService(this, this, config.phoneNumber, config.captcha,
                    config.verificationCode, config.verificationCodeMethod, config.deviceName, provisionType);
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Error during initialization : " + e.getMessage());
        }
        shouldRun = true;
        if (checkScheduled == null || (checkScheduled.isDone())) {
            checkScheduled = scheduler.scheduleWithFixedDelay(this::checkAndStartServiceIfNeeded, 0, 15,
                    TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("null")
    private Set<SignalConversationHandler> getChildHandlers() {
        return getThing().getThings().stream().map(Thing::getHandler).filter(Objects::nonNull)
                .map(handler -> (SignalConversationHandler) handler).collect(Collectors.toSet());
    }

    private void stopService() {
        lockStartAndStop.lock();
        try {
            if (checkScheduled != null) {
                checkScheduled.cancel(true);
            }
            SignalService signalServiceFinal = signalService;
            if (signalServiceFinal != null) {
                signalServiceFinal.stop();
            }
        } finally {
            lockStartAndStop.unlock();
        }
    }

    private void checkAndStartServiceIfNeeded() {
        lockStartAndStop.lock();
        try {
            if (!isStarting.getAndSet(true)) {
                if (shouldRun && !isRunning()) {
                    logger.debug("Initializing signal");
                    // ensure the underlying modem is stopped before trying to (re)starting it :
                    SignalService signalServiceFinal = signalService;
                    if (signalServiceFinal != null) {
                        signalServiceFinal.stop();
                        logger.debug("Now trying to start Signal for account {}", getId());
                        signalServiceFinal.start();
                        logger.info("Signal {} started", getId());
                    } else {
                        logger.error("Signal service should not be null here !");
                    }
                }
            }
        } catch (IncompleteRegistrationException e) {
            String message = "Incomplete registration: " + e.getMessage();
            getConfig().put(SignalBindingConstants.PROPERTY_QRCODE, null);
            checkScheduled.cancel(false);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
        } catch (IOException e) {
            String message = e.getClass().getSimpleName() + " - " + e.getMessage();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        } finally {
            isStarting.set(false);
            lockStartAndStop.unlock();
        }
    }

    public boolean isRunning() {
        SignalService signalServiceFinal = signalService;
        return signalServiceFinal != null && signalServiceFinal.isReceivingThreadRunning();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void messageReceived(@Nullable RecipientAddress recipientAddress, String messageData) {
        // dispatch to conversation :
        if (recipientAddress != null) {
            for (SignalConversationHandler child : getChildHandlers()) {
                child.checkAndReceive(recipientAddress, messageData);
            }
        }

        String sender = recipientAddress != null ? recipientAddress.getLegacyIdentifier() : null;
        logger.debug("Receiving new message from {}", sender != null ? sender : "unknown");

        // channel trigger
        String recipientAndMessage = (sender != null ? sender : "unknown") + "|" + messageData;
        triggerChannel(SignalBindingConstants.CHANNEL_TRIGGER_SIGNAL_RECEIVE, recipientAndMessage);

        // prepare discovery service
        if (sender != null) {
            senders.add(sender);
            final SignalConversationDiscoveryService finalDiscoveryService = discoveryService;
            if (finalDiscoveryService != null) {
                finalDiscoveryService.buildByAutoDiscovery(sender);
            }
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
     * Send message
     *
     * @param recipient The recipient for the message
     * @param text The message content
     */
    public DeliveryReport send(String recipient, String text) {
        logger.debug("Sending message to {}", recipient);
        SignalService signalServiceFinal = signalService;
        if (signalServiceFinal != null) {
            DeliveryReport deliveryReport = signalServiceFinal.send(recipient, text, null);
            deliveryStatusReceived(deliveryReport);
            return deliveryReport;
        } else {
            throw new IllegalStateException("Cannot send message if service not ready");
        }
    }

    /**
     * Send image
     *
     * @param recipient The recipient for the message
     * @param image The image to sent. Use a scheme at the beginning (either file:, http:, base64
     */
    public DeliveryReport sendImage(String recipient, String image) {

        logger.debug("Sending photo message to {}", recipient);
        SignalService signalServiceFinal = signalService;

        if (signalServiceFinal == null) {
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
                        + image.substring(0, image.length() < 100 ? image.length() : 100));
            }
        } catch (AttachmentCreationException e) {
            logger.debug("Cannot attach image: {}", e.getMessage());
            signalServiceFinal.send(recipient, "Cannot attach image: " + e.getMessage(), null);
            return new DeliveryReport(DeliveryStatus.FAILED, recipient);
        }

        return signalServiceFinal.send(recipient, "", attachment);
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
        return Set.of(SignalActions.class, SignalConversationDiscoveryService.class);
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
    public void newStateEvent(ConnectionState connectionState, @Nullable String detailledMessage) {
        switch (connectionState) {
            case AUTH_FAILED:
                String message = "Signal library reported an authentication error on the account " + getId()
                        + ((detailledMessage != null) ? ". " + detailledMessage : "");
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
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, detailledMessage);
                }
                break;
        }
    }

    public void setDiscoveryService(SignalConversationDiscoveryService signalConversationDiscoveryService) {
        this.discoveryService = signalConversationDiscoveryService;
    }

    @Override
    public void qrCodeToScan(String qrCode) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, "Waiting for QR code scan");
        getConfig().put(SignalBindingConstants.PROPERTY_QRCODE, qrCode);
    }

    @Override
    public void handleRemoval() {
        if (signalService != null) {
            try {
                signalService.deleteAccount();
            } catch (Exception e) {
                logger.warn("Error during signal account removal", e);
            }
        }
        stopService();
        updateStatus(ThingStatus.REMOVED);
    }
}
