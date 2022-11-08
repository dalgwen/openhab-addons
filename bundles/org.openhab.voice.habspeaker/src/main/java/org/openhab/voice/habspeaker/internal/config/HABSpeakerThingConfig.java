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
    /**
     * Custom label for this speaker sink and source
     */
    public String label = "";

    /**
     * Custom listeningItem for this speaker
     */
    public String listeningItem = "";

    /**
     * Default volume for the sink
     */
    public int sinkVolume = 100;
    /**
     * Use dual channel audio sink
     */
    public boolean sinkStereo = false;
}
