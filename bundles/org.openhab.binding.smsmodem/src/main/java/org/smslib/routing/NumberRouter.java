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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.smslib.gateway.AbstractGateway;
import org.smslib.message.OutboundMessage;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class NumberRouter extends AbstractRouter {
    Map<String, AbstractGateway> rules = new HashMap<>();

    public Map<String, AbstractGateway> getRules() {
        return this.rules;
    }

    public void addRule(String addressRegEx, AbstractGateway gateway) {
        getRules().put(addressRegEx, gateway);
    }

    public void deleteRule(String pattern) {
        getRules().remove(pattern);
    }

    @Override
    public Collection<AbstractGateway> customRoute(OutboundMessage message, Collection<AbstractGateway> gateways) {
        Collection<AbstractGateway> candidates = new ArrayList<>();
        if (getRules().size() != 0) {
            Set<String> r = getRules().keySet();
            for (String rx : r) {
                Pattern p = Pattern.compile(rx);
                Matcher m = p.matcher(message.getRecipientAddress().getAddress());
                if (m.matches() && gateways.contains(getRules().get(rx))) {
                    candidates.add(getRules().get(rx));
                }
            }
        }
        return candidates;
    }
}
