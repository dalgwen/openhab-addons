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
package org.openhab.binding.habspeaker.internal.audio;

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
import org.openhab.binding.habspeaker.internal.audio.internal.ConvertedAudioStream;
import org.openhab.binding.habspeaker.internal.io.HABSpeakerIOConnection;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.FixedLengthAudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.core.audio.UnsupportedAudioStreamException;
import org.openhab.core.library.types.PercentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerAudioSink} class is the HABSpeaker web ui Audio Sink implementation
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerAudioSink implements AudioSink {
    /**
     * Byte send to the sink after last chunk to indicate that streaming has ended.
     * Should try to be sent event on and error as the client should be aware that data transmission has ended.
     */
    private static byte TERMINATION_BYTE = (byte) 0;
    private final HashSet<AudioFormat> supportedFormats = new HashSet<>();
    private static final HashSet<Class<? extends AudioStream>> SUPPORTED_STREAMS = new HashSet<>();

    static {
        SUPPORTED_STREAMS.add(FixedLengthAudioStream.class);
        SUPPORTED_STREAMS.add(HABSpeakerAudioSource.HABSpeakerAudioStream.class);
    }
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerAudioSink.class);

    private final String sinkId;
    private final String sinkLabel;
    private final HABSpeakerIOConnection speakerIO;
    private final int channelNumber;
    private final long clientSampleRate;
    private final AudioFormat internalStreamFormat;

    public HABSpeakerAudioSink(String id, String label, HABSpeakerIOConnection speakerIO, int channelNumber,
            long clientSampleRate) {
        this.sinkId = id;
        this.sinkLabel = label;
        this.speakerIO = speakerIO;
        this.channelNumber = channelNumber;
        this.clientSampleRate = clientSampleRate;
        this.internalStreamFormat = new AudioFormat(AudioFormat.CONTAINER_WAVE, AudioFormat.CODEC_PCM_SIGNED, false, 16,
                null, clientSampleRate);
        supportedFormats.add(this.internalStreamFormat);
        supportedFormats.add(AudioFormat.WAV);
    }

    public AudioFormat getInternalStreamFormat() {
        return internalStreamFormat;
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
        ConvertedAudioStream convertedAudioStream = null;
        OutputStream outputStream = null;
        try {
            if (audioStream instanceof HABSpeakerAudioSource.HABSpeakerAudioStream && isDirectStreamSupported(format)) {
                // the ui expect a raw wav stream (no format header),
                // we don't know this, so we restrict the direct stream to only habspeaker audio streams
                int channels = format.getChannels() != null ? format.getChannels() : 1;
                var sampleRate = format.getFrequency();
                StreamType type = channels == 1 ? StreamType.PCM16BitMono : StreamType.PCM16BitStereo;
                outputStream = new HABSpeakerAudioOutputStream(speakerIO, type);
                var inputStream = audioStream;
                if (sampleRate != null && sampleRate != this.clientSampleRate) {
                    inputStream = new ConvertedAudioStream(audioStream, this.clientSampleRate, channels, true);
                }
                transferAudio(inputStream, outputStream, -1);
            } else {
                // we try to convert to one of the supported stream formats
                var streamType = channelNumber == 1 ? StreamType.PCM16BitMono : StreamType.PCM16BitStereo;
                outputStream = new HABSpeakerAudioOutputStream(speakerIO, streamType);
                convertedAudioStream = new ConvertedAudioStream(audioStream, this.clientSampleRate, channelNumber,
                        false);
                transferAudio(convertedAudioStream, outputStream, convertedAudioStream.getDuration());
            }
        } catch (UnsupportedAudioFileException e) {
            logger.warn("UnsupportedAudioFileException: {}", e.getMessage());
        } catch (InterruptedIOException ignored) {
        } catch (IOException e) {
            logger.warn("IOException: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("InterruptedException: {}", e.getMessage());
        } finally {
            if (convertedAudioStream != null) {
                try {
                    convertedAudioStream.close();
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
        return AudioFormat.WAV.isCompatible(format) && //
                bitDepth != null && bitDepth == 16 && //
                bigEndian != null && !bigEndian;
    }

    private void transferAudio(InputStream inputStream, OutputStream outputStream, long duration)
            throws IOException, InterruptedException {
        Instant start = Instant.now();
        try {
            inputStream.transferTo(outputStream);
        } finally {
            try {
                // send a byte indicating this stream has ended, so it can be tear down on the client
                outputStream.write(new byte[] { TERMINATION_BYTE }, 0, 1);
            } catch (IOException e) {
                logger.warn("Unable to send termination byte to sink {}", sinkId);
            }
        }
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
        return supportedFormats;
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
        private final HABSpeakerIOConnection speakerIO;
        private boolean closed = false;

        public HABSpeakerAudioOutputStream(HABSpeakerIOConnection speakerIO, StreamType streamFormat) {
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

    /**
     * Byte sent in the 5th position of each chunk that indicates sample format and channels
     */
    private enum StreamType {
        // 16bit int 1 channel little-endian
        PCM16BitMono((byte) 1),
        // 16bit int 2 channel little-endian
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
