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
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.voice.habspeaker.internal.audio.internal.ConvertedAudioStream;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerAudioSource} class defines the speaker Audio Source
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerAudioSource implements AudioSource, AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerAudioSource.class);
    public static int SUPPORTED_BIT_DEPTH = 16;
    public static int SUPPORTED_SAMPLE_RATE = 16000;
    public static int SUPPORTED_CHANNELS = 1;
    public static AudioFormat SUPPORTED_FORMAT = new AudioFormat(AudioFormat.CONTAINER_WAVE,
            AudioFormat.CODEC_PCM_SIGNED, false, SUPPORTED_BIT_DEPTH, null, (long) SUPPORTED_SAMPLE_RATE,
            SUPPORTED_CHANNELS);
    private final String sourceId;
    private final String sourceLabel;
    private final HABSpeakerIOConnection speakerIO;
    private final ConcurrentLinkedQueue<HABSpeakerAudioStream> sourceStreams = new ConcurrentLinkedQueue<>();
    private final int streamSampleRate;
    private final boolean serverSpotting;
    private @Nullable PipedOutputStream sourceAudioPipedOutput;
    private @Nullable PipedInputStream sourceAudioPipedInput;
    private @Nullable InputStream sourceAudioStream;

    public HABSpeakerAudioSource(String id, String label, int streamSampleRate, boolean serverSpotting,
            HABSpeakerIOConnection speakerIO) {
        this.sourceId = id;
        this.sourceLabel = label;
        this.speakerIO = speakerIO;
        this.streamSampleRate = streamSampleRate;
        this.serverSpotting = serverSpotting;
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
            final HABSpeakerAudioStream stream = new HABSpeakerAudioStream(SUPPORTED_FORMAT) {
                @Override
                public void close() throws IOException {
                    removeSourceListener(this);
                    super.close();
                }
            };
            addSourceListener(stream);
            return stream;
        } catch (IOException e) {
            throw new AudioException(e);
        }
    }

    @Override
    public void close() throws Exception {
        new HashSet<>(sourceStreams).forEach(stream -> {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        });
    }

    public void writeToStreams(byte[] payload) {
        if (this.sourceAudioStream == null || this.sourceAudioPipedOutput == null) {
            logger.debug("Source already disposed ignoring data");
            return;
        }
        byte[] convertedPayload;
        try {
            this.sourceAudioPipedOutput.write(payload);
            int resampledLength = (payload.length) / (streamSampleRate / SUPPORTED_SAMPLE_RATE);
            logger.trace("resampling payload size {} => {}", payload.length, resampledLength);
            convertedPayload = this.sourceAudioStream.readNBytes(resampledLength);
        } catch (IOException e) {
            logger.error("Error writing source audio", e);
            return;
        }
        for (var sourceAudioStream : sourceStreams) {
            try {
                sourceAudioStream.write(convertedPayload);
            } catch (IOException e) {
                logger.debug("IOException while piping source data: {}", e.getMessage());
            }
        }
    }

    private void addSourceListener(HABSpeakerAudioStream output) {
        logger.debug("Registering source stream for '{}'", getId());
        synchronized (sourceStreams) {
            if (!sourceStreams.add(output)) {
                // never
                logger.error("unable to register source");
                return;
            }
            if (this.sourceAudioStream == null) {
                try {
                    var pipedOutput = new PipedOutputStream();
                    this.sourceAudioPipedOutput = pipedOutput;
                    var pipedInput = new PipedInputStream(pipedOutput, 4096 * 4);
                    this.sourceAudioPipedInput = pipedInput;
                    if (streamSampleRate != HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE) {
                        logger.debug("Enabling audio resampling for the audio source stream: {} => {}",
                                streamSampleRate, HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE);
                        var format = new AudioFormat(AudioFormat.CONTAINER_WAVE, AudioFormat.CODEC_PCM_SIGNED, false,
                                HABSpeakerAudioSource.SUPPORTED_BIT_DEPTH, null, (long) streamSampleRate,
                                HABSpeakerAudioSource.SUPPORTED_CHANNELS);
                        this.sourceAudioStream = new ConvertedAudioStream(pipedInput, format,
                                HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE, HABSpeakerAudioSource.SUPPORTED_CHANNELS,
                                true);
                    } else {
                        logger.debug("Audio source stream sample rate {}, no resampling needed",
                                HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE);
                        this.sourceAudioStream = pipedInput;
                    }
                } catch (IOException | UnsupportedAudioFormatException | UnsupportedAudioFileException e) {
                    logger.error("Unable to setup audio source stream", e);
                }
            }
            if (sourceStreams.size() == (serverSpotting ? 2 : 1)) {
                logger.debug("Send start listening {}", getId());
                speakerIO.setListening(true);
            }
        }
    }

    private void removeSourceListener(HABSpeakerAudioStream output) {
        logger.debug("Unregistering source stream for '{}'", getId());
        synchronized (sourceStreams) {
            if (!sourceStreams.remove(output)) {
                // never
                logger.error("unregistered source");
                return;
            }
            if (sourceStreams.size() == (serverSpotting ? 1 : 0)) {
                logger.debug("Send stop listening {}", getId());
                speakerIO.setListening(false);
            }
            if (sourceStreams.size() == 0) {
                logger.debug("Disposing audio source internal resources for '{}'", getId());
                if (this.sourceAudioStream != null) {
                    try {
                        this.sourceAudioStream.close();
                    } catch (IOException ignored) {
                    }
                    this.sourceAudioStream = null;
                }
                if (this.sourceAudioPipedOutput != null) {
                    try {
                        this.sourceAudioPipedOutput.close();
                    } catch (IOException ignored) {
                    }
                    this.sourceAudioPipedOutput = null;
                }
                if (this.sourceAudioPipedInput != null) {
                    try {
                        this.sourceAudioPipedInput.close();
                    } catch (IOException ignored) {
                    }
                    this.sourceAudioPipedInput = null;
                }
            }
        }
    }

    public static class HABSpeakerAudioStream extends AudioStream {
        private final AudioFormat format;
        private final PipedInputStream pipedInput;
        private final PipedOutputStream pipedOutput;
        private boolean closed = false;

        public HABSpeakerAudioStream(AudioFormat format) throws IOException {
            this.pipedOutput = new PipedOutputStream();
            this.pipedInput = new PipedInputStream(this.pipedOutput);
            this.format = format;
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
            pipedInput.close();
            pipedOutput.close();
        }
    }
}
