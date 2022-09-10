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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Wrapper for storage issue
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = -5085889032797812846L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Exception cause) {
        super(message, cause);
    }
}
