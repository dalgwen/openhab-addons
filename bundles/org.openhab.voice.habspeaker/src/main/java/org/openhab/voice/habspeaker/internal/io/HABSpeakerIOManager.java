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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link HABSpeakerIOManager} interface represents the speaker connection manager.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIOManager {
    /**
     * Get speaker connection by id.
     * 
     * @param id the speaker identifier
     * @return active speaker connection if any
     */
    @Nullable
    HABSpeakerIOClient getSpeakerConnection(String id);

    /**
     * Get active speaker connections
     * 
     * @return list of active speaker connections
     */
    List<HABSpeakerIOClient> getSpeakerConnections();

    /**
     * Sets the active connection listener
     * 
     * @param connectionListener the connection observer
     */
    void setConnectionListener(@Nullable HABSpeakerIOListener connectionListener);

    /**
     * Dispose manager and close all active connections
     */
    void dispose();
}
