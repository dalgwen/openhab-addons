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
package org.openhab.voice.habspeaker.internal.io;

import java.io.OutputStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSource;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerLanguageInterpreter;

/**
 * The {@link HABSpeakerIOConnection} represents a speaker active connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIOConnection {
    /**
     * Get the speaker identifier
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
     * Sets the speaker source volume
     * 
     * @param value the desired volume level
     */
    void setSourceVolume(int value);

    /**
     * Gets the speaker source volume
     *
     * @return the speaker source volume level
     */
    int getSourceVolume();

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
     * Gets the speaker media state
     *
     * @return the speaker media state
     */
    @Nullable
    MediaState getMediaState();

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
    void addSourceListener(HABSpeakerAudioSource.HABSpeakerAudioStream out);

    /**
     * Stop streaming the speaker mic to this output stream.
     *
     * @param out the {@link OutputStream} to send audio to.
     */
    void removeSourceListener(HABSpeakerAudioSource.HABSpeakerAudioStream out);

    /**
     * Starts a dialog on the speaker
     */
    void spot();

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
    void playerStart(StartMediaMessage msg);

    /**
     * Claim provider playback, for providers that allow claim playback
     */
    void playerClaim(MediaProvider provider);

    /**
     * Forces a speaker disconnection.
     */
    void disconnect();

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
        VIDEO_PLAYER("video-player"),
        AUDIO_PLAYER("audio-player");

        private final String name;

        MediaProvider(String name) {
            this.name = name;
        }

        public static @Nullable MediaProvider fromString(String name) {
            switch (name) {
                case "":
                    return null;
                case "video-player":
                    return VIDEO_PLAYER;
                case "audio-player":
                    return AUDIO_PLAYER;
                default:
                    throw new IllegalStateException("Unexpected value: " + name);
            }
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

    /**
     * Describes the speaker media state
     */
    class MediaState {
        @Nullable
        public final MediaProvider provider;
        @Nullable
        public final String mediaId;
        public final long currentSecond;
        public final long totalSeconds;
        public final PlaybackStates playbackState;

        public MediaState(@Nullable MediaProvider provider, String mediaId, long currentSecond, long totalSeconds,
                PlaybackStates playbackState) {
            this.provider = provider;
            this.mediaId = mediaId;
            this.currentSecond = currentSecond;
            this.totalSeconds = totalSeconds;
            this.playbackState = playbackState;
        }
    }

    /**
     * Contains the media information to start playing
     */
    class StartMediaMessage {
        public final MediaProvider provider;
        public final String mediaId;
        public final long startSecond;

        public StartMediaMessage(MediaProvider provider, String mediaId, long startSecond)
                throws IllegalStateException {
            this.provider = provider;
            this.mediaId = mediaId;
            this.startSecond = startSecond;
        }
    }
}
