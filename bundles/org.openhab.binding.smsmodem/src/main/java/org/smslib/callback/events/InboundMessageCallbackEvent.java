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

package org.smslib.callback.events;

import org.smslib.message.InboundMessage;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class InboundMessageCallbackEvent extends BaseCallbackEvent {
    InboundMessage message;

    public InboundMessageCallbackEvent(InboundMessage message) {
        this.message = message;
    }

    public InboundMessage getMessage() {
        return this.message;
    }
}
