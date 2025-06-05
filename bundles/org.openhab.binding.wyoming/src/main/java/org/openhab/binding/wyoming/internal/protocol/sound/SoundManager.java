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
package org.openhab.binding.wyoming.internal.protocol.sound;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.WyomingClient;
import org.openhab.binding.wyoming.internal.protocol.WyomingProtocolException;
import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingRawMessage;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioChunkData;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioStartData;
import org.openhab.binding.wyoming.internal.protocol.message.data.InfoData;
import org.openhab.binding.wyoming.internal.protocol.message.data.RunPipelineData;
import org.openhab.core.audio.utils.AudioWaveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sound manager is responsible for playing sound to and getting sound from the remote
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class SoundManager {

    public static final int TIMEOUT = 1000;
    Logger logger = LoggerFactory.getLogger(SoundManager.class);

    public static final int PIPE_SIZE = 500 * 1024;

    WyomingClient client;
    @Nullable
    AudioFormat audioFormat;
    CountDownLatch audioFormatReadyLatch = new CountDownLatch(1);
    boolean isListening = false;
    boolean isSatellite = false;
    boolean hasMicrophone = false;
    boolean hasSpeaker = false;

    private final Map<InputStream, Boolean> currentlyPlayingSounds = new ConcurrentHashMap<>();

    private final Set<PipedOutputStream> audioOutputStreams = new HashSet<>();
    private static final Set<MessageType> audioMessageTypes = Set.of(MessageType.AudioStart, MessageType.AudioChunk,
            MessageType.AudioStop);

    public SoundManager(WyomingClient client) throws WyomingProtocolException {
        this.client = client;
        InfoData infoData = client.getInfoData();
        if (infoData.getSatellite() != null) {
            isSatellite = true;
            hasMicrophone = true;
            hasSpeaker = true;
        }
        if (infoData.getMic() != null) {
            hasMicrophone = true;
        }
        if (infoData.getSnd() != null) {
            hasSpeaker = true;
        }

        client.register(audioMessageTypes, this::receiveAudioMessage);
    }

    private void receiveAudioMessage(WyomingRawMessage rawMessage) {
        if (rawMessage.getHeader().getType() == MessageType.AudioChunk) {
            AudioChunkData audioChunkData = rawMessage.decodeData(AudioChunkData.class);

            // filling audioFormat
            if (audioFormat == null) {
                audioFormat = buildAudioFormat(audioChunkData);
                audioFormatReadyLatch.countDown();
            }

            // sending data to all input streams
            byte[] payload = rawMessage.getPayload();
            if (payload == null || payload.length == 0) {
                logger.debug("Received empty payload");
                return;
            }
            Iterator<PipedOutputStream> iterator = audioOutputStreams.iterator();
            while (iterator.hasNext()) {
                PipedOutputStream pipedOutputStream = iterator.next();
                try {
                    logger.debug("Sending {} bytes to input stream {}", payload.length, pipedOutputStream);
                    pipedOutputStream.write(payload);
                    pipedOutputStream.flush();
                } catch (IOException e) {
                    logger.debug("Input stream {} closed or full", pipedOutputStream);
                    iterator.remove();
                    endOutputStream(pipedOutputStream);
                }
            }
        } else if (rawMessage.getHeader().getType() == MessageType.AudioStop) {
            endAllOutputStreams();
        }
    }

    public synchronized void endAllOutputStreams() {
        logger.debug("Closing all output stream");
        audioOutputStreams.forEach(this::endOutputStream);
        audioOutputStreams.clear();
        try {
            client.sendMessage(MessageType.AudioStop);
        } catch (IOException e) {
            logger.error("Cannot send stop message to server");
        }
    }

    private synchronized void endOutputStream(PipedOutputStream audioStream) {
        try {
            audioStream.close();
            if (audioOutputStreams.isEmpty()) {
                logger.debug("No more output stream, stop streaming");
                client.sendMessage(MessageType.AudioStop);
            }
        } catch (IOException ignored) {
        }
    }

    private AudioFormat buildAudioFormat(AudioChunkData audioChunkData) {
        int sampleRate = audioChunkData.getRate();
        int sampleSizeInBits = audioChunkData.getWidth() * 8; // Convert bytes to bits
        int channels = audioChunkData.getChannels();
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, true, false);
    }

    private AudioFormat getAudioFormat() throws WyomingProtocolException {
        AudioFormat audioFormatLocal = audioFormat;
        if (audioFormatLocal != null) {
            return audioFormatLocal;
        } else {
            try {
                boolean isAudioFormatReady = audioFormatReadyLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
                if (isAudioFormatReady) {
                    audioFormatLocal = audioFormat;
                    if (audioFormatLocal == null) {
                        throw new WyomingProtocolException(
                                "Should not happened, latch ensure that audio format was received");
                    } else {
                        return audioFormatLocal;
                    }
                } else {
                    throw new WyomingProtocolException("Audio format not received within " + TIMEOUT
                            + "ms. Maybe call getAudioInputStream() before ?");
                }
            } catch (InterruptedException e) {
                throw new WyomingProtocolException("Interrupted while waiting for audio format");
            }
        }
    }

    public AudioInputStream getAudioInputStream() throws IOException, WyomingProtocolException {
        startAudioStreaming();

        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream, PIPE_SIZE);
        audioOutputStreams.add(pipedOutputStream);

        return new AudioInputStream(pipedInputStream, getAudioFormat(), AudioSystem.NOT_SPECIFIED);
    }

    private void startAudioStreaming() throws IOException {
        if (isSatellite) {
            client.sendMessage(new WyomingRawMessage(MessageType.RunSatellite));
            client.sendMessage(MessageType.RunPipeline, new RunPipelineData("wake", "tts"));
        } else {
            throw new IllegalStateException("Not implemented yet");
            // TODO
        }
    }

    public void playSound(InputStream inputStream, int sampleSizeInBits, float sampleRate, int channels)
            throws WyomingProtocolException {
        final int CHUNK_SIZE = 20 * 1024; // 20 KB

        try {
            // Add this sound to the currently playing map
            currentlyPlayingSounds.put(inputStream, true);

            AudioWaveUtils.removeFMT(inputStream);
            logger.debug("Playing sound with format {}, {}, {}", sampleSizeInBits, sampleRate, channels);

            int width = sampleSizeInBits / 8;
            client.sendMessage(MessageType.AudioStart, new AudioStartData((int) sampleRate, width, channels));

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1
                    && Boolean.TRUE.equals(currentlyPlayingSounds.get(inputStream))) {
                byte[] chunk = (bytesRead == CHUNK_SIZE) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                AudioChunkData audioChunkData = new AudioChunkData((int) sampleRate, width, channels);
                client.sendMessage(MessageType.AudioChunk, audioChunkData, chunk);
            }
            client.sendMessage(MessageType.AudioStop);
        } catch (IOException e) {
            throw new WyomingProtocolException("Cannot play sound", e);
        } finally {
            // Remove this sound from the currently playing map
            currentlyPlayingSounds.remove(inputStream);
        }
    }

    public void stopSound() {
        // Flip the flag for all currently playing sounds to false
        // This will cause the playSound loop to exit
        logger.debug("Stopping all playing sounds");
        currentlyPlayingSounds.replaceAll((stream, playing) -> false);
    }
}
