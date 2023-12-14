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
package org.openhab.binding.habspeaker.internal.io.internal.messages.out;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.binding.habspeaker.internal.config.HABSpeakerThingConfig;
import org.openhab.binding.habspeaker.internal.io.HABSpeakerIOConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerThingConfig} class defines IO client configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerOutConfig extends HABSpeakerOutMessage {

    public HABSpeakerOutConfig() {
        super(WebsocketOutputCommand.CONFIGURE);
    }

    private final static Logger logger = LoggerFactory.getLogger(HABSpeakerOutConfig.class);

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
    /**
     * Suspend audio context and connection in background.
     */
    public boolean suspendOnHide = false;
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
     * Use dual channel audio sink
     */
    public boolean useAudioElement = false;
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
    public HABSpeakerIOConnection.SpotMode spotMode = HABSpeakerIOConnection.SpotMode.NONE;

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
     * Speaker thing label
     */
    public String label = "";
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
        public float avgThreshold = 0.f;

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

    public static HABSpeakerOutConfig forSpeaker(HABSpeakerIOConnection speaker) {
        var configMsg = new HABSpeakerOutConfig();
        var handler = speaker.getThingHandler();
        if (handler == null) {
            var defaultConfig = new HABSpeakerThingConfig();
            configMsg.sampleRate = defaultConfig.sampleRate;
            configMsg.resampleMode = defaultConfig.clientResampleMode;
            configMsg.sinkVolume = defaultConfig.sinkVolume;
            configMsg.sourceVolume = defaultConfig.sourceVolume;
            configMsg.mediaVolume = defaultConfig.mediaVolume;
            return configMsg;
        }
        var config = handler.getSpeakerConfig();
        configMsg.sinkVolume = speaker.getSinkVolume() != -1 ? speaker.getSinkVolume() : config.sinkVolume;
        configMsg.sourceVolume = speaker.getSourceVolume() != -1 ? speaker.getSourceVolume() : config.sourceVolume;
        configMsg.mediaVolume = speaker.getMediaVolume() != -1 ? speaker.getMediaVolume() : config.mediaVolume;
        configMsg.sampleRate = config.sampleRate;
        configMsg.resampleMode = config.clientResampleMode;
        configMsg.useAudioElement = config.useAudioElement;
        configMsg.screenSaverTime = config.screenSaverTime;
        configMsg.dimScreen = config.dimScreen;
        configMsg.keepAwake = config.keepAwake;
        configMsg.suspendOnHide = config.suspendOnHide;
        if (!config.primaryColor.isBlank()) {
            configMsg.primaryColor = config.primaryColor;
        }
        if (!config.secondaryColor.isBlank()) {
            configMsg.secondaryColor = config.secondaryColor;
        }
        if (!config.tertiaryColor.isBlank()) {
            configMsg.tertiaryColor = config.tertiaryColor;
        }
        if (!config.logoUrl.isBlank()) {
            configMsg.logoUrl = config.logoUrl;
        }
        var label = handler.getLabel();
        if (label != null) {
            configMsg.label = label;
        }
        configMsg.spotMode = HABSpeakerIOConnection.SpotMode.NONE;
        if (!config.ks.isBlank()) {
            if (config.ks.equals(HABSpeakerConfigProvider.RUSTPOTTER_WEB_KS_ID)) {
                String wakewordFileName = config.rustpotterWakeword.toLowerCase();
                if (wakewordFileName.isBlank()) {
                    logger.warn("Missing rustpotter wakeword file, keyword spotting disabled");
                } else if (validateRustpotterWakeword(wakewordFileName)) {
                    logger.debug("Using rustpotter web with keyword '{}'", wakewordFileName);
                    configMsg.spotMode = HABSpeakerIOConnection.SpotMode.RUSTPOTTER_WEB;
                    var spotConfig = new RustpotterWebConfig();
                    spotConfig.wakeword = wakewordFileName;
                    spotConfig.avgThreshold = config.rustpotterAvgThreshold;
                    spotConfig.threshold = config.rustpotterThreshold;
                    spotConfig.minScores = config.rustpotterMinScores;
                    spotConfig.eager = config.rustpotterEager;
                    spotConfig.scoreMode = config.rustpotterScoreMode;
                    spotConfig.vadMode = config.rustpotterVADMode;
                    spotConfig.minGain = config.rustpotterMinGain;
                    spotConfig.maxGain = config.rustpotterMaxGain;
                    spotConfig.bandPass = config.rustpotterBandPass;
                    spotConfig.lowCutoff = config.rustpotterLowCutoff;
                    spotConfig.highCutoff = config.rustpotterHighCutoff;
                    spotConfig.bandSize = config.rustpotterBandSize;
                    spotConfig.scoreRef = config.rustpotterScoreRef;
                    spotConfig.gainNormalizer = config.rustpotterGainNormalizer;
                    if (config.rustpotterGainRef != null) {
                        spotConfig.gainRef = config.rustpotterGainRef;
                    }
                    configMsg.spotConfig = spotConfig;
                } else {
                    logger.warn("Missing rustpotter wakeword file '{}', keyword spotting disabled", wakewordFileName);
                }
            } else if (!config.ks.isEmpty()) {
                logger.warn("Selected server ks: {}", config.ks);
                configMsg.spotMode = HABSpeakerIOConnection.SpotMode.SERVER;
            } else {
                logger.warn("Missing ks service {}", config.ks);
            }
        }
        return configMsg;
    }

    private static boolean validateRustpotterWakeword(String modelName) {
        String suffix = ".rpw";
        String fileName = modelName;
        if (!fileName.endsWith(suffix)) {
            fileName = fileName + suffix;
        }
        var modelFile = java.nio.file.Path.of(HABSpeakerConfigProvider.RUSTPOTTER_FOLDER, fileName).toFile();
        return modelFile.exists();
    }
}
