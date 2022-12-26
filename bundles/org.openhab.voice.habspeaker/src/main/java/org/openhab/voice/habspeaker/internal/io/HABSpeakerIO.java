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

import java.io.OutputStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerLanguageInterpreter;

/**
 * The {@link HABSpeakerIO} represents a speaker active connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIO {
    /**
     *
     * @return the speaker id
     */
    String getId();

    /**
     * Sets the speaker sink volume
     * 
     * @param value the desired volume level
     */
    void setSinkVolume(int value);

    /**
     * Gets the speaker sink volume
     *
     * @return the speaker sink volume level
     */
    int getSinkVolume();

    /**
     * Sets the speaker media volume
     *
     * @param value the desired volume level
     */
    void setMediaVolume(int value);

    /**
     * Gets the speaker media volume
     *
     * @return the speaker media volume level
     */
    int getMediaVolume();

    /**
     * Get thing handler
     *
     * @return the associated thing handler
     */
    @Nullable
    HABSpeakerIOHandler getThingHandler();

    /**
     * Set thing handler
     * 
     * @param handler the associated thing handler
     */
    void setThingHandler(@Nullable HABSpeakerIOHandler handler);

    /**
     * Send audio to the speaker
     *
     * @param streamId the id associated to the stream
     * @param data the audio bytes
     */
    void sendAudio(byte[] streamId, byte[] data);

    /**
     * Start streaming the speaker mic to this output stream.
     *
     * @param out the {@link OutputStream} to send audio to.
     */
    void addSourceListener(OutputStream out);

    /**
     * Stop streaming the speaker mic to this output stream.
     *
     * @param out the {@link OutputStream} to send audio to.
     */
    void removeSourceListener(OutputStream out);

    /**
     * Starts a dialog on the speaker
     */
    void spot();

    /**
     * Starts/stops communication with another speaker
     */
    void dropIn(@Nullable HABSpeakerIO anotherSpeakerIO) throws IllegalStateException;

    /**
     * Get current drop-in speaker
     */
    @Nullable
    HABSpeakerIO getDropIn();

    /**
     * Get language interpreter linked to the speaker
     */
    HABSpeakerLanguageInterpreter getLanguageInterpreter();

    /**
     * Resume/pause media playback if any
     */
    void playerCommand(PlayPauseType event);

    /**
     * Go to next/previous media track
     */
    void playerCommand(NextPreviousType event);

    /**
     * Rewind/Fast-forward media playback
     */
    void playerCommand(RewindFastforwardType event);

    /**
     * Seek media playback to second
     */
    void playerSeekToSecond(long second);

    /**
     * Seek media playback to percent
     */
    void playerSeekToPercent(int percent);

    /**
     * Stops media playback
     */
    void playerStop();

    /**
     * Starts media playback from provider
     */
    void playerStart(MediaProvider provider, String id);

    /**
     * Forces a speaker disconnection.
     */
    void disconnect();

    /**
     * Send a new spotify access token to the speaker
     */
    void updateSpotifyToken(String accessToken);

    /**
     * Media playback states
     */
    enum PlaybackStates {
        PLAYING("playing"),
        PAUSED("paused"),
        STOPPED("stopped"),
        BUFFERING("buffering");

        private final String state;

        PlaybackStates(String state) {
            this.state = state;
        }

        @Override
        public String toString() {
            return this.state;
        }
    }

    /**
     * Available media providers
     */
    enum MediaProvider {
        YOUTUBE("youtube"),
        SPOTIFY("spotify"),
        WEB_VIDEO("web-video"),
        WEB_AUDIO("web-audio");

        private final String name;

        MediaProvider(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    /**
     * Available media providers
     */
    enum SpotMode {
        SERVER("server"),
        RUSTPOTTER_WEB("rustpotter_web"),
        NONE("none");

        private final String name;

        SpotMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
