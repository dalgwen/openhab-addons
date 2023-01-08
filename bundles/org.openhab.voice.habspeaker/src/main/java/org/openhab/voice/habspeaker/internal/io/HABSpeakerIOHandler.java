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
package org.openhab.voice.habspeaker.internal.io;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerThingConfig;

/**
 * The {@link HABSpeakerIOHandler} represents a speaker active connection handler.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIOHandler {
    /**
     * @return the label to use for the audio components
     */
    @Nullable
    String getLabel();

    /**
     * @return the label of the linked location if any
     */
    @Nullable
    String getLocationLabel();

    /**
     * @return Returns the spotify token or empty
     */
    String getSpotifyToken();

    /**
     * Returns the speaker config
     */
    HABSpeakerThingConfig getSpeakerConfig();

    /**
     * Is called on remote sink volume changes
     *
     * @param value current volume level (range 0 - 100)
     */
    void onSinkVolumeUpdate(int value);

    /**
     * Is called on remote media playback updates
     *
     * @param mediaState the speaker media state
     * @param volume the speaker media volume
     */
    void onMediaStateUpdate(HABSpeakerIO.MediaState mediaState, int volume);
}
