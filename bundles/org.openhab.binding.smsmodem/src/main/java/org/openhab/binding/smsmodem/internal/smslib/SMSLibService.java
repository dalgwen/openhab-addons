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
package org.openhab.binding.smsmodem.internal.smslib;

import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.Service;
import org.smslib.callback.IGatewayStatusCallback;
import org.smslib.callback.IInboundMessageCallback;
import org.smslib.callback.events.GatewayStatusCallbackEvent;
import org.smslib.callback.events.InboundMessageCallbackEvent;
import org.smslib.message.InboundMessage;

/**
 * Handle communication with the SMSLibv2 framework
 * (Abstract the smslibservice instance)
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SMSLibService implements IGatewayStatusCallback, IInboundMessageCallback {

    private final Logger logger = LoggerFactory.getLogger(SMSLibService.class);

    public static final SMSLibService instance = new SMSLibService();

    private HashMap<String, SMSModem> modemById = new HashMap<>();

    private SMSLibService() {
        Service.getInstance().start();
        Service.getInstance().setGatewayStatusCallback(this);
        Service.getInstance().setInboundMessageCallback(this);
    }

    public void registerModem(SMSModem modem) {
        modemById.put(modem.getId(), modem);
        Service.getInstance().registerGateway(modem.gateway);
    }

    public void unregisterModem(SMSModem modem) {
        modemById.remove(modem.getId());
        Service.getInstance().unregisterGateway(modem.gateway);
    }

    public void stop() {
        Service.getInstance().setGatewayStatusCallback(null);
        Service.getInstance().setInboundMessageCallback(null);
        Service.getInstance().stop();
        Service.getInstance().terminate();
    }

    @Override
    public boolean process(@Nullable InboundMessageCallbackEvent event) {
        if (event != null) {
            InboundMessage message = event.getMessage();
            SMSModem modem = modemById.get(message.getGatewayId());
            if (modem != null) {
                modem.receive(message);
                return true;
            }
        }
        logger.error("Receiving event message null or with no known gateway");
        return true;
    }

    @Override
    public boolean process(@Nullable GatewayStatusCallbackEvent event) {

        if (event != null) {
            SMSModem smsModem = modemById.get(event.getGateway().getGatewayId());
            if (smsModem != null) {
                smsModem.newStatus(event.getNewStatus());
                return true;
            }
        }
        logger.warn("Receiving status for no known gateway");
        return true;
    }
}
