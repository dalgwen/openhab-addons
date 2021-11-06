/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

package org.smslib.gateway.modem;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class ModemResponse {
    String responseData;

    boolean responseOk;

    public ModemResponse(String responseData, boolean responseOk) {
        this.responseData = responseData;
        this.responseOk = responseOk;
    }

    public String getResponseData() {
        return this.responseData;
    }

    public boolean isResponseOk() {
        return this.responseOk;
    }
}
