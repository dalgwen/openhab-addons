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

package org.smslib.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public abstract class AbstractSymmetricKey extends AbstractKey {
    private SecretKeySpec key;

    public SecretKeySpec getKey() {
        return this.key;
    }

    public void setKey(SecretKeySpec key) {
        this.key = key;
    }

    public abstract SecretKeySpec generateKey() throws NoSuchAlgorithmException;

    public abstract byte[] encrypt(byte[] message) throws NoSuchAlgorithmException, NoSuchPaddingException,
            BadPaddingException, IllegalBlockSizeException, InvalidKeyException;

    public abstract byte[] decrypt(byte[] message) throws NoSuchAlgorithmException, NoSuchPaddingException,
            BadPaddingException, IllegalBlockSizeException, InvalidKeyException;
}
