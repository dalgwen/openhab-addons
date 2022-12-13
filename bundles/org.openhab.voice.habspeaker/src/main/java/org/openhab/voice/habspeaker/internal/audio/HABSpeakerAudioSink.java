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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.FixedLengthAudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.core.audio.UnsupportedAudioStreamException;
import org.openhab.core.library.types.PercentType;
import org.openhab.voice.habspeaker.internal.audio.internal.ConvertedInputStream;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerAudioSink} class defines the speaker Audio Sink
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerAudioSink implements AudioSink {
    private static final HashSet<AudioFormat> SUPPORTED_FORMATS = new HashSet<>();
    private static final HashSet<Class<? extends AudioStream>> SUPPORTED_STREAMS = new HashSet<>();

    static {
        SUPPORTED_FORMATS.add(AudioFormat.WAV);
        SUPPORTED_FORMATS.add(AudioFormat.MP3);
        SUPPORTED_STREAMS.add(FixedLengthAudioStream.class);
    }
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerAudioSink.class);

    private final String sinkId;
    private final String sinkLabel;
    private final HABSpeakerIO speakerIO;
    private final long targetSampleRate;
    private final int targetChannels;

    public HABSpeakerAudioSink(String id, String label, long sampleRate, int channels, HABSpeakerIO speakerIO) {
        this.sinkId = id;
        this.sinkLabel = label;
        this.targetSampleRate = sampleRate;
        this.targetChannels = channels;
        this.speakerIO = speakerIO;
    }

    @Override
    public String getId() {
        return this.sinkId;
    }

    @Override
    public @Nullable String getLabel(@Nullable Locale locale) {
        return this.sinkLabel;
    }

    @Override
    public void process(@Nullable AudioStream audioStream)
            throws UnsupportedAudioFormatException, UnsupportedAudioStreamException {
        if (audioStream == null) {
            return;
        }
        ConvertedInputStream convertedInputStream = null;
        var outputStream = new HABSpeakerAudioOutputStream(speakerIO);
        try {
            convertedInputStream = new ConvertedInputStream(audioStream, targetSampleRate, targetChannels);
            Instant start = Instant.now();
            convertedInputStream.transferTo(outputStream);
            if (convertedInputStream.getDuration() != -1) {
                Instant end = Instant.now();
                long millisSecondTimedToSendAudioData = Duration.between(start, end).toMillis();
                if (millisSecondTimedToSendAudioData < convertedInputStream.getDuration()) {
                    long timeToSleep = convertedInputStream.getDuration() - millisSecondTimedToSendAudioData;
                    logger.debug("Sleep time to let the system play sound : {}", timeToSleep);
                    Thread.sleep(timeToSleep);
                }
            }
        } catch (UnsupportedAudioFileException e) {
            logger.warn("UnsupportedAudioFileException: {}", e.getMessage());
        } catch (InterruptedIOException ignored) {
        } catch (IOException e) {
            logger.warn("IOException: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("InterruptedException: {}", e.getMessage());
        } finally {
            if (convertedInputStream != null) {
                try {
                    convertedInputStream.close();
                    logger.debug("ConvertedAudioStream {} closed", speakerIO.getId());
                } catch (IOException e) {
                    logger.warn("IOException: {}", e.getMessage(), e);
                }
            }
            try {
                audioStream.close();
                logger.debug("AudioStream {} closed", speakerIO.getId());
            } catch (IOException e) {
                logger.warn("IOException: {}", e.getMessage(), e);
            }
            try {
                outputStream.close();
                logger.debug("OutputStream {} closed", speakerIO.getId());
            } catch (IOException e) {
                logger.warn("IOException: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public Set<Class<? extends AudioStream>> getSupportedStreams() {
        return SUPPORTED_STREAMS;
    }

    @Override
    public PercentType getVolume() throws IOException {
        return new PercentType(speakerIO.getSinkVolume());
    }

    @Override
    public void setVolume(PercentType percentType) throws IOException {
        speakerIO.setSinkVolume(percentType.intValue());
    }

    private static class HABSpeakerAudioOutputStream extends OutputStream {
        private final byte[] id = generateId();
        private final HABSpeakerIO speakerIO;
        private boolean closed = false;

        public HABSpeakerAudioOutputStream(HABSpeakerIO speakerIO) {
            this.speakerIO = speakerIO;
        }

        @Override
        public void write(int b) throws IOException {
            write(ByteBuffer.allocate(4).putInt(b).array());
        }

        @Override
        public void write(byte @Nullable [] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            if (b != null) {
                speakerIO.sendAudio(id, b);
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private static byte[] generateId() {
            SecureRandom sr = new SecureRandom();
            byte[] rndBytes = new byte[4];
            sr.nextBytes(rndBytes);
            return rndBytes;
        }
    }
}
