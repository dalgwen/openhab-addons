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
package org.openhab.binding.habspeaker.internal.io.internal.messages.out.media;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.habspeaker.internal.config.HABSpeakerThingConfig;

/**
 * The {@link HABSpeakerThingConfig} class defines IO client configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerOutMediaStart extends HABSpeakerOutMediaMessage {
    public final String provider;
    public final String mediaId;
    public final long second;

    public HABSpeakerOutMediaStart(String provider, String mediaId, long second) {
        super(MediaMessageType.start);
        this.provider = provider;
        this.mediaId = mediaId;
        this.second = second;
    }
}
