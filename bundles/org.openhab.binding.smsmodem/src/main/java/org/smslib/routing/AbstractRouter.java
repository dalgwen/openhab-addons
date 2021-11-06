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

package org.smslib.routing;

import java.util.ArrayList;
import java.util.Collection;

import org.smslib.gateway.AbstractGateway;
import org.smslib.gateway.AbstractGateway.Status;
import org.smslib.helper.Common;
import org.smslib.message.OutboundMessage;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public abstract class AbstractRouter {
    public Collection<AbstractGateway> route(OutboundMessage message, Collection<AbstractGateway> gateways) {
        AbstractGateway candidateGateway;
        ArrayList<AbstractGateway> candidateGateways = new ArrayList<>();
        for (AbstractGateway g : gateways) {
            candidateGateway = null;
            if (Common.isNullOrEmpty(message.getGatewayId())) {
                candidateGateway = g;
            } else if (message.getGatewayId().equalsIgnoreCase(g.getGatewayId())) {
                candidateGateway = g;
            }
            if (candidateGateway == null) {
                continue;
            }
            if (candidateGateway.getStatus() != Status.Started) {
                continue;
            }
            if (!candidateGateway.getCapabilities().matches(message)) {
                continue;
            }
            candidateGateways.add(candidateGateway);
        }
        return customRoute(message, candidateGateways);
    }

    public abstract Collection<AbstractGateway> customRoute(OutboundMessage msg, Collection<AbstractGateway> gateways);
}
