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
package org.openhab.binding.signal.internal.handler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.SignalBindingConstants;
import org.openhab.binding.signal.internal.SignalBridgeConfiguration;
import org.openhab.binding.signal.internal.SignalConversationDiscoveryService;
import org.openhab.binding.signal.internal.actions.SignalActions;
import org.openhab.binding.signal.internal.protocol.DeliveryReport;
import org.openhab.binding.signal.internal.protocol.DeliveryReportListener;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.MessageListener;
import org.openhab.binding.signal.internal.protocol.SignalService;
import org.openhab.binding.signal.internal.protocol.StateListener;
import org.openhab.binding.signal.internal.protocol.store.Context;
import org.openhab.binding.signal.internal.protocol.store.ContextDedicatedAccount;
import org.openhab.binding.signal.internal.protocol.store.ContextLinkedAccount;
import org.openhab.binding.signal.internal.protocol.store.PersistentStorageFile;
import org.openhab.core.OpenHAB;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.signal.zkgroup.InvalidInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

/**
 * The {@link SignalBridgeHandler} is responsible for handling
 * communication with the signal server.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalBridgeHandler extends BaseBridgeHandler
        implements StateListener, MessageListener, DeliveryReportListener {

    private final Logger logger = LoggerFactory.getLogger(SignalBridgeHandler.class);

    private final ThingTypeUID thingTypeUID;

    public static final List<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Arrays.asList(
            SignalBindingConstants.SIGNALACCOUNTBRIDGE_THING_TYPE,
            SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE);

    private Set<SignalConversationHandler> childHandlers = new HashSet<>();

    /**
     * The signal account responsible for the communication with whispersystem
     */
    @Nullable
    protected SignalService signalService;

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

    public SignalBridgeHandler(Bridge bridge, ThingTypeUID thingTypeUID) {
        super(bridge);
        this.thingTypeUID = thingTypeUID;
    }

    @Override
    public void initialize() {
        shouldRun = true;
        if (checkScheduled == null || (checkScheduled.isDone())) {
            checkScheduled = scheduler.scheduleWithFixedDelay(this::checkAndStartServiceIfNeeded, 0, 15,
                    TimeUnit.SECONDS);
        }
    }

    private synchronized void stopService() {
        if (checkScheduled != null) {
            checkScheduled.cancel(true);
        }
        SignalService signalServiceFinal = signalService;
        if (signalServiceFinal != null) {
            signalServiceFinal.stop();
        }
        signalService = null;
    }

    private synchronized void checkAndStartServiceIfNeeded() {
        try {
            if (!isStarting.getAndSet(true)) {
                if (shouldRun && !isRunning()) {
                    logger.debug("Initializing signal");
                    // ensure the underlying modem is stopped before trying to (re)starting it :
                    SignalBridgeConfiguration config = getConfigAs(SignalBridgeConfiguration.class);
                    SignalService signalServiceFinal = signalService;
                    if (signalServiceFinal != null) {
                        signalServiceFinal.stop();
                    } else {
                        signalServiceFinal = createService(config);
                        signalService = signalServiceFinal;
                    }
                    logger.debug("Now trying to start Signal for account {}", getId());
                    signalServiceFinal.start();
                    logger.info("Signal {} started", getId());
                }
            }
        } catch (IncompleteRegistrationException e) {
            String message = "Incomplete registration: " + e.getMessage();
            logger.info(message);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
            checkScheduled.cancel(false);
        } catch (NonSuccessfulResponseCodeException e) {
            String message = "Communication error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            logger.error(message, e);
            checkScheduled.cancel(false);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
        } catch (IOException | InvalidInputException e) {
            String message = e.getClass().getSimpleName() + " - " + e.getMessage();
            logger.debug(message, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        } catch (InvalidKeyException e) {
            String message = e.getMessage();
            logger.debug(message, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
        } finally {
            isStarting.set(false);
        }
    }

    protected SignalService createService(SignalBridgeConfiguration config)
            throws InvalidInputException, IOException, IncompleteRegistrationException {
        Context context;
        Path accountStoragePath = Path.of(OpenHAB.getUserDataFolder(), "signal", thing.getUID().getId());
        PersistentStorageFile persistentStorage = new PersistentStorageFile(accountStoragePath);
        if (thingTypeUID.equals(SignalBindingConstants.SIGNALLINKEDBRIDGE_THING_TYPE)) {
            context = new ContextLinkedAccount(persistentStorage, config.deviceName);
        } else if (thingTypeUID.equals(SignalBindingConstants.SIGNALACCOUNTBRIDGE_THING_TYPE)) {
            context = new ContextDedicatedAccount(persistentStorage, config.phoneNumber, config.captcha,
                    config.verificationCode, config.verificationCodeMethod);
        } else {
            throw new IllegalArgumentException("Cannot create signal service of " + thingTypeUID.toString());
        }
        return new SignalService(context, scheduler, this, this);
    }

    public boolean isRunning() {
        SignalService signalServiceFinal = signalService;
        return signalServiceFinal != null && signalServiceFinal.getConnectionState() == ConnectionState.CONNECTED
                && signalServiceFinal.isReceivingThreadRunning();
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof SignalConversationHandler) {
            childHandlers.add((SignalConversationHandler) childHandler);
        } else {
            logger.error("The SignalBridgeHandler can only handle SignalConversation as child");
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        childHandlers.remove(childHandler);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void messageReceived(SignalServiceAddress signalServiceAddress, SignalServiceDataMessage messageData) {
        // dispatch to conversation :
        for (SignalConversationHandler child : childHandlers) {
            child.checkAndReceive(signalServiceAddress, messageData);
        }

        String sender = getIdentifierFromAddress(signalServiceAddress);
        if (sender == null) {
            logger.error("Sender cannot be null ! Cannot parse incoming message");
            return;
        }
        logger.debug("Receiving new message from {}", sender);

        // channel trigger
        if (messageData.getBody().isPresent()) {
            String recipientAndMessage = sender + "|" + messageData.getBody().get();
            triggerChannel(SignalBindingConstants.CHANNEL_TRIGGER_SIGNAL_RECEIVE, recipientAndMessage);
        }

        // prepare discovery service
        senders.add(sender);
        final SignalConversationDiscoveryService finalDiscoveryService = discoveryService;
        if (finalDiscoveryService != null) {
            finalDiscoveryService.buildByAutoDiscovery(sender);
        }
    }

    @Override
    public void deliveryStatusReceived(DeliveryReport deliveryReport) {
        // dispatch to conversation :
        for (SignalConversationHandler child : childHandlers) {
            child.checkAndUpdateDeliveryStatus(deliveryReport);
        }

        String sender = Optional.ofNullable(deliveryReport.getE164())
                .orElse(Optional.ofNullable(deliveryReport.getAci()).orElse("unknown"));
        logger.debug("Receiving delivery status from {}", sender);
    }

    @Nullable
    public String getIdentifierFromAddress(SignalServiceAddress sender) {
        if (sender.getNumber().isPresent()) {
            return sender.getNumber().get();
        } else {
            return sender.getAci().toString();
        }
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
            DeliveryReport deliveryReport = signalServiceFinal.send(recipient, text);
            deliveryStatusReceived(deliveryReport);
            return deliveryReport;
        } else {
            throw new IllegalStateException("Cannot send message if service not ready");
        }
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
    public void newStateEvent(ConnectionState connectionState) {
        switch (connectionState) {
            case AUTH_FAILED:
                logger.error("Signal library reported an authentication error on the account {}", getId());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
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
                    updateStatus(ThingStatus.OFFLINE);
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
        getConfig().put(SignalBindingConstants.PROPERTY_QRCODE, qrCode.toString());
    }

    @Override
    public void handleRemoval() {
        stopService();
        Path accountStoragePath = Path.of(OpenHAB.getUserDataFolder(), thing.getUID().getId());
        new PersistentStorageFile(accountStoragePath).deleteEverything();
        updateStatus(ThingStatus.REMOVED);
    }
}
