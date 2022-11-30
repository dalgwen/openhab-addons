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

import static org.openhab.voice.habspeaker.internal.io.internal.websocket.HABSpeakerWebSocketProtocol.ALT_AUTH_HEADER;

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
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.openhab.voice.habspeaker.internal.io.internal.HABSpeakerIOBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerWebSocketIO} class controls an individual WebSocket client connection
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebSocketIO extends HABSpeakerIOBase implements WebSocketListener {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketIO.class);
    private volatile @Nullable Session session = null;
    private @Nullable RemoteEndpoint remote = null;
    private final HABSpeakerWebSocketProtocol servlet;
    private final ScheduledExecutorService executor;
    private String id = "";
    private final ConcurrentLinkedQueue<OutputStream> listeners = new ConcurrentLinkedQueue<>();
    private @Nullable ScheduledFuture<?> scheduledDisconnection = null;

    private int sinkVolume = 100;

    public HABSpeakerWebSocketIO(HABSpeakerWebSocketProtocol servlet, ScheduledExecutorService executor) {
        super(servlet.audioManager, servlet.voiceManager, servlet.bundleContext, servlet::getConfig);
        this.servlet = servlet;
        this.executor = executor;
    }

    public void handleCommand(WebsocketInputCommand command, Map<String, Object> data) {
        logger.debug("Handling command {}", command);
        switch (command) {
            case INITIALIZE:
                var requiredSinkSampleRate = (int) data.get("sampleRate");
                @Nullable
                String token = (String) data.get("token");
                var session = getSession();
                if (token == null && session != null) {
                    // Allow access to the websocket using user generated tokens
                    token = Objects.requireNonNull(session).getUpgradeRequest().getHeader(ALT_AUTH_HEADER);
                }
                scheduledDisconnection.cancel(true);
                if (isValidAccess(token)) {
                    id = (String) data.getOrDefault("id", "");
                    servlet.addHandler(this);
                    try {
                        var thingHandler = this.thingHandler;
                        var speakerConfig = getSpeakerConfig(thingHandler);
                        if (speakerConfig != null) {
                            sendClientCommand(HABSpeakerWebSocketIO.WebsocketOutputCommand.CONFIGURE, speakerConfig);
                        }
                        registerSpeakerComponents(id, requiredSinkSampleRate, thingHandler);
                        sendClientCommand(HABSpeakerWebSocketIO.WebsocketOutputCommand.INITIALIZED);
                    } catch (IOException | IllegalStateException e) {
                        logger.warn("Disconnecting client: {}", e.getMessage());
                        disconnect();
                    }
                } else {
                    logger.warn("Unauthorized access, disconnecting client");
                    disconnect();
                }
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
            default:
                logger.warn("Unhandled JSON command: {}", data);
        }
    }

    private boolean isValidAccess(@Nullable String token) {
        if (!servlet.getConfig().secure) {
            return true;
        }
        if (token == null) {
            logger.debug("Token is missed.");
            return false;
        }
        return servlet.isValidToken(token);
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
    public void spot() {
        onRemoteSpot();
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
            var messageJson = new ObjectMapper().readValue(message, HashMap.class);
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
        ON_SPOT,
        SINK_VOLUME,
    }

    private enum WebsocketOutputCommand {
        CONFIGURE,
        INITIALIZED,
        START_LISTENING,
        STOP_LISTENING,
        SINK_VOLUME,
    }
}
