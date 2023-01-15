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
    // Voice control config
    /**
     * Phrase to say on the speaker on command success
     */
    public String commandSentMessage = "done";

    /**
     * Phrase to start drop-in to other speaker
     */
    public String startDropInPhrase = "";

    /**
     * Phrase to stop drop-in on the current speaker
     */
    public String stopDropInPhrase = "";
    /**
     * Phrase to listen on web audio player
     */
    public String listenOnWebPhrase = "";
    /**
     * Phrase to watch on web video player
     */
    public String watchOnWebPhrase = "";
    /**
     * Phrase to listen on spotify
     */
    public String listenOnSpotifyPhrase = "";
    /**
     * Phrase to watch on YouTube
     */
    public String watchOnYouTubePhrase = "";
    /**
     * Phrase to continue media on another speaker
     */
    public String continueMediaOnPhrase = "";
    /**
     * Phrase to continue media into the current speaker
     */
    public String claimMediaPhrase = "";
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
     * Phrase to go to the next media item
     */
    public String nextMediaPhrase = "";
    /**
     * Phrase to go to the previous media item
     */
    public String previousMediaPhrase = "";
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
