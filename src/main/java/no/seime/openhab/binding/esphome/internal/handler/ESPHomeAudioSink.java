/**
 * Copyright (c) 2023 Contributors to the Seime Openhab Addons project
 * Copyright (c) 2025 Contributors to the ESPHome binding extension
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.esphome.internal.handler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioHTTPServer;
import org.openhab.core.audio.AudioSinkAsync;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.audio.StreamServed;
import org.openhab.core.audio.URLAudioStream;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.core.audio.UnsupportedAudioStreamException;
import org.openhab.core.library.types.PercentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.esphome.api.MediaPlayerCommand;
import io.esphome.api.MediaPlayerCommandRequest;

/**
 * The {@link ESPHomeAudioSink} implements the AudioSink interface for ESPHome devices
 * that support media player / speaker output.
 *
 * @author Gwendal Le Guillou - Initial contribution
 */
@NonNullByDefault
public class ESPHomeAudioSink extends AudioSinkAsync {

    private final Logger logger = LoggerFactory.getLogger(ESPHomeAudioSink.class);

    // Supported audio formats: PCM WAV (primary for ESP32), MP3
    private static final Set<AudioFormat> SUPPORTED_FORMATS = Set.of(AudioFormat.WAV, AudioFormat.MP3);

    private static final Set<Class<? extends AudioStream>> SUPPORTED_STREAMS = Set.of(AudioStream.class);

    private final ESPHomeHandler handler;
    private final @Nullable AudioHTTPServer audioHTTPServer;
    private final @Nullable String callbackUrl;

    public ESPHomeAudioSink(ESPHomeHandler handler, @Nullable AudioHTTPServer audioHTTPServer,
            @Nullable String callbackUrl) {
        this.handler = handler;
        this.audioHTTPServer = audioHTTPServer;
        this.callbackUrl = callbackUrl;
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
    public String getId() {
        return handler.getThing().getUID().toString() + ":audio";
    }

    @Override
    public @Nullable String getLabel(@Nullable Locale locale) {
        return handler.getThing().getLabel() + " (AudioSink)";
    }

    @Override
    protected void processAsynchronously(@Nullable AudioStream audioStream)
            throws UnsupportedAudioFormatException, UnsupportedAudioStreamException {
        if (audioStream == null) {
            // Stop playback
            sendMediaCommand(null, null, false);
            return;
        }

        String url;
        if (audioStream instanceof URLAudioStream urlAudioStream) {
            // External URL - ESPHome can fetch it directly
            url = urlAudioStream.getURL();
            tryClose(audioStream);
            logger.debug("Playing external URL {} on ESPHome device {}", url, handler.getThing().getUID());
        } else if (callbackUrl != null) {
            // Serve via openHAB's HTTP server
            StreamServed streamServed;
            try {
                streamServed = audioHTTPServer.serve(audioStream, 10, true);
            } catch (IOException e) {
                tryClose(audioStream);
                throw new UnsupportedAudioStreamException(
                        "ESPHome was not able to handle the audio stream (cache on disk failed).",
                        audioStream.getClass(), e);
            }
            url = callbackUrl + streamServed.url();
            streamServed.playEnd().thenRun(() -> this.playbackFinished(audioStream));
            logger.debug("Serving audio stream from {} on ESPHome device {}", url, handler.getThing().getUID());
        } else {
            logger.warn("No callback URL configured and audio stream is not a URL audio stream!");
            tryClose(audioStream);
            return;
        }

        // Send media URL to ESPHome device
        sendMediaCommand(url, null, false);
    }

    /**
     * Send a media command to the ESPHome device
     *
     * @param mediaUrl the URL to play, or null to stop
     * @param volume the volume level (0-1), or null to keep current
     * @param announcement true if this is an announcement
     */
    private void sendMediaCommand(@Nullable String mediaUrl, @Nullable Float volume, boolean announcement) {
        try {
            MediaPlayerCommandRequest.Builder builder = MediaPlayerCommandRequest.newBuilder();

            if (mediaUrl != null) {
                builder.setHasMediaUrl(true);
                builder.setMediaUrl(mediaUrl);
                builder.setHasCommand(true);
                builder.setCommand(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PLAY);
            } else {
                // Stop command
                builder.setHasCommand(true);
                builder.setCommand(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_STOP);
            }

            if (volume != null) {
                builder.setHasVolume(true);
                builder.setVolume(volume);
            }

            if (announcement) {
                builder.setHasAnnouncement(true);
                builder.setAnnouncement(true);
            }

            handler.sendMessage(builder.build());
            logger.debug("Sent media command to ESPHome device {}: url={}, volume={}", handler.getThing().getUID(),
                    mediaUrl, volume);
        } catch (Exception e) {
            logger.warn("Failed to send media command to ESPHome device {}: {}", handler.getThing().getUID(),
                    e.getMessage());
        }
    }

    private void tryClose(@Nullable InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public PercentType getVolume() throws IOException {
        // Volume state is managed by the ESPHome device via MediaPlayerStateResponse
        // The binding does not currently store volume locally
        throw new IOException("Volume not available - ESPHome device volume must be queried via state");
    }

    @Override
    public void setVolume(PercentType volume) throws IOException {
        if (volume == null) {
            return;
        }
        try {
            MediaPlayerCommandRequest.Builder builder = MediaPlayerCommandRequest.newBuilder();
            builder.setHasVolume(true);
            builder.setVolume(volume.floatValue() / 100.0f); // Convert 0-100 to 0-1

            handler.sendMessage(builder.build());
            logger.debug("Sent volume command to ESPHome device {}: volume={}", handler.getThing().getUID(), volume);
        } catch (Exception e) {
            throw new IOException("Failed to set volume on ESPHome device: " + e.getMessage(), e);
        }
    }
}
