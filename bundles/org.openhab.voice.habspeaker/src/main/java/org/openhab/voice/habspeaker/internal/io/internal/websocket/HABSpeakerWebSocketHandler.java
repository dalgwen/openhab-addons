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

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;
import static org.openhab.voice.habspeaker.internal.io.internal.websocket.HABSpeakerWebSocketServlet.ALT_AUTH_HEADER;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSink;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSource;
import org.openhab.voice.habspeaker.internal.handler.HABSpeakerThingHandler;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerKS;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerWebSocketHandler} class controls an individual WebSocket client connection
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebSocketHandler extends WebSocketAdapter implements HABSpeakerIO {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketHandler.class);
    private final HABSpeakerWebSocketServlet servlet;
    private final ScheduledExecutorService executor;
    private String id = "";
    private @Nullable String listeningItem = null;
    private int requiredSinkSampleRate = 0;
    private final ConcurrentLinkedQueue<OutputStream> listeners = new ConcurrentLinkedQueue<>();
    private @Nullable ScheduledFuture<?> scheduledDisconnection = null;
    private boolean initialized = false;
    private @Nullable HABSpeakerKS ks;

    private int sinkVolume = 100;
    private @Nullable HABSpeakerThingHandler thingHandler = null;

    public HABSpeakerWebSocketHandler(HABSpeakerWebSocketServlet servlet, ScheduledExecutorService executor) {
        this.servlet = servlet;
        this.executor = executor;
    }

    @Override
    public void setThingHandler(@Nullable HABSpeakerThingHandler handler) {
        thingHandler = handler;
    }

    public void handleCommand(WebsocketInputCommand command, Map<String, Object> data) {
        logger.debug("Handling command {}", command);
        switch (command) {
            case INITIALIZE:
                id = (String) data.getOrDefault("id", "");
                requiredSinkSampleRate = (int) data.get("sampleRate");
                @Nullable
                String token = (String) data.get("token");
                if (token == null) {
                    // Allow access to the websocket using user generated tokens
                    token = getSession().getUpgradeRequest().getHeader(ALT_AUTH_HEADER);
                }
                scheduledDisconnection.cancel(true);
                if (isValidAccess(token)) {
                    registerSpeakerComponents();
                } else {
                    logger.warn("Unauthorized access, disconnecting client");
                    try {
                        getSession().disconnect();
                    } catch (IOException ignored) {
                    }
                }
                break;
            case ON_SPOT:
                var ks = this.ks;
                if (ks != null) {
                    ks.onRemoteSpot();
                }
                break;
            case SINK_VOLUME:
                try {
                    sinkVolume = Integer.parseInt(data.getOrDefault("value", "0").toString());
                    if (thingHandler != null) {
                        thingHandler.updateSinkVolume(sinkVolume);
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

    private void startDialog() throws IllegalStateException {
        var sink = servlet.audioManager.getSink(getSinkId());
        var source = servlet.audioManager.getSource(getSourceId());
        if (sink == null || source == null) {
            logger.warn("Missing audio components");
            return;
        }
        var ks = new HABSpeakerKS(this);
        this.ks = ks;
        servlet.voiceManager.startDialog(ks, null, null, null, List.of(), source, sink, null, null, listeningItem);
    }

    private void stopDialog() {
        var source = servlet.audioManager.getSource(getSourceId());
        if (source != null) {
            try {
                servlet.voiceManager.stopDialog(source);
            } catch (Exception e) {
                logger.debug("Unable to stop dialog for {}", getSourceId());
            }
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
                remote.sendBytes(ByteBuffer.wrap(buff.array()));
            }
        } catch (IOException e) {
            logger.warn("IOException while sending audio");
        }
    }

    public void addSourceListener(OutputStream output) {
        synchronized (listeners) {
            listeners.add(output);
            if (listeners.size() == 1) {
                sendClientCommand(WebsocketOutputCommand.START_LISTENING);
            }
        }
    }

    public void removeSourceListener(OutputStream output) {
        synchronized (listeners) {
            listeners.remove(output);
            if (listeners.size() == 0) {
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
    public void onWebSocketConnect(@Nullable Session sess) {
        super.onWebSocketConnect(sess);
        if (sess == null) {
            // never
            return;
        }
        logger.info("New client connected.");
        scheduledDisconnection = executor.schedule(() -> {
            try {
                sess.disconnect();
            } catch (IOException ignored) {
            }
        }, 5, TimeUnit.SECONDS);
    }

    private synchronized void registerSpeakerComponents() {
        try {
            if (id.isBlank()) {
                throw new IOException("Unable to register audio components");
            }
            servlet.addHandler(this);
            String label = "HAB Speaker (" + id + ")";
            var sinkStereo = false;
            var thingHandler = this.thingHandler;
            if (thingHandler != null) {
                // send speaker configuration before initialization
                var config = thingHandler.getSpeakerConfig();
                var thingLabel = thingHandler.getThing().getLabel();
                if (thingLabel != null && !thingLabel.isBlank()) {
                    label = thingLabel;
                }
                listeningItem = !config.listeningItem.isBlank() ? config.listeningItem : null;
                var initializedConfig = new HashMap<String, Object>();
                var currentVolume = thingHandler.getSinkVolume();
                sinkVolume = currentVolume != null ? currentVolume : config.sinkVolume;
                initializedConfig.put("sinkVolume", sinkVolume);
                sinkStereo = config.sinkStereo;
                initializedConfig.put("sinkStereo", sinkStereo);
                sendClientCommand(WebsocketOutputCommand.CONFIGURE, initializedConfig);
            }
            var sourceId = getSourceId();
            logger.debug("Registering audio source {}", sourceId);
            if (servlet.audioComponentRegistrations.containsKey(sourceId)) {
                throw new IOException("Source already registered");
            }
            var sinkId = getSinkId();
            logger.debug("Registering audio sink {}", sinkId);
            if (servlet.audioComponentRegistrations.containsKey(sinkId)) {
                throw new IOException("Sink already registered");
            }
            initialized = true;
            // register source
            servlet.audioComponentRegistrations.put(sourceId, servlet.bundleContext.registerService(
                    AudioSource.class.getName(), new HABSpeakerAudioSource(sourceId, label, this), new Hashtable<>()));
            // register sink
            servlet.audioComponentRegistrations.put(sinkId,
                    servlet.bundleContext.registerService(AudioSink.class.getName(),
                            new HABSpeakerAudioSink(sinkId, label, requiredSinkSampleRate, sinkStereo ? 2 : 1, this),
                            new Hashtable<>()));
            startDialog();
            sendClientCommand(WebsocketOutputCommand.INITIALIZED);
        } catch (IOException | IllegalStateException e) {
            logger.warn("Disconnecting client: {}", e.getMessage());
            disconnect();
        }
    }

    private synchronized void unregisterSpeakerComponents() {
        stopDialog();
        var sourceId = getSourceId();
        ServiceRegistration<?> sourceReg = servlet.audioComponentRegistrations.remove(sourceId);
        if (sourceReg != null) {
            logger.debug("Unregistering audio source {}", sourceId);
            sourceReg.unregister();
        }
        var sinkId = getSinkId();
        ServiceRegistration<?> sinkReg = servlet.audioComponentRegistrations.remove(sinkId);
        if (sinkReg != null) {
            logger.debug("Unregistering audio sink {}", sinkId);
            sinkReg.unregister();
        }
    }

    private void sendClientCommand(WebsocketOutputCommand command) {
        sendClientCommand(command, new HashMap<>());
    }

    private void sendClientCommand(WebsocketOutputCommand command, Map<String, Object> args) {
        args.put("cmd", command.toString());
        var remote = getRemote();
        if (remote != null) {
            try {
                remote.sendString(new ObjectMapper().writeValueAsString(args));
            } catch (JsonProcessingException e) {
                logger.warn("JsonProcessingException writing JSON message: ", e);
            } catch (IOException e) {
                logger.debug("IOException sending client command: ", e);
            }
        }
    }

    private String getSinkId() {
        return SERVICE_ID + "::" + id + "::sink";
    }

    private String getSourceId() {
        return SERVICE_ID + "::" + id + "::source";
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
        super.onWebSocketClose(statusCode, reason);
        logger.debug("Session closed with code {}: {}", statusCode, reason);
        servlet.removeHandler(this);
        if (initialized) {
            unregisterSpeakerComponents();
        }
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
