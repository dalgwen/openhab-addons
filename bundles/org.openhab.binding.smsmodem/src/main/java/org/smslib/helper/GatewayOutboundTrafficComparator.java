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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.gateway.AbstractGateway;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class GatewayOutboundTrafficComparator implements Comparator<AbstractGateway>, Serializable {
    static Logger logger = LoggerFactory.getLogger(GatewayOutboundTrafficComparator.class);

    private static final long serialVersionUID = 1L;

    @Override
    public int compare(AbstractGateway g1, AbstractGateway g2) {
        try {
            return (((g1.getStatistics().getTotalSent() + g1.getQueueLoad()) > (g2.getStatistics().getTotalSent()
                    + g2.getQueueLoad()))
                            ? 1
                            : (((g1.getStatistics().getTotalSent()
                                    + g1.getQueueLoad()) == (g2.getStatistics().getTotalSent() + g2.getQueueLoad()) ? 0
                                            : -1)));
        } catch (Exception e) {
            logger.error("Unhandled exception!", e);
            return 0;
        }
    }
}
