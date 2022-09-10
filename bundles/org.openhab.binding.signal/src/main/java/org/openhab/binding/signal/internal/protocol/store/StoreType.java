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
 * Store type from storage
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public enum StoreType {
    IDKEY_STORE,
    PREKEY_STORE,
    SENDERKEY_STORE,
    SENDERKEYSHAREDWITH_STORE,
    SESSION_STORE,
    SIGNEDPREKEY_STORE;
}
