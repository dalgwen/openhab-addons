package org.openhab.binding.wyoming.internal.protocol.sound;

import org.openhab.binding.wyoming.internal.protocol.WyomingClient;
import org.openhab.binding.wyoming.internal.protocol.WyomingProtocolException;
import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.message.WyomingRawMessage;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioChunkData;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioStartData;
import org.openhab.core.audio.utils.AudioWaveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SoundManager {

    Logger logger = LoggerFactory.getLogger(SoundManager.class);
    
    public static final int PIPE_SIZE = 500 * 1024;

    WyomingClient client;
    AudioFormat audioFormat;
    CountDownLatch audioFormatReadyLatch;
    Boolean isListening = false;

    private final Set<PipedOutputStream> audioOutputStreams = new HashSet<>();
    private static final Set<MessageType> audioMessageTypes = Set.of(MessageType.AudioStart, MessageType.AudioChunk, MessageType.AudioStop);

    public SoundManager(WyomingClient client) {
        this.client = client;
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

            // sending data to all input stream
            byte[] payload = rawMessage.getPayload();
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
        }
        else if (rawMessage.getHeader().getType() == MessageType.AudioStop) {
            audioOutputStreams.forEach(this::endOutputStream);
            audioOutputStreams.clear();
        }
    }

    private void endOutputStream(PipedOutputStream audioStream) {
        try {
            audioStream.close();
        } catch (IOException ignored) {}
    }

    private AudioFormat buildAudioFormat(AudioChunkData audioChunkData) {
        int sampleRate = audioChunkData.getRate();
        int sampleSizeInBits = audioChunkData.getWidth() * 8; // Convert bytes to bits
        int channels = audioChunkData.getChannels();
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, true, false);
    }

    private AudioFormat getAudioFormat() throws WyomingProtocolException {
        if (audioFormat != null) {
            return audioFormat;
        } else {
            try {
                if (audioFormatReadyLatch == null) {
                    throw new WyomingProtocolException("You cannot call getAudioFormat() before calling getAudioInputStream() at least once");
                }
                boolean isAudioFormatReady = audioFormatReadyLatch.await(1000, TimeUnit.MILLISECONDS);
                if (isAudioFormatReady) {
                    return audioFormat;
                } else {
                    throw new WyomingProtocolException("Audio format not received within 200ms");
                }
            } catch (InterruptedException e) {
                throw new WyomingProtocolException("Interrupted while waiting for audio format");
            }
        }
    }

    public AudioInputStream getAudioInputStream() throws IOException, WyomingProtocolException {
        if (audioFormatReadyLatch == null) { // first call, must prepare audio format detection
            audioFormatReadyLatch = new CountDownLatch(1);
        }
        startAudioStreaming();

        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream, PIPE_SIZE);
        audioOutputStreams.add(pipedOutputStream);

        return new AudioInputStream(pipedInputStream, getAudioFormat(), 500000);
    }

    private void startAudioStreaming() throws IOException {
        client.sendMessage(new WyomingRawMessage(MessageType.RunSatellite));
    }

    public void playSound(InputStream inputStream) throws WyomingProtocolException {
        final int CHUNK_SIZE = 20 * 1024; // 20 KB

        try {
            AudioWaveUtils.removeFMT(inputStream);
            client.sendMessage(MessageType.AudioStart, new AudioStartData(16000, 2, 1));

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] chunk = (bytesRead == CHUNK_SIZE) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                AudioChunkData audioChunkData = new AudioChunkData(16000, 2, 1);
                client.sendMessage(MessageType.AudioChunk, audioChunkData, chunk);
            }

            client.sendMessage(MessageType.AudioStop);
        } catch (IOException e) {
            throw new WyomingProtocolException("Cannot play sound", e);
        }
    }
}
