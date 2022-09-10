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
package org.openhab.binding.signal.internal.protocol.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.util.Pair;

/**
 * A wrapper object to handle the composite key of a sender
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SenderKeyIdentifier extends Pair<@Nullable SignalProtocolAddress, @Nullable UUID> implements Serializable {

    private static final long serialVersionUID = 5702915979530742898L;

    public SenderKeyIdentifier(@Nullable SignalProtocolAddress address, @Nullable UUID uuid) {
        super(address, uuid);
    }

    public static SenderKeyIdentifier deserialize(byte[] src) {
        ByteArrayInputStream bis = new ByteArrayInputStream(src);
        try (ObjectInput in = new ObjectInputStream(bis);) {
            SenderKeyIdentifier o = (SenderKeyIdentifier) in.readObject();
            return new SenderKeyIdentifier(o.first(), o.second());
        } catch (IOException | ClassNotFoundException ex) {
            throw new AssertionError("Cannot deserialize object in memory, should not happen", ex);
        }
    }

    public byte[] serialize() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new AssertionError("Cannot serialize object in memory, should not happen", ex);
        }
    }
}
