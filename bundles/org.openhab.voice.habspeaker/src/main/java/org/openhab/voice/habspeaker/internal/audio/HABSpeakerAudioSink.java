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
        SUPPORTED_FORMATS.add(
                new AudioFormat(AudioFormat.CONTAINER_WAVE, AudioFormat.CODEC_PCM_SIGNED, false, 16, null, 16000L));
        SUPPORTED_FORMATS.add(AudioFormat.WAV);
        SUPPORTED_FORMATS.add(AudioFormat.MP3);
        SUPPORTED_STREAMS.add(FixedLengthAudioStream.class);
    }
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerAudioSink.class);

    private final String sinkId;
    private final String sinkLabel;
    private final HABSpeakerIO speakerIO;
    private final int preferredChannels;

    public HABSpeakerAudioSink(String id, String label, HABSpeakerIO speakerIO, int preferredChannels) {
        this.sinkId = id;
        this.sinkLabel = label;
        this.speakerIO = speakerIO;
        this.preferredChannels = preferredChannels;
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
        var format = audioStream.getFormat();
        ConvertedInputStream convertedInputStream = null;
        OutputStream outputStream = null;
        try {
            if (isDirectStreamSupported(format)) {
                logger.debug("The audio format can be streamed");
                var channels = format.getChannels();
                var bitDepth = format.getBitDepth();
                var sampleRate = format.getFrequency();
                StreamType type = channels == null || channels == 1 ? StreamType.PCM16BitMono
                        : StreamType.PCM16BitStereo;
                int duration = -1;
                if (audioStream instanceof FixedLengthAudioStream) {
                    var audioLength = ((FixedLengthAudioStream) audioStream).length();
                    if (audioLength > 0 && channels != null && bitDepth != null && sampleRate != null) {
                        float durationInSeconds = (audioLength / (float) (bitDepth * sampleRate * channels));
                        duration = Math.round(durationInSeconds * 1000);
                        logger.debug("Duration of input stream : {}ms", duration);
                    }
                }
                outputStream = new HABSpeakerAudioOutputStream(speakerIO, type);
                transferAudio(audioStream, outputStream, duration);
            } else {
                logger.debug("The audio format can't be streamed, try to convert");
                // we try to convert to one of the supported stream formats
                var requiredSampleRate = 16000;
                var streamType = preferredChannels == 1 ? StreamType.PCM16BitMono : StreamType.PCM16BitStereo;
                outputStream = new HABSpeakerAudioOutputStream(speakerIO, streamType);
                convertedInputStream = new ConvertedInputStream(audioStream, requiredSampleRate, preferredChannels);
                transferAudio(convertedInputStream, outputStream, convertedInputStream.getDuration());
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
                if (outputStream != null) {
                    outputStream.close();
                }
                logger.debug("OutputStream {} closed", speakerIO.getId());
            } catch (IOException e) {
                logger.warn("IOException: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isDirectStreamSupported(AudioFormat format) {
        var bigEndian = format.isBigEndian();
        var bitDepth = format.getBitDepth();
        var frequency = format.getFrequency();
        return AudioFormat.WAV.isCompatible(format) && //
                bitDepth != null && bitDepth == 16 && //
                frequency != null && frequency == 16000L && //
                bigEndian != null && !bigEndian;
    }

    private void transferAudio(InputStream convertedInputStream, OutputStream outputStream, long duration)
            throws IOException, InterruptedException {
        Instant start = Instant.now();
        convertedInputStream.transferTo(outputStream);
        if (duration != -1) {
            Instant end = Instant.now();
            long millisSecondTimedToSendAudioData = Duration.between(start, end).toMillis();
            if (millisSecondTimedToSendAudioData < duration) {
                long timeToSleep = duration - millisSecondTimedToSendAudioData;
                logger.debug("Sleep time to let the system play sound : {}ms", timeToSleep);
                Thread.sleep(timeToSleep);
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
        private final byte[] id;
        private final HABSpeakerIO speakerIO;
        private boolean closed = false;

        public HABSpeakerAudioOutputStream(HABSpeakerIO speakerIO, StreamType streamFormat) {
            this.speakerIO = speakerIO;
            this.id = generateId(streamFormat);
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

        private static byte[] generateId(StreamType streamFormat) {
            SecureRandom sr = new SecureRandom();
            byte[] rndBytes = new byte[5];
            sr.nextBytes(rndBytes);
            rndBytes[4] = streamFormat.get();
            return rndBytes;
        }
    }

    private enum StreamType {
        // 16000Hz 16bit int 1 channel little-endian
        PCM16BitMono((byte) 1),
        // 16000Hz 16bit int 2 channel little-endian
        PCM16BitStereo((byte) 2);

        private final byte b;

        StreamType(byte b) {
            this.b = b;
        }

        public byte get() {
            return this.b;
        }
    }
}
