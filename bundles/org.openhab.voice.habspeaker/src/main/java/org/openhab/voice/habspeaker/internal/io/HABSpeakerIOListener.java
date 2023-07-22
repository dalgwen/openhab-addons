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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HABSpeakerIOListener} observes the active speaker connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIOListener {
    /**
     * Called on speaker connection
     * 
     * @param speaker active speaker connection
     */
    void onConnected(HABSpeakerIOClient speaker);

    /**
     * Called on speaker disconnection
     * 
     * @param speaker inactive speaker connection
     */
    void onDisconnected(HABSpeakerIOClient speaker);
}
