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
package org.openhab.binding.signal.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link SignalBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalBindingConstants {

    private static final String BINDING_ID = "signal";

    // List of all Thing Type UIDs
    public static final ThingTypeUID SIGNALCONVERSATION_THING_TYPE = new ThingTypeUID(BINDING_ID, "signalconversation");
    public static final ThingTypeUID SIGNALACCOUNTBRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID,
            "signalaccountbridge");
    public static final ThingTypeUID SIGNALLINKEDBRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, "signallinkedbridge");

    // List of all Channel ids
    public static final String CHANNEL_RECEIVED = "receive";
    public static final String CHANNEL_SEND = "send";
    public static final String CHANNEL_DELIVERYSTATUS = "deliverystatus";
    public static final String CHANNEL_TRIGGER_SIGNAL_RECEIVE = "receivetrigger";

    // List of all Parameters
    public static final String SIGNALCONVERSATION_PARAMETER_RECIPIENT = "recipient";
    public static final String SIGNALCONVERSATION_ASK_DELIVERY_REPORT = "deliveryReport";

    // List of all properties
    public static final String PROPERTY_QRCODE = "qrCode";
}
