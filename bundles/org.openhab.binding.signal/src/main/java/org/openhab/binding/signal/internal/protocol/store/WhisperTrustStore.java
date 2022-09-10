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

import java.io.InputStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.whispersystems.signalservice.api.push.TrustStore;

/**
 * Simple trust store from resource file
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class WhisperTrustStore implements TrustStore {

    private final String store;

    public WhisperTrustStore(String store) {
        super();
        this.store = store;
    }

    @Override
    public InputStream getKeyStoreInputStream() {
        InputStream resourceAsStream = getClass().getResourceAsStream("/" + store);
        if (resourceAsStream != null) {
            return resourceAsStream;
        } else {
            throw new IllegalArgumentException("Cannot get " + store + " store");
        }
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
}
