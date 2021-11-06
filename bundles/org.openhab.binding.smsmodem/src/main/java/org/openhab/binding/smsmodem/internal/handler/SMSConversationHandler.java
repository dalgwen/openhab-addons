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
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smsmodem.internal.SMSConversationConfiguration;
import org.openhab.binding.smsmodem.internal.SMSModemBindingConstants;
import org.openhab.binding.smsmodem.internal.actions.SMSConversationActions;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SMSConversationHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SMSConversationHandler extends BaseThingHandler {

    public static final ThingTypeUID SUPPORTED_THING_TYPES_UIDS = SMSModemBindingConstants.SMSCONVERSATION_THING_TYPE;

    private final Logger logger = LoggerFactory.getLogger(SMSConversationHandler.class);

    private @Nullable SMSModemBridgeHandler bridgeHandler;

    private SMSConversationConfiguration config;

    public SMSConversationHandler(Thing thing) {
        super(thing);
        this.config = new SMSConversationConfiguration();
    }

    private synchronized @Nullable SMSModemBridgeHandler getBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                logger.error("Required bridge not defined for SMSconversation {} with {}.", thing.getUID(),
                        config.recipient);
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof SMSModemBridgeHandler) {
                this.bridgeHandler = (SMSModemBridgeHandler) handler;
            } else {
                logger.error("No available bridge handler found for SMSConversation {} bridge {} .", thing.getUID(),
                        bridge.getUID());
                return null;
            }
        }
        return this.bridgeHandler;
    }

    protected void checkAndReceive(String sender, String text) {
        String filter = config.recipient.trim();
        if (filter.equals(sender)) {
            updateState(SMSModemBindingConstants.CHANNEL_RECEIVED, new StringType(text));
            triggerChannel(SMSModemBindingConstants.CHANNEL_TRIGGER_CONVERSATION_RECEIVE, text);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }
        if (channelUID.getId().equals(SMSModemBindingConstants.CHANNEL_SEND)) {
            send(command.toString());
        }
    }

    public void send(String text) {
        SMSModemBridgeHandler bridgeHandlerFinal = bridgeHandler;
        if (bridgeHandlerFinal != null) {
            bridgeHandlerFinal.send(config.recipient, text);
        } else {
            logger.warn("Only channel send in SMSConversation can receive command");
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(SMSConversationActions.class);
    }

    @Override
    public void initialize() {
        config = getConfigAs(SMSConversationConfiguration.class);
        bridgeHandler = getBridgeHandler();
        setStatusByBridgeStatus();
    }

    private void setStatusByBridgeStatus() {
        SMSModemBridgeHandler bridgeHandlerFinal = bridgeHandler;
        if (bridgeHandlerFinal != null) {
            switch (bridgeHandlerFinal.getThing().getStatus()) {
                case INITIALIZING:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                    break;
                case OFFLINE:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                    break;
                case ONLINE:
                    updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
                    break;
                case REMOVED:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                    break;
                case REMOVING:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                    break;
                case UNINITIALIZED:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                    break;
                case UNKNOWN:
                    updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.BRIDGE_OFFLINE);
                    break;
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }
}
