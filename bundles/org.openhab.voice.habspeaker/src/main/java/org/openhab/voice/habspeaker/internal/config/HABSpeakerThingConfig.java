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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link HABSpeakerThingConfig} class defines the speaker configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerThingConfig {
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
    // Voice config
    /**
     * Custom tts service for this speaker
     */
    public String tts = "";
    /**
     * Custom stt service for this speaker
     */
    public String stt = "";
    /**
     * Custom voice service for this speaker
     */
    public String voice = "";
    /**
     * Custom interpreters chain for this speaker
     */
    public List<String> hlis = List.of();
    /**
     * Enables ks service for this speaker
     */
    public String ks = "";
    /**
     * Custom keyword for this speaker
     */
    public String keyword = "";
    /**
     * Custom listeningItem for this speaker
     */
    public String listeningItem = "";
    /**
     * Location item for this speaker
     */
    public String location = "";
    // Sink config
    /**
     * Default volume for the sink
     */
    public int sinkVolume = 100;
    /**
     * Use dual channel audio sink
     */
    public boolean sinkStereo = false;
    /**
     * Audio sample rate.
     */
    public long sampleRate = -1L;
    /**
     * Audio sample rate.
     */
    public String clientResampleMode = "wasm_sinc_medium_quality";
    // Rustpotter web config
    /**
     * Rustpotter web threshold
     */
    public float rustpotterThreshold = 0.5f;
    /**
     * Rustpotter web averaged threshold
     */
    public float rustpotterAvgThreshold = 0.2f;

    /**
     * Rustpotter web min scores.
     */
    public int rustpotterMinScores = 5;

    /**
     * Rustpotter web score mode.
     */
    public String rustpotterScoreMode = "max";

    /**
     * Rustpotter web comparator reference.
     */
    public float rustpotterComparatorRef;
    /**
     * Rustpotter web comparator band size.
     */
    public int rustpotterComparatorBandSize;
    /**
     * Rustpotter web gain-normalizer enabled.
     */
    public boolean rustpotterGainNormalizer;
    /**
     * Rustpotter web gain-normalizer min gain.
     */
    public float rustpotterMinGain = 0.5f;
    /**
     * Rustpotter web gain-normalizer max gain.
     */
    public float rustpotterMaxGain = 1f;
    /**
     * Rustpotter web gain-normalizer ref.
     */
    @Nullable
    public Float rustpotterGainRef = null;
    /**
     * Rustpotter web use band-pass filter
     */
    public boolean rustpotterBandPass = false;
    /**
     * Rustpotter web band pass low cutoff
     */
    public float rustpotterLowCutoff = 80f;
    /**
     * Rustpotter web band pass high cutoff
     */
    public float rustpotterHighCutoff = 400f;
}
