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
package org.openhab.voice.habspeaker.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link HABSpeakerConstants} class defines common constants, which are
 * used across the whole addon.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerConstants {

    /**
     * Service name
     */
    public static final String SERVICE_NAME = "HAB Speaker";

    /**
     * Service id
     */
    public static final String SERVICE_ID = "habspeaker";

    /**
     * Service category
     */
    public static final String SERVICE_CATEGORY = "voice";

    /**
     * Service pid
     */
    public static final String SERVICE_PID = "org.openhab." + SERVICE_CATEGORY + "." + SERVICE_ID;
    // THINGS
    /**
     * Speaker thing uid
     */
    public static final ThingTypeUID SPEAKER_THING_TYPE = new ThingTypeUID(SERVICE_ID, "speaker");
    // CHANNELS
    public static final String SINK_VOLUME_CHANNEL = "sink-volume";
}
