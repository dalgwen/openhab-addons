/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package org.openhab.binding.wyoming.internal.protocol;

import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.sound.SoundManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public class WyomingJavaTest {

    private static final Logger logger = LoggerFactory.getLogger(WyomingJavaTest.class);

    private static WyomingClient connection;

    public static void main(String[] args) throws IOException, InterruptedException, WyomingProtocolException {


        connection = new WyomingClient("localhost", 10700);
        connection.connectAndListen();

        testRecordSound();
//        testSound();

        Thread.sleep(10000);

        connection.disconnectAndStop();
    }

    private static void testRecordSound() throws IOException, WyomingProtocolException {

        SoundManager soundManager = new SoundManager(connection);
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                connection.sendMessage(MessageType.PauseSatellite);
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
        connection.register(Set.of(MessageType.Info), (message) -> {
            logger.debug("Received a message: " + message.getHeader().getType());
        });
        connection.sendMessage(MessageType.Describe);

    }

    public static void testSound() throws IOException, WyomingProtocolException {
        SoundManager soundManager = new SoundManager(connection);
        connection.registerAll((message) -> {
            logger.debug("Received a message: " + message.getHeader().getType());
        });
    
        try (InputStream inputStream = new FileInputStream("d:/temp/coucou.wav")){            ;
            soundManager.playSound(inputStream);
        }
    }
}