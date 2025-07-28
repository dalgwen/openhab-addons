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
package org.openhab.binding.wyoming.internal;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import javax.sound.sampled.UnsupportedAudioFileException;

import net.iot.wyoming.WyomingProtocolException;
import net.iot.wyoming.sound.SoundManager;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioSinkAsync;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.core.library.types.PercentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an AudioSink for a Wyoming compliant sound device
 * 
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class WyomingAudioSink extends AudioSinkAsync {

    private final WyomingHandler wyomingHandler;
    private final SoundManager soundManager;

    private static final Set<AudioFormat> SUPPORTED_FORMATS = Set.of(AudioFormat.PCM_SIGNED, AudioFormat.WAV,
            AudioFormat.MP3);
    private static final Set<Class<? extends AudioStream>> SUPPORTED_STREAMS = Set.of(AudioStream.class);
    private final Logger logger = LoggerFactory.getLogger(WyomingAudioSink.class);

    private final ScheduledExecutorService scheduler;

    public WyomingAudioSink(WyomingHandler wyomingHandler, SoundManager soundManager,
            ScheduledExecutorService scheduler) {
        this.wyomingHandler = wyomingHandler;
        this.soundManager = soundManager;
        this.scheduler = scheduler;
    }

    @Override
    public String getId() {
        return wyomingHandler.getThing().getUID().toString();
    }

    @Override
    @Nullable
    public String getLabel(@Nullable Locale locale) {
        var label = wyomingHandler.getThing().getLabel();
        return label != null ? label : wyomingHandler.getThing().getUID().getId();
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
        // NOT IMPLEMENTED
        return new PercentType(100);
    }

    @Override
    public void setVolume(PercentType volume) throws IOException {
        // NOT IMPLEMENTED
    }

    @Override
    protected void processAsynchronously(@Nullable AudioStream audioStream) throws UnsupportedAudioFormatException {
        if (audioStream == null) {
            soundManager.stopSound();
            return;
        }

        try {
            ConvertedInputStream convertedInputStream = new ConvertedInputStream(audioStream);
            AudioFormat ohAudioFormat = audioStream.getFormat();
            Integer bitDepth = ohAudioFormat.getBitDepth();
            Long frequency = ohAudioFormat.getFrequency();
            Integer channels = ohAudioFormat.getChannels();
            if (bitDepth == null || frequency == null || channels == null) {
                logger.warn("Insufficient audio information. Will try default. You should use well defined audio file");
            }
            int bitDepthFinal = bitDepth != null ? bitDepth : 16;
            long frequencyFinal = frequency != null ? frequency : 16000;
            int channelsFinal = channels != null ? channels : 1;

            scheduler.execute(() -> {
                try {
                    soundManager.playSound(convertedInputStream, bitDepthFinal, frequencyFinal, channelsFinal);
                } catch (WyomingProtocolException e) {
                    logger.error("Error playing sound", e);
                } finally {
                    // todo compute time ?
                    playbackFinished(audioStream);
                }
            });
        } catch (IOException | UnsupportedAudioFileException e) {
            throw new UnsupportedAudioFormatException("Cannot handle format", audioStream.getFormat(), e);
        }
    }
}
