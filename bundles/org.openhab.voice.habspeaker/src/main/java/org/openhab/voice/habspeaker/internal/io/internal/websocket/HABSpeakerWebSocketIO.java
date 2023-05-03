/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.voice.habspeaker.internal.io.internal.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.openhab.voice.habspeaker.internal.io.internal.HABSpeakerIOBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerWebSocketIO} class controls an individual WebSocket client connection
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebSocketIO extends HABSpeakerIOBase implements WebSocketListener {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketIO.class);
    private static final TypeReference<HashMap<String, Object>> WEBSOCKET_MAPPER_TYPE_REF = new TypeReference<>() {
    };
    private final ConcurrentLinkedQueue<OutputStream> listeners = new ConcurrentLinkedQueue<>();

    private String id = "";
    private volatile @Nullable Session session;
    private @Nullable RemoteEndpoint remote;
    private final HABSpeakerWebSocketProtocol servlet;
    private final ScheduledExecutorService executor;
    private @Nullable ScheduledFuture<?> scheduledDisconnection;
    private int sinkVolume;
    private int mediaVolume;
    private long streamSampleRate;
    private @Nullable MediaState mediaState;

    public HABSpeakerWebSocketIO(HABSpeakerWebSocketProtocol servlet, HABSpeakerConfigProvider configProvider,
            HttpClient httpClient, ScheduledExecutorService executor, HABSpeakerIOManager ioManager) {
        super(servlet.audioManager, servlet.voiceManager, httpClient, servlet.bundleContext, configProvider, ioManager);
        this.servlet = servlet;
        this.executor = executor;
    }

    public void handleCommand(WebsocketInputCommand command, Map<String, Object> data) {
        logger.debug("Handling command {}", command);
        var thingHandler = getThingHandler();
        try {
            switch (command) {
                case INITIALIZE:
                    streamSampleRate = (int) data.get("sampleRate");
                    var scheduledDisconnection = this.scheduledDisconnection;
                    if (scheduledDisconnection != null) {
                        scheduledDisconnection.cancel(true);
                    }
                    id = (String) data.getOrDefault("id", "");
                    servlet.addHandler(this);
                    thingHandler = getThingHandler();
                    if (thingHandler != null) {
                        if (thingHandler.getSpeakerConfig().sampleRate != -1L) {
                            streamSampleRate = thingHandler.getSpeakerConfig().sampleRate;
                        }
                        sendClientCommand(HABSpeakerWebSocketIO.WebsocketOutputCommand.CONFIGURE,
                                getSpeakerConfigMessage(thingHandler));
                    } else {
                        registerSpeakerComponents(id, streamSampleRate, null);
                        sendClientCommand(HABSpeakerWebSocketIO.WebsocketOutputCommand.INITIALIZED);
                    }
                    break;
                case CONFIGURED:
                    if (thingHandler == null) {
                        throw new IOException("configured command send by unregistered speaker");
                    }
                    registerSpeakerComponents(id, streamSampleRate, thingHandler);
                    sendClientCommand(HABSpeakerWebSocketIO.WebsocketOutputCommand.INITIALIZED);
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
                case MEDIA_STATE:
                    try {
                        var currentSecond = Long.parseLong(data.getOrDefault("currentSecond", "0").toString());
                        var totalSeconds = Long.parseLong(data.getOrDefault("totalSeconds", "0").toString());
                        var playbackState = PlaybackStates
                                .valueOf(data.getOrDefault("state", "stopped").toString().toUpperCase());
                        var volume = Integer.parseInt(data.getOrDefault("volume", "0").toString());
                        var provider = data.getOrDefault("provider", "").toString();
                        var mediaId = data.getOrDefault("id", "").toString();
                        @Nullable
                        String playlistId = data.containsKey("playlistId") ? data.get("playlistId").toString() : null;
                        var playlistIndex = Integer.parseInt(data.getOrDefault("playlistIndex", "0").toString());
                        mediaVolume = volume;
                        var mediaState = new MediaState(MediaProvider.fromString(provider), mediaId, playlistId,
                                playlistIndex, currentSecond, totalSeconds, playbackState);
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

    public void addSourceListener(OutputStream output) {
        synchronized (listeners) {
            if (listeners.add(output) && listeners.size() == (serverSpotting ? 2 : 1)) {
                logger.debug("Send start listening {}", getId());
                sendClientCommand(WebsocketOutputCommand.START_LISTENING);
            }
        }
    }

    public void removeSourceListener(OutputStream output) {
        synchronized (listeners) {
            if (listeners.remove(output) && listeners.size() == (serverSpotting ? 1 : 0)) {
                logger.debug("Send stop listening {}", getId());
                sendClientCommand(WebsocketOutputCommand.STOP_LISTENING);
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
    public void updateSpotifyToken(String accessToken) {
        var data = new HashMap<String, Object>();
        data.put("token", accessToken);
        sendClientCommand(WebsocketOutputCommand.SPOTIFY_TOKEN, data);
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
        if (startMediaMessage.mediaId != null) {
            data.put("mediaId", startMediaMessage.mediaId);
        }
        if (startMediaMessage.playlistId != null) {
            data.put("playlistId", startMediaMessage.playlistId);
        }
        data.put("playlistIndex", startMediaMessage.playlistIndex);
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
            writeToListeners(payload);
        }
    }

    private void writeToListeners(byte[] payload) {
        for (var listener : listeners) {
            try {
                listener.write(payload);
            } catch (IOException e) {
                logger.debug("IOException while piping to source: {}", e.getMessage());
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
        servlet.removeHandler(this);
        stopDropIn();
        unregisterSpeakerComponents(id);
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
        MEDIA_STATE,
    }

    private enum WebsocketOutputCommand {
        CONFIGURE,
        INITIALIZED,
        START_LISTENING,
        STOP_LISTENING,
        SINK_VOLUME,
        MEDIA_COMMAND,
        SPOTIFY_TOKEN,
    }
}
