/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import java.io.Serial;

/**
 *
 * Used when the response has an error element
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class JsonResponseException extends Exception {

    @Serial
    private static final long serialVersionUID = 3091625747064284241L;

    private final JsonResponse.Error error;

    public JsonResponseException(String message, JsonResponse.Error error) {
        super(message);
        this.error = error;
    }

    public JsonResponse.Error getError() {
        return error;
    }
}
