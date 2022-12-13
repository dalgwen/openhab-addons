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
package org.openhab.voice.habspeaker.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HABSpeakerConfig} class defines the speaker configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerConfig {
    // Service config
    /**
     * Require security to use the speaker.
     */
    public boolean secure;
    // Voice control config
    /**
     * Phrase to say on the speaker on command success
     */
    public String commandSentMessage = "done";

    /**
     * Phrase to stop drop-in on the current speaker
     */
    public String stopDropInPhrase = "";
    /**
     * Phrase to listen on spotify
     */
    public String listenOnSpotifyPhrase = "";
    /**
     * Phrase to watch on YouTube
     */
    public String watchOnYouTubePhrase = "";
    /**
     * Phrase to resume media
     */
    public String resumeMediaPhrase = "";
    /**
     * Phrase to pause media
     */
    public String pauseMediaPhrase = "";
    /**
     * Phrase to stop media
     */
    public String stopMediaPhrase = "";
    /**
     * Phrase to decrease media volume
     */
    public String decreaseMediaVolumePhrase = "";
    /**
     * Phrase to increase media volume
     */
    public String increaseMediaVolumePhrase = "";
    /**
     * Phrase fast-forward the media progress
     */
    public String fastForwardMediaProgressPhrase = "";
    /**
     * Phrase rewind the media progress
     */
    public String rewindMediaProgressPhrase = "";
    /**
     * Volume step used by the increase/decrease media volume phrases
     */
    public int mediaVolumeStep;

    // Credentials config
    /**
     * Spotify app client id
     */
    public String spotifyClientId = "";
    /**
     * Youtube app api key
     */
    public String youtubeAPIKey = "";
}
