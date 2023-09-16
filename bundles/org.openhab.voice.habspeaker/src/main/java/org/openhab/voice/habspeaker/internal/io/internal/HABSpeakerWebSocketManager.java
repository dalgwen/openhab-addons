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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.io.websocket.WebSocketAdapter;
import org.openhab.core.voice.VoiceManager;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOClient;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOListener;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerWebSocketManager} class defines the adapter to
 * create websocket connections.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
@Component(service = { HABSpeakerIOManager.class, WebSocketAdapter.class })
public class HABSpeakerWebSocketManager implements HABSpeakerIOManager, WebSocketAdapter {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketManager.class);
    private final List<HABSpeakerWebSocketClient> wsHandlers = new ArrayList<>();
    private final ScheduledExecutorService executor = ThreadPoolManager.getScheduledPool("voice-habspeaker");
    protected final BundleContext bundleContext;
    protected final VoiceManager voiceManager;
    protected final AudioManager audioManager;
    private final ScheduledFuture<?> pingTask;
    private final HABSpeakerConfigProvider configProvider;

    private final Set<HABSpeakerIOClient> speakerConnections = Collections.synchronizedSet(new HashSet<>());

    private @Nullable HABSpeakerIOListener connectionListener = null;

    @Activate
    public HABSpeakerWebSocketManager(BundleContext bundleContext, final @Reference AudioManager audioManager,
            final @Reference VoiceManager voiceManager, final @Reference HABSpeakerConfigProvider configProvider) {
        this.bundleContext = bundleContext;
        this.configProvider = configProvider;
        this.audioManager = audioManager;
        this.voiceManager = voiceManager;
        this.pingTask = executor.scheduleWithFixedDelay(this::pingHandlers, 10, 5, TimeUnit.SECONDS);
    }

    private void pingHandlers() {
        var handlers = new ArrayList<>(wsHandlers);
        for (var handler : handlers) {
            if (handler != null) {
                boolean pinned = false;
                var remote = handler.getRemote();
                if (remote != null) {
                    try {
                        remote.sendPing(ByteBuffer.wrap("oh".getBytes(StandardCharsets.UTF_8)));
                        pinned = true;
                    } catch (IOException ignored) {
                    }
                }
                if (!pinned) {
                    logger.warn("ping failed, disconnecting speaker {}", handler.getId());
                    var session = handler.getSession();
                    removeHandler(handler);
                    if (session != null) {
                        session.close();
                    }
                }
            }

        }
    }

    private void disconnectHandlers() {
        logger.debug("Disconnecting {} clients...", wsHandlers.size());
        var handlers = new ArrayList<>(wsHandlers);
        for (var handler : handlers) {
            onSpeakerDisconnected(handler);
            var session = handler.getSession();
            if (session != null) {
                try {
                    session.disconnect();
                } catch (IOException e) {
                    logger.debug("Disconnect failed: {}", e.getMessage());
                }
            }
        }
    }

    public List<HABSpeakerIOClient> getSpeakerConnections() {
        synchronized (speakerConnections) {
            return new ArrayList<>(speakerConnections);
        }
    }

    private void onSpeakerConnected(HABSpeakerIOClient speaker) throws IllegalStateException {
        synchronized (speakerConnections) {
            if (getSpeakerConnection(speaker.getId()) != null) {
                throw new IllegalStateException("Another speaker with the same id is already connected");
            }
            speakerConnections.add(speaker);
            var protocolListener = this.connectionListener;
            if (protocolListener != null) {
                protocolListener.onConnected(speaker);
            }
            logger.debug("connected speakers {}", speakerConnections.size());
        }
    }

    private void onSpeakerDisconnected(HABSpeakerIOClient speaker) {
        logger.debug("speaker disconnected '{}'", speaker.getId());
        synchronized (speakerConnections) {
            speakerConnections.remove(speaker);
            var protocolListener = this.connectionListener;
            if (protocolListener != null) {
                protocolListener.onDisconnected(speaker);
            }
            logger.debug("connected speakers {}", speakerConnections.size());
        }
    }

    protected void addHandler(HABSpeakerWebSocketClient speaker) {
        wsHandlers.add(speaker);
        onSpeakerConnected(speaker);
    }

    public void setConnectionListener(@Nullable HABSpeakerIOListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public @Nullable HABSpeakerIOClient getSpeakerConnection(String id) {
        synchronized (speakerConnections) {
            return speakerConnections.stream()
                    .filter(speakerConnection -> speakerConnection.getId().equalsIgnoreCase(id)).findAny().orElse(null);
        }
    }

    protected void removeHandler(HABSpeakerWebSocketClient habSpeakerWebSocketIO) {
        if (wsHandlers.remove(habSpeakerWebSocketIO)) {
            onSpeakerDisconnected(habSpeakerWebSocketIO);
        }
    }

    @Override
    public String getId() {
        return "habspeaker";
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest,
            ServletUpgradeResponse servletUpgradeResponse) {
        logger.debug("creating connection!");
        return new HABSpeakerWebSocketClient(this, configProvider, executor);
    }

    public synchronized void dispose() {
        logger.debug("Unregistering protocols");
        pingTask.cancel(true);
        disconnectHandlers();
    }
}
