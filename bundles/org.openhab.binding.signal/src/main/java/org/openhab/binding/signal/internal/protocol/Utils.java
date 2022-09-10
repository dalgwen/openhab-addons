/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

/**
 * Utility class
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class Utils {

    private static final Pattern UUID_REGEX = Pattern
            .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public static boolean isUUID(String maybeUUID) {
        return UUID_REGEX.matcher(maybeUUID).matches();
    }

    public static IdentityKeyPair createIdentityKeyPair() {
        ECKeyPair djbKeyPair = Curve.generateKeyPair();
        IdentityKey djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
        var djbPrivateKey = djbKeyPair.getPrivateKey();
        return new IdentityKeyPair(djbIdentityKey, djbPrivateKey);
    }

    public static List<PreKeyRecord> generatePreKeyRecords(final int offset, final int batchSize) {
        var records = new ArrayList<PreKeyRecord>(batchSize);
        for (var i = 0; i < batchSize; i++) {
            var preKeyId = (offset + i) % Medium.MAX_VALUE;
            var keyPair = Curve.generateKeyPair();
            var record = new PreKeyRecord(preKeyId, keyPair);
            records.add(record);
        }
        return records;
    }

    public static SignedPreKeyRecord generateSignedPreKeyRecord(final IdentityKeyPair identityKeyPair,
            final int signedPreKeyId) {
        var keyPair = Curve.generateKeyPair();
        byte[] signature;
        try {
            signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
        return new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
    }

    public static byte[] getStrippedPaddingMessageBody(byte[] messageWithPadding) {
        int paddingStart = 0;

        for (int i = messageWithPadding.length - 1; i >= 0; i--) {
            if (messageWithPadding[i] == (byte) 0x80) {
                paddingStart = i;
                break;
            } else if (messageWithPadding[i] != (byte) 0x00) {
                return messageWithPadding;
            }
        }

        byte[] strippedMessage = new byte[paddingStart];
        System.arraycopy(messageWithPadding, 0, strippedMessage, 0, strippedMessage.length);

        return strippedMessage;
    }

    /**
     * Format a number to international Format.
     *
     * @param identifier The recipient number
     * @param localNumber The local number allow extracting local national code
     * @return A formatted number in international format (or a signal uuid)
     * @throws InvalidNumberException
     */
    public static String formatPhoneNumber(String identifier, String localNumber) throws InvalidNumberException {
        if (isUUID(identifier)) {
            return identifier;
        }
        return PhoneNumberFormatter.formatNumber(identifier, localNumber);
    }
}
