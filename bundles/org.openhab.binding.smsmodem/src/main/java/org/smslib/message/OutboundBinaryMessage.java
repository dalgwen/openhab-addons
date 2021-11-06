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

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class OutboundBinaryMessage extends OutboundMessage {
    private static final long serialVersionUID = 1L;

    public OutboundBinaryMessage() {
    }

    public OutboundBinaryMessage(MsIsdn originatorAddress, MsIsdn recipientAddress, byte[] data) {
        super(originatorAddress, recipientAddress, new Payload(data));
        setEncoding(Encoding.Enc8);
    }

    public OutboundBinaryMessage(MsIsdn recipient, byte[] data) {
        this(new MsIsdn(""), recipient, data);
    }

    public OutboundBinaryMessage(String originatorAddress, String recipientAddress, byte[] data) {
        this(new MsIsdn(originatorAddress), new MsIsdn(recipientAddress), data);
    }

    public OutboundBinaryMessage(String recipientAddress, byte[] data) {
        this(new MsIsdn(""), new MsIsdn(recipientAddress), data);
    }
}
