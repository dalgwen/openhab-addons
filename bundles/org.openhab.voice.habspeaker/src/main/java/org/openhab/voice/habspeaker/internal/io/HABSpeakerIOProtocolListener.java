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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HABSpeakerIOProtocolListener} represents a listener for the speaker connection events.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIOProtocolListener {
    void onConnected(HABSpeakerIO speaker) throws IllegalStateException;

    void onDisconnected(HABSpeakerIO speaker);
}
