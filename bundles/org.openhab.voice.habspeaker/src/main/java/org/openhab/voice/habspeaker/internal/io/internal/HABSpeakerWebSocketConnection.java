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

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.voice.KSService;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSink;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSource;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerThingConfig;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOConnection;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOHandler;
import org.openhab.voice.habspeaker.internal.io.internal.messages.in.HABSpeakerInInitialize;
import org.openhab.voice.habspeaker.internal.io.internal.messages.in.HABSpeakerInMediaState;
import org.openhab.voice.habspeaker.internal.io.internal.messages.in.HABSpeakerInMessage;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutConfig;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutInitialized;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutMessage;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutSinkVolume;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutSourceVolume;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutStartListening;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.HABSpeakerOutStopListening;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaClaim;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaNext;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaPause;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaPlay;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaPrev;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaSeek;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaStart;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaStop;
import org.openhab.voice.habspeaker.internal.io.internal.messages.out.media.HABSpeakerOutMediaVolume;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerKS;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerLanguageInterpreter;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerWebSocketConnection} class controls an individual WebSocket client connection
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebSocketConnection implements HABSpeakerIOConnection, WebSocketListener {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketConnection.class);
    protected final Map<String, ServiceRegistration<?>> audioComponentRegistrations = new ConcurrentHashMap<>();
    protected final HABSpeakerConfigProvider configProvider;
    private final HABSpeakerLanguageInterpreter speakerLanguageInterpreter;
    protected @Nullable HABSpeakerIOHandler thingHandler = null;
    private volatile @Nullable Session session;
    private @Nullable RemoteEndpoint remote;
    private final HABSpeakerWebSocketAdapter wsAdapter;
    private final ScheduledExecutorService executor;
    private @Nullable ScheduledFuture<?> scheduledDisconnection;
    private int sinkVolume = -1;
    private int sourceVolume = -1;
    private int mediaVolume = -1;
    private int clientSampleRate;
    private @Nullable MediaState mediaState;

    private boolean initialized = false;
    protected boolean serverSpotting = false;
    private @Nullable HABSpeakerKS ks = null;
    private String id = "";

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private @Nullable HABSpeakerAudioSource audioSource = null;

    public HABSpeakerWebSocketConnection(HABSpeakerWebSocketAdapter wsAdapter, HABSpeakerConfigProvider configProvider,
            ScheduledExecutorService executor) {
        this.wsAdapter = wsAdapter;
        this.executor = executor;
        this.configProvider = configProvider;
        this.speakerLanguageInterpreter = new HABSpeakerLanguageInterpreter(this, wsAdapter, configProvider);
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

    @Override
    public void setListening(boolean listening) {
        sendClientCommand(listening ? new HABSpeakerOutStartListening() : new HABSpeakerOutStopListening());
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
        sendClientCommand(
                PlayPauseType.PLAY.equals(command) ? new HABSpeakerOutMediaPlay() : new HABSpeakerOutMediaPause());
    }

    @Override
    public void playerCommand(NextPreviousType command) {
        sendClientCommand(
                NextPreviousType.NEXT.equals(command) ? new HABSpeakerOutMediaNext() : new HABSpeakerOutMediaPrev());
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
        sendClientCommand(new HABSpeakerOutMediaSeek(second));
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
        sendClientCommand(new HABSpeakerOutMediaStop());
    }

    @Override
    public void playerStart(StartMediaMessage startMediaMessage) {
        sendClientCommand(new HABSpeakerOutMediaStart(startMediaMessage.provider.toString(), startMediaMessage.mediaId,
                startMediaMessage.startSecond));
    }

    @Override
    public void playerClaim(MediaProvider provider) {
        sendClientCommand(new HABSpeakerOutMediaClaim(provider.toString()));
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

    private <T extends HABSpeakerOutMessage> void sendClientCommand(T msg) {
        var remote = getRemote();
        if (remote != null) {
            try {
                remote.sendStringByFuture(new ObjectMapper().writeValueAsString(msg));
            } catch (JsonProcessingException e) {
                logger.warn("JsonProcessingException writing JSON message: ", e);
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte @Nullable [] payload, int offset, int len) {
        logger.trace("Received binary data of length {}", len);
        HABSpeakerAudioSource audioSource = this.audioSource;
        if (payload != null && audioSource != null) {
            audioSource.writeToStreams(payload);
        }
    }

    @Override
    public void onWebSocketText(@Nullable String message) {
        try {
            var rootMessageNode = jsonMapper.readTree(message);
            if (rootMessageNode.has("cmd")) {
                try {
                    var cmd = rootMessageNode.get("cmd").asText().trim().toUpperCase();
                    logger.debug("Handling msg '{}'", cmd);
                    var messageType = HABSpeakerInMessage.InputCommand.valueOf(cmd);
                    switch (messageType) {
                        case INITIALIZE -> {
                            var initializeMsg = jsonMapper.readValue(message, HABSpeakerInInitialize.class);
                            var scheduledDisconnection = this.scheduledDisconnection;
                            if (scheduledDisconnection != null) {
                                scheduledDisconnection.cancel(true);
                            }
                            // update connection settings
                            id = initializeMsg.id;
                            clientSampleRate = initializeMsg.sampleRate;
                            // register client so it gets connected to its handler if any
                            wsAdapter.addHandler(this);
                            // send speaker configuration message
                            var speakerConfig = HABSpeakerOutConfig.forSpeaker(this);
                            serverSpotting = speakerConfig.spotMode == SpotMode.SERVER;
                            sendClientCommand(speakerConfig);
                        }
                        case ON_SPOT -> {
                            onRemoteSpot();
                        }
                        case CONFIGURED -> {
                            registerSpeakerComponents(id, clientSampleRate, thingHandler);
                            sendClientCommand(new HABSpeakerOutInitialized());
                        }
                        case MEDIA_STATE -> {
                            var mediaStateMessage = jsonMapper.readValue(message, HABSpeakerInMediaState.class);
                            mediaVolume = mediaStateMessage.volume;
                            var mediaState = new MediaState(MediaProvider.fromString(mediaStateMessage.provider),
                                    mediaStateMessage.id, mediaStateMessage.currentSecond,
                                    mediaStateMessage.totalSeconds, PlaybackStates.valueOf(mediaStateMessage.state));
                            this.mediaState = mediaState;
                            if (thingHandler != null) {
                                thingHandler.onMediaStateUpdate(mediaState, mediaVolume);
                            }
                        }
                    }

                } catch (IOException | IllegalStateException e) {
                    logger.warn("Disconnecting client: {}", e.getMessage());
                    disconnect();
                }
            }
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
        unregisterSpeakerComponents(id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setSinkVolume(int value) {
        this.sinkVolume = value;
        if (initialized) {
            sendClientCommand(new HABSpeakerOutSinkVolume(value));
        }
    }

    @Override
    public void setSourceVolume(int value) {
        this.sourceVolume = value;
        if (initialized) {
            sendClientCommand(new HABSpeakerOutSourceVolume(value));
        }
    }

    @Override
    public void setMediaVolume(int value) {
        this.mediaVolume = value;
        if (initialized) {
            sendClientCommand(new HABSpeakerOutMediaVolume(value));
        }
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

    @Override
    public void setThingHandler(@Nullable HABSpeakerIOHandler handler) {
        thingHandler = handler;
    }

    @Override
    public @Nullable HABSpeakerIOHandler getThingHandler() {
        return thingHandler;
    }

    protected synchronized void registerSpeakerComponents(String id, int clientSampleRate,
            @Nullable HABSpeakerIOHandler thingHandler) throws IOException {
        if (id.isBlank()) {
            throw new IOException("Unable to register audio components");
        }
        String label = "HABSpeaker (" + id + ")";
        var sinkStereo = false;
        @Nullable
        String listeningItem = null;
        int streamSampleRate = clientSampleRate;
        if (thingHandler != null) {
            var config = thingHandler.getSpeakerConfig();
            var thingLabel = thingHandler.getLabel();
            if (thingLabel != null && !thingLabel.isBlank()) {
                label = thingLabel;
            }
            if (config.sampleRate != -1) {
                streamSampleRate = config.sampleRate;
            }
            listeningItem = !config.listeningItem.isBlank() ? config.listeningItem : null;
            sinkStereo = config.sinkStereo;
        } else {
            var config = new HABSpeakerThingConfig();
            if (config.sampleRate != -1) {
                streamSampleRate = config.sampleRate;
            }
        }
        logger.debug("Registering dialog components for '{}' (stream sample rate {})", id, streamSampleRate);
        this.initialized = true;
        // register source
        this.audioSource = new HABSpeakerAudioSource(getSourceId(id), label, streamSampleRate, serverSpotting, this);
        logger.debug("Registering audio source {}", this.audioSource.getId());
        audioComponentRegistrations.put(this.audioSource.getId(), wsAdapter.bundleContext
                .registerService(AudioSource.class.getName(), this.audioSource, new Hashtable<>()));
        // register sink
        var sink = new HABSpeakerAudioSink(getSinkId(id), label, this, sinkStereo ? 2 : 1, streamSampleRate);
        logger.debug("Registering audio sink {}", sink.getId());
        audioComponentRegistrations.put(sink.getId(),
                wsAdapter.bundleContext.registerService(AudioSink.class.getName(), sink, new Hashtable<>()));
        // init dialog
        var defaultHLI = wsAdapter.voiceManager.getHLI();
        List<HumanLanguageInterpreter> hlis = defaultHLI == null ? List.of(speakerLanguageInterpreter)
                : List.of(speakerLanguageInterpreter, defaultHLI);
        @Nullable
        KSService ks = null;
        var dCBuilder = wsAdapter.voiceManager.getDialogContextBuilder();
        dCBuilder.withSource(this.audioSource).withSink(sink).withListeningItem(listeningItem);
        if (thingHandler != null) {
            var speakerConfig = thingHandler.getSpeakerConfig();
            if (!speakerConfig.stt.isBlank()) {
                dCBuilder.withSTT(wsAdapter.voiceManager.getSTT(speakerConfig.stt));
            }
            if (!speakerConfig.tts.isBlank()) {
                dCBuilder.withTTS(wsAdapter.voiceManager.getTTS(speakerConfig.tts));
            }
            if (!speakerConfig.voice.isBlank()) {
                dCBuilder.withVoice(wsAdapter.voiceManager.getAllVoices().stream()//
                        .filter(v -> v.getUID().equals(speakerConfig.voice))//
                        .findAny().orElse(null));
            }
            var interpreters = wsAdapter.voiceManager.getHLIsByIds(speakerConfig.hlis);
            if (!interpreters.isEmpty()) {
                ArrayList<HumanLanguageInterpreter> finalInterpreters = new ArrayList<>();
                finalInterpreters.add(speakerLanguageInterpreter);
                finalInterpreters.addAll(interpreters);
                hlis = finalInterpreters;
            }
            if (!speakerConfig.keyword.isBlank()) {
                dCBuilder.withKeyword(speakerConfig.keyword);
            }
            if (!speakerConfig.ks.isBlank()) {
                ks = wsAdapter.voiceManager.getKS(speakerConfig.ks);
            }
        }
        this.ks = new HABSpeakerKS(this, ks);
        wsAdapter.voiceManager.startDialog(dCBuilder.withKS(this.ks)//
                .withHLIs(hlis)//
                .build());
    }

    protected synchronized void unregisterSpeakerComponents(String id) {
        if (initialized) {
            var source = wsAdapter.audioManager.getSource(getSourceId(id));
            if (source instanceof HABSpeakerAudioSource hsAudioSource) {
                try {
                    hsAudioSource.close();
                } catch (Exception ignored) {
                }
            }
            if (source != null) {
                try {
                    wsAdapter.voiceManager.stopDialog(source);
                } catch (Exception e) {
                    logger.debug("Unable to stop dialog for {}", source.getId());
                }
                ServiceRegistration<?> sourceReg = audioComponentRegistrations.remove(source.getId());
                if (sourceReg != null) {
                    logger.debug("Unregistering audio source {}", source.getId());
                    sourceReg.unregister();
                }
            }
            var sink = wsAdapter.audioManager.getSink(getSinkId(id));
            if (sink != null) {
                ServiceRegistration<?> sinkReg = audioComponentRegistrations.remove(sink.getId());
                if (sinkReg != null) {
                    logger.debug("Unregistering audio sink {}", sink.getId());
                    sinkReg.unregister();
                }
            }
        }
    }

    protected void onRemoteSpot() {
        var ks = this.ks;
        if (ks != null) {
            ks.onRemoteSpot();
        }
    }

    private String getSinkId(String id) {
        return SERVICE_ID + "::" + id + "::sink";
    }

    private String getSourceId(String id) {
        return SERVICE_ID + "::" + id + "::source";
    }
}
