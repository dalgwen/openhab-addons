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
package org.openhab.binding.wyoming.internal.protocol;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.message.data.InfoData;
import org.openhab.binding.wyoming.internal.protocol.sound.SoundManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * 
 * @author Gwendal Roulleau - Initial contribution
 */
public class WyomingJavaTest {

    private static final Logger logger = LoggerFactory.getLogger(WyomingJavaTest.class);

    private static WyomingClient connection;

    public static void main(String[] args) throws IOException, WyomingProtocolException, UnsupportedAudioFileException {

//        connection = new WyomingClient("loisirs-pi.lan", 10700);
        connection = new WyomingClient("localhost", 10600);
        connection.connectAndListen(true);

        InfoData infoData = connection.getInfoData();
        logger.info("Connected to server");
        logger.info("Server info: " + infoData);

        // testRecordSound();
        testSound();
        // testWakeWord();

        // Thread.sleep(100000);

        connection.disconnectAndStop();
    }

    private static void testWakeWord() throws IOException, WyomingProtocolException {

        SoundManager soundManager = new SoundManager(connection);
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                connection.sendMessage(MessageType.PauseSatellite);
                soundManager.endAllOutputStreams();
            } catch (InterruptedException | IOException e) {
                logger.error("Error occurred while sending the message", e);
            }
        }).start();

        // Write the audio input stream directly to a file in WAV format
        connection.sendMessage(MessageType.RunPipeline);
        AudioInputStream audioInputStream = soundManager.getAudioInputStream();
        File outputFile = new File("output.wav");
        try {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            logger.info("Audio successfully written to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write audio to file", e);
        }
    }

    private static void testRecordSound() throws IOException, WyomingProtocolException {

        SoundManager soundManager = new SoundManager(connection);
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                connection.sendMessage(MessageType.PauseSatellite);
                soundManager.endAllOutputStreams();
            } catch (InterruptedException | IOException e) {
                logger.error("Error occurred while sending the message", e);
            }
        }).start();

        // Write the audio input stream directly to a file in WAV format
        AudioInputStream audioInputStream = soundManager.getAudioInputStream();
        File outputFile = new File("output.wav");
        try {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            logger.info("Audio successfully written to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write audio to file", e);
        }
    }

    private static void testDescribe() throws IOException {
        connection.register(Set.of(MessageType.Info),
                (message) -> logger.debug("Received a message: " + message.getHeader().getType()));
        connection.sendMessage(MessageType.Describe);
    }

    public static void testSound() throws IOException, WyomingProtocolException, UnsupportedAudioFileException {
        SoundManager soundManager = new SoundManager(connection);
        connection.registerAll((message) -> logger.debug("Received a message: " + message.getHeader().getType()));
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File("d:/temp/coucou.wav"))) {
            AudioFormat format = audioInputStream.getFormat();
            soundManager.playSound(audioInputStream, format.getSampleSizeInBits(), format.getSampleRate(),
                    format.getChannels());
        }
    }
}
