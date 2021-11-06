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
import org.smslib.pduUtils.gsm3040.SmsDeliveryPdu;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class InboundEncryptedMessage extends InboundBinaryMessage {
    private static final long serialVersionUID = 1L;

    public InboundEncryptedMessage(SmsDeliveryPdu pdu, String memLocation, int memIndex) {
        super(pdu, memLocation, memIndex);
    }

    public byte[] getDecryptedData() throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            NoSuchPaddingException, NoSuchAlgorithmException {
        if (Service.getInstance().getKeyManager().getKey(getOriginatorAddress()) != null) {
            return (Service.getInstance().getKeyManager().decrypt(getOriginatorAddress(), getPayload().getBytes()));
        }
        return new byte[0];
    }
}
