/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.smsmodem.internal.handler;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smsmodem.internal.SMSConversationDiscoveryService;
import org.openhab.binding.smsmodem.internal.SMSModemBindingConstants;
import org.openhab.binding.smsmodem.internal.SMSModemBridgeConfiguration;
import org.openhab.binding.smsmodem.internal.actions.SMSModemActions;
import org.openhab.binding.smsmodem.internal.smslib.MessageReceiver;
import org.openhab.binding.smsmodem.internal.smslib.ModemCommunicationException;
import org.openhab.binding.smsmodem.internal.smslib.ModemConfigurationException;
import org.openhab.binding.smsmodem.internal.smslib.SMSModem;
import org.openhab.binding.smsmodem.internal.smslib.ServiceStatusListener;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.library.types.StringType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SMSModemBridgeHandler} is responsible for handling the modem.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SMSModemBridgeHandler extends BaseBridgeHandler implements MessageReceiver, ServiceStatusListener {

    public static final ThingTypeUID SUPPORTED_THING_TYPES_UIDS = SMSModemBindingConstants.SMSMODEMBRIDGE_THING_TYPE;

    private final Logger logger = LoggerFactory.getLogger(SMSModemBridgeHandler.class);

    private Set<SMSConversationHandler> childHandlers = new HashSet<>();

    // we keep a list of msisdn sender for autodiscovery
    private Set<String> senderMsisdn = new HashSet<String>();

    private @NonNullByDefault({}) SMSModem smsModem;

    private SerialPortManager serialPortManager;

    private @Nullable ScheduledFuture<?> restartScheduled;

    private boolean shouldRun = false;

    private @Nullable SMSConversationDiscoveryService discoveryService;

    @Override
    public void dispose() {
        shouldRun = false;
        ScheduledFuture<?> restartScheduledFinal = restartScheduled;
        if (restartScheduledFinal != null && !restartScheduledFinal.isDone()) {
            restartScheduledFinal.cancel(true);
        }
        scheduler.execute(smsModem::stop);
    }

    public SMSModemBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void initialize() {
        shouldRun = true;
        SMSModemBridgeConfiguration config = getConfigAs(SMSModemBridgeConfiguration.class);
        smsModem = new SMSModem(serialPortManager, config.serialPortOrIP, config.baudOrNetworkPort, config.simPin, this,
                this);
        scheduler.execute(this::startModem);
    }

    private void startModem() {
        try {
            restartScheduled = null;
            smsModem.start();
        } catch (ModemConfigurationException e) {
            String message = e.getMessage();
            logger.error(message, e.getCause());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            scheduleRestart();
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof SMSConversationHandler) {
            childHandlers.add((SMSConversationHandler) childHandler);
        } else {
            logger.error("The SMSModemBridgeHandler can only handle SMSConversation as child");
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
    public void receive(String sender, String text) {
        logger.debug("Receiving new message from {} : {}", sender, text);

        // dispatch to conversation :
        for (SMSConversationHandler child : childHandlers) {
            child.checkAndReceive(sender, text);
        }

        // channel with last message
        String recipientAndMessage = sender + "|" + text;
        updateState(SMSModemBindingConstants.CHANNEL_RECEIVED, new StringType(recipientAndMessage));
        // channel trigger
        triggerChannel(SMSModemBindingConstants.CHANNEL_TRIGGER_MODEM_RECEIVE, recipientAndMessage);

        // prepare discovery service
        senderMsisdn.add(sender);
        if (discoveryService != null) {
            discoveryService.buildDiscovery(sender);
        }
    }

    public void send(String recipient, String text) {
        scheduler.execute(() -> {
            try {
                smsModem.send(recipient, text);
            } catch (ModemCommunicationException e) {
                String message = "Cannot send SMS message to " + recipient;
                logger.error(message, e);
            }
        });
    }

    public Set<String> getAllSender() {
        return new HashSet<>(senderMsisdn);
    }

    private void scheduleRestart() {
        final ScheduledFuture<?> restartScheduledFinal = restartScheduled;
        if ((restartScheduledFinal == null || restartScheduledFinal.isDone()) && this.shouldRun) {
            restartScheduled = scheduler.schedule(this::startModem, 15, TimeUnit.SECONDS);
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(SMSModemActions.class, SMSConversationDiscoveryService.class);
    }

    @Override
    public void error() {
        logger.error("SMSLib reported an error on the underlying modem");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        scheduleRestart();
    }

    @Override
    public void started() {
        logger.debug("SMSLib reported the modem is started");
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void starting() {
        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void stopped() {
        logger.debug("SMSLib reported the modem is stopped");
        updateStatus(ThingStatus.OFFLINE);
        scheduleRestart();
    }

    @Override
    public void stopping() {
        updateStatus(ThingStatus.OFFLINE);
    }

    public void setDiscoveryService(SMSConversationDiscoveryService smsConversationDiscoveryService) {
        this.discoveryService = smsConversationDiscoveryService;
    }
}
