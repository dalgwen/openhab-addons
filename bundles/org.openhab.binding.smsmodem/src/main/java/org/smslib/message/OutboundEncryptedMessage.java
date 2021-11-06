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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.smslib.Service;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class OutboundEncryptedMessage extends OutboundBinaryMessage {
    private static final long serialVersionUID = 1L;

    public OutboundEncryptedMessage(MsIsdn recipientAddress, byte[] data) throws InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, NoSuchAlgorithmException {
        super(recipientAddress, data);
        setPayload(new Payload(Service.getInstance().getKeyManager().encrypt(recipientAddress, data)));
    }

    public OutboundEncryptedMessage(String recipientAddress, byte[] data) throws InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, NoSuchAlgorithmException {
        this(new MsIsdn(recipientAddress), data);
    }
}
