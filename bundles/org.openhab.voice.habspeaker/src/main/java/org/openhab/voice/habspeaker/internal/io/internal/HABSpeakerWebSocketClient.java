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
package org.openhab.voice.habspeaker.internal.io.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.UnsupportedAudioFormatException;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSource;
import org.openhab.voice.habspeaker.internal.audio.internal.ConvertedAudioStream;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerWebSocketClient} class controls an individual WebSocket client connection
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebSocketClient extends HABSpeakerIOClientBase implements WebSocketListener {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketClient.class);
    private static final TypeReference<HashMap<String, Object>> WEBSOCKET_MAPPER_TYPE_REF = new TypeReference<>() {
    };
    private final ConcurrentLinkedQueue<HABSpeakerAudioSource.HABSpeakerAudioStream> sourceStreams = new ConcurrentLinkedQueue<>();

    private String id = "";
    private volatile @Nullable Session session;
    private @Nullable RemoteEndpoint remote;
    private final HABSpeakerWebSocketManager wsAdapter;
    private final ScheduledExecutorService executor;
    private @Nullable ScheduledFuture<?> scheduledDisconnection;
    private int sinkVolume;
    private int sourceVolume;
    private int mediaVolume;
    private long clientSampleRate;
    private @Nullable MediaState mediaState;
    private @Nullable PipedOutputStream sourceAudioPipedOutput;
    private @Nullable PipedInputStream sourceAudioPipedInput;
    private @Nullable InputStream sourceAudioStream;

    public HABSpeakerWebSocketClient(HABSpeakerWebSocketManager wsAdapter, HABSpeakerConfigProvider configProvider,
            ScheduledExecutorService executor) {
        super(wsAdapter.audioManager, wsAdapter.voiceManager, wsAdapter.bundleContext, configProvider, wsAdapter);
        this.wsAdapter = wsAdapter;
        this.executor = executor;
    }

    public void handleCommand(WebsocketInputCommand command, Map<String, Object> data) {
        logger.debug("Handling command {}", command);
        var thingHandler = getThingHandler();
        try {
            switch (command) {
                case INITIALIZE:
                    clientSampleRate = (int) data.get("sampleRate");
                    var scheduledDisconnection = this.scheduledDisconnection;
                    if (scheduledDisconnection != null) {
                        scheduledDisconnection.cancel(true);
                    }
                    id = (String) data.getOrDefault("id", "");
                    wsAdapter.addHandler(this);
                    sendClientCommand(HABSpeakerWebSocketClient.WebsocketOutputCommand.CONFIGURE,
                            getSpeakerConfigMessage(getThingHandler()));
                    break;
                case CONFIGURED:
                    registerSpeakerComponents(id, clientSampleRate, thingHandler);
                    sendClientCommand(HABSpeakerWebSocketClient.WebsocketOutputCommand.INITIALIZED);
                    break;
                case ON_SPOT:
                    onRemoteSpot();
                    break;
                case SINK_VOLUME:
                    try {
                        sinkVolume = Integer.parseInt(data.getOrDefault("value", "0").toString());
                        if (thingHandler != null) {
                            thingHandler.onSinkVolumeUpdate(sinkVolume);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Unable to parse sink volume");
                    }
                    break;
                case SOURCE_VOLUME:
                    try {
                        sourceVolume = Integer.parseInt(data.getOrDefault("value", "0").toString());
                        if (thingHandler != null) {
                            thingHandler.onSourceVolumeUpdate(sinkVolume);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Unable to parse source volume");
                    }
                    break;
                case MEDIA_STATE:
                    try {
                        var currentSecond = Long.parseLong(data.getOrDefault("currentSecond", "0").toString());
                        var totalSeconds = Long.parseLong(data.getOrDefault("totalSeconds", "0").toString());
                        var playbackState = PlaybackStates
                                .valueOf(data.getOrDefault("state", "stopped").toString().toUpperCase());
                        var volume = Integer.parseInt(data.getOrDefault("volume", "0").toString());
                        var provider = data.getOrDefault("provider", "").toString();
                        var mediaId = data.getOrDefault("id", "").toString();
                        mediaVolume = volume;
                        var mediaState = new MediaState(MediaProvider.fromString(provider), mediaId, currentSecond,
                                totalSeconds, playbackState);
                        this.mediaState = mediaState;
                        if (thingHandler != null) {
                            thingHandler.onMediaStateUpdate(mediaState, volume);
                        }
                    } catch (NumberFormatException | IllegalStateException e) {
                        logger.warn("Unable to parse media state: {}", e.getMessage());
                    }
                    break;
                default:
                    logger.warn("Unhandled JSON command: {}", data);
            }
        } catch (IOException | IllegalStateException e) {
            logger.warn("Disconnecting client: {}", e.getMessage());
            disconnect();
        }
    }

    public void sendAudio(byte[] id, byte[] b) {
        try {
            var remote = getRemote();
            if (remote != null) {
                // concat stream identifier and send
                ByteBuffer buff = ByteBuffer.wrap(new byte[id.length + b.length]);
                buff.put(id);
                buff.put(b);
                remote.sendBytesByFuture(ByteBuffer.wrap(buff.array()));
            }
        } catch (IllegalStateException ignored) {
            logger.warn("Unable to send audio buffer");
        }
    }

    public void addSourceListener(HABSpeakerAudioSource.HABSpeakerAudioStream output) {
        logger.debug("Registering source stream for '{}'", getId());
        synchronized (sourceStreams) {
            if (!sourceStreams.add(output)) {
                return;
            }
            if (this.sourceAudioStream == null) {
                try {
                    var pipedOutput = new PipedOutputStream();
                    this.sourceAudioPipedOutput = pipedOutput;
                    var pipedInput = new PipedInputStream(pipedOutput, 4096 * 4);
                    this.sourceAudioPipedInput = pipedInput;
                    if (streamSampleRate != HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE) {
                        logger.debug("Enabling audio resampling for the audio source stream {} => {}", streamSampleRate,
                                HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE);
                        var format = new AudioFormat(AudioFormat.CONTAINER_WAVE, AudioFormat.CODEC_PCM_SIGNED, false,
                                HABSpeakerAudioSource.SUPPORTED_BIT_DEPTH, null, streamSampleRate,
                                HABSpeakerAudioSource.SUPPORTED_CHANNELS);
                        this.sourceAudioStream = new ConvertedAudioStream(pipedInput, format,
                                HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE, HABSpeakerAudioSource.SUPPORTED_CHANNELS,
                                true);
                    } else {
                        logger.debug("Audio source stream already has sample rate {}",
                                HABSpeakerAudioSource.SUPPORTED_SAMPLE_RATE);
                        this.sourceAudioStream = pipedInput;
                    }
                } catch (IOException | UnsupportedAudioFormatException | UnsupportedAudioFileException e) {
                    logger.error("Unable to setup audio source stream", e);
                }
            }
            if (sourceStreams.size() == (serverSpotting ? 2 : 1)) {
                logger.debug("Send start listening {}", getId());
                sendClientCommand(WebsocketOutputCommand.START_LISTENING);
            }
        }
    }

    public void removeSourceListener(HABSpeakerAudioSource.HABSpeakerAudioStream output) {
        logger.debug("Unregistering source stream for '{}'", getId());
        synchronized (sourceStreams) {
            if (sourceStreams.remove(output) && sourceStreams.size() == (serverSpotting ? 1 : 0)) {
                logger.debug("Send stop listening {}", getId());
                sendClientCommand(WebsocketOutputCommand.STOP_LISTENING);
            }
            if (sourceStreams.size() == 0) {
                logger.debug("Disposing audio source internal resources for '{}'", getId());
                if (this.sourceAudioStream != null) {
                    try {
                        this.sourceAudioStream.close();
                    } catch (IOException ignored) {
                    }
                    this.sourceAudioStream = null;
                }
                if (this.sourceAudioPipedOutput != null) {
                    try {
                        this.sourceAudioPipedOutput.close();
                    } catch (IOException ignored) {
                    }
                    this.sourceAudioPipedOutput = null;
                }
                if (this.sourceAudioPipedInput != null) {
                    try {
                        this.sourceAudioPipedInput.close();
                    } catch (IOException ignored) {
                    }
                    this.sourceAudioPipedInput = null;
                }
            }
        }
    }

    @Override
    public void disconnect() {
        var session = getSession();
        if (session != null) {
            try {
                session.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void spot() {
        onRemoteSpot();
    }

    @Override
    public void playerCommand(PlayPauseType command) {
        var data = new HashMap<String, Object>();
        data.put("type", PlayPauseType.PLAY.equals(command) ? "play" : "pause");
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public void playerCommand(NextPreviousType command) {
        var data = new HashMap<String, Object>();
        data.put("type", NextPreviousType.NEXT.equals(command) ? "next" : "previous");
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public void playerCommand(RewindFastforwardType command) {
        var mediaState = this.mediaState;
        if (mediaState == null || mediaState.totalSeconds < 1L) {
            logger.warn("Unable to seek missed media info");
            return;
        }
        int newProgress = (int) ((((double) mediaState.currentSecond) / ((double) mediaState.totalSeconds)) * 100.0)
                + (RewindFastforwardType.REWIND.equals(command) ? -3 : 3);
        playerSeekToPercent(newProgress);
    }

    @Override
    public void playerSeekToSecond(long second) {
        var data = new HashMap<String, Object>();
        data.put("type", "seek");
        data.put("second", second);
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public void playerSeekToPercent(int percent) {
        var mediaState = this.mediaState;
        if (mediaState == null || mediaState.totalSeconds < 1L) {
            logger.error("Unable to seek missed media info");
            return;
        }
        if (percent > 99) {
            this.playerSeekToSecond(mediaState.totalSeconds);
        } else if (percent < 1) {
            this.playerSeekToSecond(0);
        } else {
            var seekSecond = (long) ((((double) percent) / 100.0) * (double) mediaState.totalSeconds);
            this.playerSeekToSecond(seekSecond);
        }
    }

    @Override
    public void playerStop() {
        var data = new HashMap<String, Object>();
        data.put("type", "stop");
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public void playerStart(StartMediaMessage startMediaMessage) {
        var data = new HashMap<String, Object>();
        data.put("type", "start");
        data.put("provider", startMediaMessage.provider.toString());
        data.put("mediaId", startMediaMessage.mediaId);
        data.put("second", startMediaMessage.startSecond);
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public void playerClaim(MediaProvider provider) {
        var data = new HashMap<String, Object>();
        data.put("type", "claim");
        data.put("provider", provider.toString());
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public void onWebSocketConnect(@Nullable Session sess) {
        if (sess == null) {
            // never
            return;
        }
        this.session = sess;
        this.remote = sess.getRemote();
        logger.info("New client connected.");
        scheduledDisconnection = executor.schedule(() -> {
            try {
                sess.disconnect();
            } catch (IOException ignored) {
            }
        }, 5, TimeUnit.SECONDS);
    }

    private void sendClientCommand(WebsocketOutputCommand command) {
        sendClientCommand(command, new HashMap<>());
    }

    private void sendClientCommand(WebsocketOutputCommand command, Map<String, Object> args) {
        args.put("cmd", command.toString());
        var remote = getRemote();
        if (remote != null) {
            try {
                remote.sendStringByFuture(new ObjectMapper().writeValueAsString(args));
            } catch (JsonProcessingException e) {
                logger.warn("JsonProcessingException writing JSON message: ", e);
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte @Nullable [] payload, int offset, int len) {
        logger.trace("Received binary data of length {}", len);
        if (payload != null) {
            writeToSourceStreams(payload);
        }
    }

    private void writeToSourceStreams(byte[] payload) {
        if (this.sourceAudioStream == null || this.sourceAudioPipedOutput == null) {
            logger.debug("Source already disposed ignoring data");
            return;
        }
        byte[] convertedPayload;
        try {
            this.sourceAudioPipedOutput.write(payload);
            int available = this.sourceAudioStream.available();
            if (available > 0) {
                convertedPayload = this.sourceAudioStream.readNBytes(available);
            } else {
                return;
            }
        } catch (IOException e) {
            logger.error("Error writing source audio", e);
            return;
        }
        for (var sourceAudioStream : sourceStreams) {
            try {
                sourceAudioStream.write(convertedPayload);
            } catch (IOException e) {
                logger.debug("IOException while piping source data: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onWebSocketText(@Nullable String message) {
        try {
            var messageJson = new ObjectMapper().readValue(message, WEBSOCKET_MAPPER_TYPE_REF);
            handleCommand(WebsocketInputCommand.valueOf((String) Objects.requireNonNull(messageJson.get("cmd"))),
                    messageJson);
        } catch (JsonProcessingException e) {
            logger.warn("Exception parsing JSON message: ", e);
        }
    }

    @Override
    public void onWebSocketError(@Nullable Throwable cause) {
        logger.warn("WebSocket Error: ", cause);
    }

    @Override
    public void onWebSocketClose(int statusCode, @Nullable String reason) {
        this.session = null;
        this.remote = null;
        logger.debug("Session closed with code {}: {}", statusCode, reason);
        wsAdapter.removeHandler(this);
        stopDropIn();
        sourceStreams.forEach(l -> {
            try {
                l.close();
            } catch (IOException err) {
                logger.error("IOException closing source audio stream", err);
            }
        });
        unregisterSpeakerComponents(id);
        sourceStreams.clear();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setSinkVolume(int value) {
        var data = new HashMap<String, Object>();
        data.put("value", value);
        sendClientCommand(WebsocketOutputCommand.SINK_VOLUME, data);
    }

    @Override
    public void setSourceVolume(int value) {
        var data = new HashMap<String, Object>();
        data.put("value", value);
        sendClientCommand(WebsocketOutputCommand.SOURCE_VOLUME, data);
    }

    @Override
    public void setMediaVolume(int value) {
        var data = new HashMap<String, Object>();
        data.put("type", "volume");
        data.put("level", value);
        sendClientCommand(WebsocketOutputCommand.MEDIA_COMMAND, data);
    }

    @Override
    public int getMediaVolume() {
        return mediaVolume;
    }

    @Override
    public @Nullable MediaState getMediaState() {
        return mediaState;
    }

    @Override
    public int getSinkVolume() {
        return sinkVolume;
    }

    @Override
    public int getSourceVolume() {
        return sourceVolume;
    }

    public @Nullable RemoteEndpoint getRemote() {
        return this.remote;
    }

    public @Nullable Session getSession() {
        return this.session;
    }

    public boolean isConnected() {
        Session sess = this.session;
        return sess != null && sess.isOpen();
    }

    private enum WebsocketInputCommand {
        INITIALIZE,
        CONFIGURED,
        ON_SPOT,
        SINK_VOLUME,
        SOURCE_VOLUME,
        MEDIA_STATE,
    }

    private enum WebsocketOutputCommand {
        CONFIGURE,
        INITIALIZED,
        START_LISTENING,
        STOP_LISTENING,
        SINK_VOLUME,
        SOURCE_VOLUME,
        MEDIA_COMMAND,
    }
}
