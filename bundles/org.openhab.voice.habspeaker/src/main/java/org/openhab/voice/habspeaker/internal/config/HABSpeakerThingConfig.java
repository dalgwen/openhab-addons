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
     * Custom hli service for this speaker
     */
    public String hli = "";
    /**
     * Enables ks service for this speaker
     */
    public String ks = "";
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
}
