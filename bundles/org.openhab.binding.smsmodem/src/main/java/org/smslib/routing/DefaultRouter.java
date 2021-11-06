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

import java.util.Collection;

import org.smslib.gateway.AbstractGateway;
import org.smslib.message.OutboundMessage;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class DefaultRouter extends AbstractRouter {
    @Override
    public Collection<AbstractGateway> customRoute(OutboundMessage message, Collection<AbstractGateway> gateways) {
        return gateways;
    }
}
