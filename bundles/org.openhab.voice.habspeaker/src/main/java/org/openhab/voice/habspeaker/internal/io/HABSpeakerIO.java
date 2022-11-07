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

import java.io.OutputStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.voice.habspeaker.internal.handler.HABSpeakerThingHandler;

/**
 * The {@link HABSpeakerIO} represents a speaker active connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public interface HABSpeakerIO {
    /**
     *
     * @return the speaker id
     */
    String getId();

    /**
     * Sets the speaker sink volume
     * 
     * @param value
     */
    void setSinkVolume(int value);

    /**
     * Gets the speaker sink volume
     * 
     * @return the speaker sink volume
     */
    int getSinkVolume();

    /**
     * Set thing handler
     * 
     * @param handler the associated thing handler
     */
    void setThingHandler(@Nullable HABSpeakerThingHandler handler);

    /**
     * Send audio to the speaker
     *
     * @param streamId the id associated to the stream
     * @param data the audio bytes
     */
    void sendAudio(byte[] streamId, byte[] data);

    /**
     * Start streaming the speaker mic to this output stream.
     *
     * @param out the {@link OutputStream} to send audio to.
     */
    void addSourceListener(OutputStream out);

    /**
     * Stop streaming the speaker mic to this output stream.
     *
     * @param out the {@link OutputStream} to send audio to.
     */
    void removeSourceListener(OutputStream out);

    /**
     * Forces a speaker disconnection.
     */
    void disconnect();
}
