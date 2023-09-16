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
package org.openhab.voice.habspeaker.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOClient;

/**
 * The {@link HABSpeakerThingConfig} class defines IO client configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerIOConfig {
    // UI config
    /**
     * Seconds to activate screen saver (0 for disabled)
     */
    public int screenSaverTime = 30;
    /**
     * Lower the screen brightness while the screen saver is enabled.
     */
    public boolean dimScreen = false;
    /**
     * Prevent device from going to sleep/block while running the application.
     */
    public boolean keepAwake = false;
    // Sink config
    /**
     * Default media volume
     */
    public int mediaVolume = 100;
    /**
     * Default volume for the sink
     */
    public int sinkVolume = 100;
    /**
     * Default volume for the sink
     */
    public int sourceVolume = 50;
    /**
     * Use dual channel audio sink
     */
    public boolean sinkStereo = false;
    /**
     * IO audio sample rate.
     */
    public long sampleRate = 16000;
    /**
     * Resample mode.
     */
    public String resampleMode = "SRC_LINEAR";
    /**
     * Keyword spot mode
     */
    public HABSpeakerIOClient.SpotMode spotMode = HABSpeakerIOClient.SpotMode.NONE;

    /**
     * Rustpotter Web config
     */
    public @Nullable RustpotterWebConfig spotConfig;
    /**
     * Theme primary color
     */
    public String primaryColor = "";
    /**
     * Theme secondary color
     */
    public String secondaryColor = "";
    /**
     * Theme tertiary color
     */
    public String tertiaryColor = "";
    /**
     * Logo url
     */
    public String logoUrl = "";

    // Rustpotter web config
    public static class RustpotterWebConfig {

        /**
         * Rustpotter web wakeword file
         */
        public String wakeword = "";
        /**
         * Rustpotter web threshold
         */
        public float threshold = 0.5f;
        /**
         * Rustpotter web averaged threshold
         */
        public float avgThreshold = 0.2f;

        /**
         * Rustpotter web min scores.
         */
        public int minScores = 5;

        /**
         * Rustpotter web eager.
         */
        public boolean eager = false;

        /**
         * Rustpotter web score mode.
         */
        public String scoreMode = "max";

        /**
         * Rustpotter web vad mode.
         */
        public @Nullable String vadMode = null;

        /**
         * Rustpotter web comparator reference.
         */
        public float scoreRef;
        /**
         * Rustpotter web comparator band size.
         */
        public int bandSize;
        /**
         * Rustpotter web gain-normalizer enabled.
         */
        public boolean gainNormalizer;
        /**
         * Rustpotter web gain-normalizer min gain.
         */
        public float minGain = 0.5f;
        /**
         * Rustpotter web gain-normalizer max gain.
         */
        public float maxGain = 1f;
        /**
         * Rustpotter web gain-normalizer ref.
         */
        @Nullable
        public Float gainRef = null;
        /**
         * Rustpotter web use band-pass filter
         */
        public boolean bandPass = false;
        /**
         * Rustpotter web band pass low cutoff
         */
        public float lowCutoff = 80f;
        /**
         * Rustpotter web band pass high cutoff
         */
        public float highCutoff = 400f;
    }
}
