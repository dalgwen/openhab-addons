/**
 * Copyright (c) 2023 Contributors to the Seime Openhab Addons project
 * Copyright (c) 2025 Contributors to the ESPHome binding extension
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.esphome.internal.handler;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.audio.AudioStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ESPHomeAudioSource} is an AudioSource implementation for ESPHome devices that support
 * the voice assistant component with microphone streaming.
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class ESPHomeAudioSource implements AudioSource {

    private static final int DEFAULT_PIPE_SIZE = 1024 * 10;

    private final Logger logger = LoggerFactory.getLogger(ESPHomeAudioSource.class);
    private final String thingUID;
    private final AudioFormat audioFormat;
    private final Set<AudioFormat> supportedFormats;
    private final PipedOutputStream pipedOutput;
    private final PipedInputStream pipedInput;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public ESPHomeAudioSource(String thingUID, AudioFormat audioFormat) throws IOException {
        this.thingUID = thingUID;
        this.audioFormat = audioFormat;
        this.supportedFormats = Set.of(audioFormat);
        this.pipedInput = new PipedInputStream(DEFAULT_PIPE_SIZE);
        this.pipedOutput = new PipedOutputStream(pipedInput);
    }

    @Override
    public String getId() {
        return thingUID + ":voice";
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return "ESPHome Voice Assistant";
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return supportedFormats;
    }

    @Override
    public AudioStream getInputStream(AudioFormat format) throws AudioException {
        if (!format.isCompatible(audioFormat)) {
            throw new AudioException("Incompatible audio format: " + format);
        }
        return new AudioStreamImpl(pipedInput, audioFormat, isClosed);
    }

    /**
     * Writes PCM audio data to the pipe buffer.
     *
     * @param data raw PCM audio bytes
     * @param length number of bytes to write
     * @throws IOException if writing fails
     */
    public void writeAudioData(byte[] data, int length) throws IOException {
        if (!isClosed.get()) {
            pipedOutput.write(data, 0, length);
            pipedOutput.flush();
        }
    }

    /**
     * Closes the audio source and releases resources.
     */
    public void close() {
        isClosed.set(true);
        try {
            pipedOutput.close();
        } catch (IOException e) {
            logger.debug("[{}] Error closing piped output stream", thingUID, e);
        }
    }

    /**
     * Simple AudioStream implementation wrapping a PipedInputStream.
     */
    private static class AudioStreamImpl extends AudioStream {
        private final PipedInputStream inputStream;
        private final AudioFormat format;
        private final AtomicBoolean closed;

        AudioStreamImpl(PipedInputStream inputStream, AudioFormat format, AtomicBoolean isClosed) {
            this.inputStream = inputStream;
            this.format = format;
            this.closed = isClosed;
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        // Do NOT override read(byte[], int, int) - let the parent InputStream handle it
        // The parent class's read() is final and delegates to read(byte[], int, int)
        // which we cannot safely override due to ECJ parameter naming constraints

        @Override
        public void close() throws IOException {
            // Don't close the underlying pipe - let the source manage it
        }
    }
}
