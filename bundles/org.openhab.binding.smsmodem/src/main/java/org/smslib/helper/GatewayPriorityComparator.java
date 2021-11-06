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

package org.smslib.helper;

import java.io.Serializable;
import java.util.Comparator;

import org.smslib.gateway.AbstractGateway;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class GatewayPriorityComparator implements Comparator<AbstractGateway>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public int compare(AbstractGateway g1, AbstractGateway g2) {
        return ((g1.getPriority() > g2.getPriority()) ? 1 : ((g1.getPriority() == g2.getPriority() ? 0 : -1)));
    }
}
