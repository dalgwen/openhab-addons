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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Launch to notify incomplete registration
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class IncompleteRegistrationException extends Exception {

    private static final long serialVersionUID = 7319532258732939073L;

    private RegistrationState registrationState;
    private String additionnalMessage;

    public IncompleteRegistrationException(RegistrationState registrationState) {
        this(registrationState, "");
    }

    public IncompleteRegistrationException(RegistrationState registrationState, String additionnalMessage) {
        super();
        this.registrationState = registrationState;
        this.additionnalMessage = additionnalMessage;
    }

    public RegistrationState getRegistrationState() {
        return registrationState;
    }

    @Override
    public @Nullable String getMessage() {
        return registrationState.name() + " " + additionnalMessage;
    }
}
