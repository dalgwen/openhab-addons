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

package org.smslib.message;

import org.smslib.pduUtils.gsm3040.SmsDeliveryPdu;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class InboundBinaryMessage extends InboundMessage {
    private static final long serialVersionUID = 1L;

    public InboundBinaryMessage(SmsDeliveryPdu pdu, String memLocation, int memIndex) {
        super(pdu, memLocation, memIndex);
        setPayload(new Payload(pdu.getUserDataAsBytes()));
    }
}
