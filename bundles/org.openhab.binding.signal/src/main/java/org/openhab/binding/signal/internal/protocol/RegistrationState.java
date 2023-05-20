/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Registration state with the whisper system
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public enum RegistrationState {
    NO_VALID_USER,
    CAPTCHA_NEEDED,
    RATE_LIMIT,
    VERIFICATION_CODE_NEEDED,
    QR_CODE_NEEDED,
    REGISTER_NEEDED,
    REGISTERED;
}
