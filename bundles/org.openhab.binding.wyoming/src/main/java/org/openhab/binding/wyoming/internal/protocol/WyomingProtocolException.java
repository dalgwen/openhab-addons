/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.wyoming.internal.protocol;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Exception for an unexpected protocol message
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class WyomingProtocolException extends Exception {

    private static final long serialVersionUID = 322306527992645679L;

    @Nullable
    private String json;

    public WyomingProtocolException(String message) {
        super(message);
    }

    public WyomingProtocolException(String message, Exception cause) {
        super(message, cause);
    }

    public void setJson(String json) {
        this.json = json;
    }

    public @Nullable String getJson() {
        return json;
    }
}
