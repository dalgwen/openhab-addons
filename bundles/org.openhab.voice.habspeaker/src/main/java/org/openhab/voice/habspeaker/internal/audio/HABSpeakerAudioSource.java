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
package org.openhab.voice.habspeaker.internal.audio;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.audio.AudioStream;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOConnection;

/**
 * The {@link HABSpeakerAudioSource} class defines the speaker Audio Source
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerAudioSource implements AudioSource, AutoCloseable {
    public static int SUPPORTED_BIT_DEPTH = 16;
    public static int SUPPORTED_SAMPLE_RATE = 16000;
    public static int SUPPORTED_CHANNELS = 1;
    public static AudioFormat SUPPORTED_FORMAT = new AudioFormat(AudioFormat.CONTAINER_WAVE,
            AudioFormat.CODEC_PCM_SIGNED, false, SUPPORTED_BIT_DEPTH, null, (long) SUPPORTED_SAMPLE_RATE,
            SUPPORTED_CHANNELS);
    private final static Set<HABSpeakerAudioStream> activeStreams = new HashSet<>();
    private final String sourceId;
    private final String sourceLabel;
    private final HABSpeakerIOConnection speakerIO;

    public HABSpeakerAudioSource(String id, String label, HABSpeakerIOConnection speakerIO) {
        this.sourceId = id;
        this.sourceLabel = label;
        this.speakerIO = speakerIO;
    }

    @Override
    public String getId() {
        return this.sourceId;
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return this.sourceLabel;
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return Set.of(SUPPORTED_FORMAT);
    }

    @Override
    public AudioStream getInputStream(AudioFormat audioFormat) throws AudioException {
        try {
            return new HABSpeakerAudioStream(speakerIO, SUPPORTED_FORMAT);
        } catch (IOException e) {
            throw new AudioException(e);
        }
    }

    @Override
    public void close() throws Exception {
        new HashSet<>(activeStreams).forEach(s -> {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        });
    }

    public static class HABSpeakerAudioStream extends AudioStream {
        private final HABSpeakerIOConnection speakerIO;
        private final AudioFormat format;
        private final PipedInputStream pipedInput;
        private final PipedOutputStream pipedOutput;
        private boolean closed = false;

        public HABSpeakerAudioStream(HABSpeakerIOConnection speakerIO, AudioFormat format) throws IOException {
            this.speakerIO = speakerIO;
            this.pipedOutput = new PipedOutputStream();
            this.pipedInput = new PipedInputStream(this.pipedOutput);
            this.format = format;
            activeStreams.add(this);
            speakerIO.addSourceListener(this);
        }

        public void write(byte[] bytes) throws IOException {
            this.pipedOutput.write(bytes);
        }

        @Override
        public AudioFormat getFormat() {
            return this.format;
        }

        @Override
        public int read() throws IOException {
            if (closed) {
                return -1;
            }
            return pipedInput.read();
        }

        @Override
        public int read(byte @Nullable [] b) throws IOException {
            if (closed) {
                return -1;
            }
            return pipedInput.read(b);
        }

        @Override
        public int read(byte @Nullable [] b, int off, int len) throws IOException {
            if (closed) {
                return -1;
            }
            return pipedInput.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            pipedOutput.close();
            pipedInput.close();
            speakerIO.removeSourceListener(this);
            activeStreams.remove(this);
        }
    }
}
