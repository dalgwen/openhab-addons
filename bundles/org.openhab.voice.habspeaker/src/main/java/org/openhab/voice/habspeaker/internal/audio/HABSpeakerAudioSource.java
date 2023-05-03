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
package org.openhab.voice.habspeaker.internal.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Locale;
import java.util.Set;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.audio.AudioStream;
import org.openhab.voice.habspeaker.internal.audio.internal.ConvertedAudioStream;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;

/**
 * The {@link HABSpeakerAudioSource} class defines the speaker Audio Source
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerAudioSource implements AudioSource {
    public static final AudioFormat HABSPEAKER_SOURCE_FORMAT = new AudioFormat(AudioFormat.CONTAINER_WAVE,
            AudioFormat.CODEC_PCM_SIGNED, false, 16, null, 16000L, 1);
    private final Set<AudioFormat> supportedFormats = Set.of(HABSPEAKER_SOURCE_FORMAT);
    private final String sourceId;
    private final String sourceLabel;
    private final HABSpeakerIO speakerIO;
    private final long streamSampleRate;

    public HABSpeakerAudioSource(String id, String label, HABSpeakerIO speakerIO, long streamSampleRate) {
        this.sourceId = id;
        this.sourceLabel = label;
        this.speakerIO = speakerIO;
        this.streamSampleRate = streamSampleRate;
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
        return supportedFormats;
    }

    @Override
    public AudioStream getInputStream(AudioFormat audioFormat) throws AudioException {
        try {
            var pipeOutput = new PipedOutputStream();
            var pipeInput = new PipedInputStream(pipeOutput, 4096 * 4) {
                @Override
                public void close() throws IOException {
                    speakerIO.removeSourceListener(pipeOutput);
                    super.close();
                }
            };
            speakerIO.addSourceListener(pipeOutput);
            var originalAudioFormat = new AudioFormat(AudioFormat.CONTAINER_WAVE, AudioFormat.CODEC_PCM_SIGNED, false,
                    16, null, this.streamSampleRate, 1);
            if (audioFormat.getFrequency() != null && audioFormat.getFrequency() != this.streamSampleRate) {
                var convertedAudioStream = new ConvertedAudioStream(pipeInput, originalAudioFormat,
                        audioFormat.getFrequency(), 1, true);
                return new HABSpeakerAudioStream(convertedAudioStream.getFormat(), convertedAudioStream);
            }
            return new HABSpeakerAudioStream(originalAudioFormat, pipeInput);
        } catch (IOException | UnsupportedAudioFileException e) {
            throw new AudioException(e);
        }
    }

    public static class HABSpeakerAudioStream extends AudioStream {
        private final InputStream input;
        private final AudioFormat format;
        private boolean closed = false;

        public HABSpeakerAudioStream(AudioFormat format, InputStream input) {
            this.input = input;
            this.format = format;
        }

        @Override
        public AudioFormat getFormat() {
            return this.format;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int bytesRead = read(b);
            if (-1 == bytesRead) {
                return bytesRead;
            }
            return b[0];
        }

        @Override
        public int read(byte @Nullable [] b) throws IOException {
            return read(b, 0, b == null ? 0 : b.length);
        }

        @Override
        public int read(byte @Nullable [] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            if (b == null) {
                throw new IOException("Buffer is null");
            }
            return input.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            input.close();
        }
    }
}
