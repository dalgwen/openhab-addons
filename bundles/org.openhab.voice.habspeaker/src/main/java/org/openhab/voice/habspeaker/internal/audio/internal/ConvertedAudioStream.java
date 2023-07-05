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
package org.openhab.voice.habspeaker.internal.audio.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.FixedLengthAudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tritonus.share.sampled.file.TAudioFileFormat;

/**
 * This class convert a stream to the normalized pcm
 * format wanted (ported from pulseaudio binding)
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class ConvertedAudioStream extends AudioStream {

    private final Logger logger = LoggerFactory.getLogger(ConvertedAudioStream.class);

    private final AudioFormat inAudioFormat;
    private final javax.sound.sampled.AudioFormat inJAudioFormat;
    private final AudioFormat outAudioFormat;
    private final javax.sound.sampled.AudioFormat outJAudioFormat;
    private final AudioInputStream pcmNormalizedInputStream;

    private long duration = -1;
    private long length = -1;
    private final boolean rawAudioInput;

    public ConvertedAudioStream(AudioStream innerInputStream, long targetSampleRate, int targetChannels,
            boolean rawAudioInput) throws UnsupportedAudioFormatException, UnsupportedAudioFileException, IOException {
        this(innerInputStream, innerInputStream.getFormat(), targetSampleRate, targetChannels, rawAudioInput);
    }

    public ConvertedAudioStream(InputStream innerInputStream, AudioFormat audioFormat, long targetSampleRate,
            int targetChannels, boolean rawAudioInput)
            throws UnsupportedAudioFormatException, UnsupportedAudioFileException, IOException {
        this.inAudioFormat = audioFormat;
        int inputBitDepth = Objects.requireNonNull(inAudioFormat.getBitDepth());
        float inputSampleRate = Objects.requireNonNull(inAudioFormat.getFrequency());
        int inputChannels = Objects.requireNonNull(inAudioFormat.getChannels());
        boolean inputIsBigEndian = inAudioFormat.isBigEndian() != null
                && Objects.requireNonNull(inAudioFormat.isBigEndian());
        this.inJAudioFormat = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                inputSampleRate, inputBitDepth, inputChannels, inputChannels * (inputBitDepth / 8), inputSampleRate,
                inputIsBigEndian);
        this.outAudioFormat = new AudioFormat(this.inAudioFormat.getContainer(), this.inAudioFormat.getCodec(),
                inputIsBigEndian, inputBitDepth, null, targetSampleRate, targetChannels);
        this.outJAudioFormat = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                targetSampleRate, inputBitDepth, targetChannels, targetChannels * (inputBitDepth / 8), targetSampleRate,
                inputIsBigEndian);
        if (innerInputStream instanceof FixedLengthAudioStream) {
            length = ((FixedLengthAudioStream) innerInputStream).length();
        }
        this.rawAudioInput = rawAudioInput;
        pcmNormalizedInputStream = getPCMStreamNormalized(getPCMStream(innerInputStream));
    }

    @Override
    public int read(byte @Nullable [] b) throws IOException {
        return pcmNormalizedInputStream.read(b);
    }

    @Override
    public int read(byte @Nullable [] b, int off, int len) throws IOException {
        return pcmNormalizedInputStream.read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return pcmNormalizedInputStream.readAllBytes();
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        return pcmNormalizedInputStream.readNBytes(len);
    }

    @Override
    public int readNBytes(byte @Nullable [] b, int off, int len) throws IOException {
        return pcmNormalizedInputStream.readNBytes(b, off, len);
    }

    @Override
    public int read() throws IOException {
        return pcmNormalizedInputStream.read();
    }

    @Override
    public void close() throws IOException {
        pcmNormalizedInputStream.close();
    }

    /**
     * Ensure right PCM format by converting if needed (sample rate, channel)
     *
     * @param pcmInputStream PCM java system audio stream
     * @return A PCM normalized stream at the desired format
     */
    private AudioInputStream getPCMStreamNormalized(AudioInputStream pcmInputStream) {
        javax.sound.sampled.AudioFormat format = pcmInputStream.getFormat();
        if (format.getChannels() != outJAudioFormat.getChannels()
                || !format.getEncoding().equals(outJAudioFormat.getEncoding())
                || format.getSampleSizeInBits() != outJAudioFormat.getSampleSizeInBits()
                || Math.abs(format.getFrameRate() - outJAudioFormat.getFrameRate()) > 1000) {
            logger.debug("Sound is not in the target format. Trying to re-encode it");
            return AudioSystem.getAudioInputStream(outJAudioFormat, pcmInputStream);
        } else {
            return pcmInputStream;
        }
    }

    public long getDuration() {
        return duration;
    }

    /**
     * If necessary, this method convert MP3 to PCM, and try to
     * extract duration information.
     *
     * @param innerInputStream An audio stream
     * @return PCM stream
     */
    private AudioInputStream getPCMStream(InputStream innerInputStream)
            throws UnsupportedAudioFileException, IOException, UnsupportedAudioFormatException {
        if (this.rawAudioInput) {
            if (!AudioFormat.WAV.isCompatible(inAudioFormat)) {
                throw new UnsupportedAudioFormatException("Unsupported raw streaming", inAudioFormat);
            }
            AudioInputStream audioInputStream = new AudioInputStream(innerInputStream, this.inJAudioFormat, length);
            if (length > 0) {
                float durationInSeconds = getAudioDurationInSeconds(audioInputStream);
                duration = Math.round(durationInSeconds * 1000);
                logger.debug("Duration of input stream : {}ms", duration);
            }
            return audioInputStream;
        }
        // A stream supporting reset operation (reset is mandatory to parse formation without loosing data)
        InputStream resettableInnerInputStream = new BufferedInputStream(innerInputStream);
        if (AudioFormat.MP3.isCompatible(inAudioFormat)) {
            MpegAudioFileReader mpegAudioFileReader = new MpegAudioFileReader();
            if (length > 0) { // compute duration if possible
                AudioFileFormat audioFileFormat = mpegAudioFileReader.getAudioFileFormat(resettableInnerInputStream);
                if (audioFileFormat instanceof TAudioFileFormat) {
                    Map<String, Object> taudioFileFormatProperties = audioFileFormat.properties();
                    if (taudioFileFormatProperties.containsKey("mp3.framesize.bytes")
                            && taudioFileFormatProperties.containsKey("mp3.framerate.fps")) {
                        Integer frameSize = (Integer) taudioFileFormatProperties.get("mp3.framesize.bytes");
                        Float frameRate = (Float) taudioFileFormatProperties.get("mp3.framerate.fps");
                        if (frameSize != null && frameRate != null) {
                            duration = Math.round((length / (frameSize * frameRate)) * 1000);
                            logger.debug("Duration of input stream : {}", duration);
                        }
                    }
                }
                resettableInnerInputStream.reset();
            }

            logger.debug("Sound is a MP3. Trying to reencode it");
            AudioInputStream sourceAIS = mpegAudioFileReader.getAudioInputStream(resettableInnerInputStream);
            javax.sound.sampled.AudioFormat sourceFormat = sourceAIS.getFormat();
            MpegFormatConversionProvider mpegconverter = new MpegFormatConversionProvider();
            javax.sound.sampled.AudioFormat convertFormat = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16,
                    sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);
            return mpegconverter.getAudioInputStream(convertFormat, sourceAIS);
        } else if (AudioFormat.WAV.isCompatible(inAudioFormat)) {
            // return the same input stream, but try to compute the duration first
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(resettableInnerInputStream);
            if (length > 0) {
                float durationInSeconds = getAudioDurationInSeconds(audioInputStream);
                duration = Math.round(durationInSeconds * 1000);
                logger.debug("Duration of input stream : {}ms", duration);
            }
            return audioInputStream;
        } else {
            throw new UnsupportedAudioFormatException("HABSpeaker audio sink can only play pcm streams", inAudioFormat);
        }
    }

    private float getAudioDurationInSeconds(AudioInputStream audioInputStream) {
        int frameSize = audioInputStream.getFormat().getFrameSize();
        float frameRate = audioInputStream.getFormat().getFrameRate();
        return length / (frameSize * frameRate);
    }

    @Override
    public AudioFormat getFormat() {
        return this.outAudioFormat;
    }
}
