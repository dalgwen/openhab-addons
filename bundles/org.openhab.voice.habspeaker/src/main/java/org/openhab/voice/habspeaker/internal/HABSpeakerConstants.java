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
    public static final String SERVICE_NAME = "HABSpeaker";

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
    public static final String SOURCE_VOLUME_CHANNEL = "source-volume";
    public static final String MEDIA_CURRENT_SECOND_CHANNEL = "media-current-second";
    public static final String MEDIA_TOTAL_SECONDS_CHANNEL = "media-total-seconds";
    public static final String MEDIA_PROGRESS_CHANNEL = "media-progress";
    public static final String MEDIA_CONTROL_CHANNEL = "media-control";
    public static final String MEDIA_VOLUME_CHANNEL = "media-volume";
    public static final String PLAY_AUDIO_CHANNEL = "play-audio";
    public static final String PLAY_VIDEO_CHANNEL = "play-video";
    public static final String AUDIO_SEARCH_CHANNEL = "audio-search";
    public static final String VIDEO_SEARCH_CHANNEL = "video-search";
    public static final String SPOT_CHANNEL = "spot";
    public static final String DROP_IN_CHANNEL = "drop-in";
}
