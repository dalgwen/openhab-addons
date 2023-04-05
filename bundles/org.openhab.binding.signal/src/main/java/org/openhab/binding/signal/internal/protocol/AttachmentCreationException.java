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

/**
 *
 * Used when creating attachment failed
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class AttachmentCreationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception instance.
     *
     * @param message exception message
     */
    public AttachmentCreationException(String message) {
        super(message);
    }

    /**
     * Creates a new exception instance.
     *
     * @param cause exception cause
     */
    public AttachmentCreationException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new exception instance.
     *
     * @param message exception message
     * @param cause exception cause
     */
    public AttachmentCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
