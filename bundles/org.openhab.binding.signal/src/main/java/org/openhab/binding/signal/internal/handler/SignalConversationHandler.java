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

import org.asamk.signal.manager.api.RecipientAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.SignalBindingConstants;
import org.openhab.binding.signal.internal.SignalConversationConfiguration;
import org.openhab.binding.signal.internal.protocol.DeliveryReport;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.UuidUtil;

/**
 * The {@link SignalConversationHandler} is responsible for managing
 * discussion channels.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalConversationHandler extends BaseThingHandler {

    public static final ThingTypeUID SUPPORTED_THING_TYPES_UIDS = SignalBindingConstants.SIGNALCONVERSATION_THING_TYPE;

    private final Logger logger = LoggerFactory.getLogger(SignalConversationHandler.class);

    private @Nullable SignalBridgeHandler bridgeHandler;

    private SignalConversationConfiguration config;

    public SignalConversationHandler(Thing thing) {
        super(thing);
        this.config = new SignalConversationConfiguration();
    }

    public String getRecipient() {
        String recipient = config.recipient.trim();
        if (UuidUtil.isUuid(recipient)) {
            return recipient;
        } else {
            if (recipient.startsWith("+")) {
                return recipient;
            } else {
                return "+" + recipient;
            }
        }
    }

    private synchronized @Nullable SignalBridgeHandler getBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                logger.error("Required bridge not defined for SignalConversation {} with {}.", thing.getUID(),
                        getRecipient());
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof SignalBridgeHandler) {
                this.bridgeHandler = (SignalBridgeHandler) handler;
            } else {
                logger.error("No available bridge handler found for SignalConversation {} bridge {} .", thing.getUID(),
                        bridge.getUID());
                return null;
            }
        }
        return this.bridgeHandler;
    }

    protected void checkAndReceive(RecipientAddress address, String message) {
        String conversationRecipient = getRecipient();
        // is the recipient the one handled by this conversation ? :
        String uuid = address.getServiceId().toString();
        String number = address.getLegacyIdentifier();
        if (conversationRecipient.equals(uuid) || conversationRecipient.equals(number)) {
            updateState(SignalBindingConstants.CHANNEL_RECEIVED, new StringType(message));
        }
    }

    protected void checkAndUpdateDeliveryStatus(DeliveryReport deliveryReport) {
        String conversationRecipient = getRecipient();
        // is the recipient the one handled by this conversation ? :
        String uuid = deliveryReport.getAci();
        String number = deliveryReport.getE164();
        if (conversationRecipient.equals(uuid) || conversationRecipient.equals(number)) {
            updateState(SignalBindingConstants.CHANNEL_DELIVERYSTATUS,
                    new StringType(deliveryReport.getDeliveryStatus().name()));
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }
        if (channelUID.getId().equals(SignalBindingConstants.CHANNEL_SEND)) {
            send(command.toString());
            updateState(SignalBindingConstants.CHANNEL_SEND, new StringType(command.toString()));
        }
    }

    public void send(String text) {
        SignalBridgeHandler bridgeHandlerFinal = bridgeHandler;
        if (bridgeHandlerFinal != null) {
            bridgeHandlerFinal.send(getRecipient(), text);
        } else {
            logger.warn("Only channel 'send' in SignalConversation can receive command");
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(SignalConversationConfiguration.class);
        bridgeHandler = getBridgeHandler();
        updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
    }
}
