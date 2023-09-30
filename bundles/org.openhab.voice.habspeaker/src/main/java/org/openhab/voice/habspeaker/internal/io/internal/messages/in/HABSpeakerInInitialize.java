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
package org.openhab.voice.habspeaker.internal.io.internal.messages.in;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerThingConfig;

/**
 * The {@link HABSpeakerThingConfig} abstract class for input messages from the speaker
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerInInitialize extends HABSpeakerInMessage {

    public String id = "";
    public int sampleRate;

    public HABSpeakerInInitialize() {
        super(InputCommand.INITIALIZE);
    }
}
