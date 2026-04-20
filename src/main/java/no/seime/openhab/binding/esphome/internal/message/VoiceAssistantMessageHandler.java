/**
 * Copyright (c) 2023 Contributors to the Seime Openhab Addons project
 * Copyright (c) 2025 Contributors to the ESPHome binding extension
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.esphome.internal.message;

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.InvalidProtocolBufferException;

import io.esphome.api.VoiceAssistantAudio;
import no.seime.openhab.binding.esphome.internal.handler.ESPHomeAudioSource;
import no.seime.openhab.binding.esphome.internal.handler.ESPHomeHandler;

/**
 * Message handler for {@link ESPHomeProtocol.VoiceAssistantAudio} messages received from ESP32 devices
 * with voice assistant capability. These messages contain raw PCM audio data from the device's
 * microphone.
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class VoiceAssistantMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(VoiceAssistantMessageHandler.class);

    private final ESPHomeHandler handler;
    private @Nullable ESPHomeAudioSource audioSource;

    public VoiceAssistantMessageHandler(ESPHomeHandler handler) {
        this.handler = handler;
    }

    public void setAudioSource(@Nullable ESPHomeAudioSource audioSource) {
        this.audioSource = audioSource;
    }

    /**
     * Handles incoming VoiceAssistantAudio messages which contain raw PCM audio data.
     *
     * @param message the raw protobuf message
     */
    public void handleVoiceAssistantAudio(VoiceAssistantAudio message) {
        try {
            byte[] audioData = message.getData().toByteArray();
            if (audioData.length == 0) {
                logger.trace("[{}] Received empty audio data", handler.getThing().getUID());
                return;
            }

            ESPHomeAudioSource source = this.audioSource;
            if (source == null) {
                logger.debug("[{}] No audio source registered, discarding {} bytes", handler.getThing().getUID(),
                        audioData.length);
                return;
            }

            source.writeAudioData(audioData, audioData.length);

            if (message.getEnd()) {
                logger.debug("[{}] Voice assistant stream ended", handler.getThing().getUID());
                source.close();
            }
        } catch (InvalidProtocolBufferException e) {
            logger.warn("[{}] Failed to parse VoiceAssistantAudio message", handler.getThing().getUID(), e);
        } catch (IOException e) {
            logger.warn("[{}] Failed to write audio data to pipe", handler.getThing().getUID(), e);
        }
    }
}
