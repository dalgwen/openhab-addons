/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.wyoming.internal.protocol.message.data;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class AudioChunkData extends WyomingData {

    private int rate; // sample rate in Hertz (required)
    private int width; // sample width in bytes (required)
    private int channels; // number of channels (required)
    @Nullable
    private Integer timestamp; // timestamp of audio chunk in milliseconds (optional)

    public AudioChunkData(int rate, int width, int channels) {
        this.rate = rate;
        this.width = width;
        this.channels = channels;
    }

    public int getRate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    @Nullable
    public Integer getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Integer timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AudioChunk{" + "rate=" + rate + ", width=" + width + ", channels=" + channels + ", timestamp="
                + timestamp + '}';
    }
}
