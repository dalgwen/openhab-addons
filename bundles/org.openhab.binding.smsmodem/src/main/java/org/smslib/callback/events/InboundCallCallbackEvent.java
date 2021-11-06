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

package org.smslib.callback.events;

import org.smslib.message.MsIsdn;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class InboundCallCallbackEvent extends BaseCallbackEvent {
    MsIsdn msisdn;

    String gatewayId;

    public InboundCallCallbackEvent(MsIsdn msisdn, String gatewayId) {
        this.msisdn = msisdn;
        this.gatewayId = gatewayId;
    }

    public MsIsdn getMsisdn() {
        return this.msisdn;
    }

    public String getGatewayId() {
        return this.gatewayId;
    }
}
