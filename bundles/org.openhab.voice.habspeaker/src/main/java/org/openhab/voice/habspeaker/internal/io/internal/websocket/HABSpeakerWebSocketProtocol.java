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
package org.openhab.voice.habspeaker.internal.io.internal.websocket;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.auth.UserRegistry;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.voice.VoiceManager;
import org.openhab.voice.habspeaker.internal.auth.HABSpeakerSystemSecurityHelper;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOProtocol;
import org.openhab.voice.habspeaker.internal.io.internal.websocket.internal.HABSpeakerWebsocketContext;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerWebSocketProtocol} class defines the WebSocket servlet
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebSocketProtocol extends WebSocketServlet implements HABSpeakerIOProtocol {
    public static final String WS_PATH = "/" + SERVICE_ID + "/ws";
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketProtocol.class);
    private final HttpService httpService;
    private final List<HABSpeakerWebSocketIO> wsHandlers = new ArrayList<>();
    private final ScheduledExecutorService executor = ThreadPoolManager.getScheduledPool("OH-ui-habspeaker");
    protected final BundleContext bundleContext;
    protected final VoiceManager voiceManager;
    protected final AudioManager audioManager;
    private final UserRegistry userRegistry;
    private final HABSpeakerSystemSecurityHelper apiSecurityHelper;
    private final ScheduledFuture<?> pingTask;
    private final HABSpeakerIOManager ioManager;
    private final HABSpeakerConfigProvider configProvider;

    public HABSpeakerWebSocketProtocol(HABSpeakerIOManager ioManager, HABSpeakerConfigProvider configProvider,
            BundleContext bundleContext, HttpService httpService, AudioManager audioManager, VoiceManager voiceManager,
            UserRegistry userRegistry, HABSpeakerSystemSecurityHelper apiSecurityHelper) {
        this.ioManager = ioManager;
        this.configProvider = configProvider;
        this.bundleContext = bundleContext;
        this.httpService = httpService;
        this.audioManager = audioManager;
        this.voiceManager = voiceManager;
        this.userRegistry = userRegistry;
        this.apiSecurityHelper = apiSecurityHelper;
        this.pingTask = executor.scheduleWithFixedDelay(this::pingHandlers, 60, 30, TimeUnit.SECONDS);
    }

    @Override
    public void register() {
        try {
            httpService.registerServlet(WS_PATH, this, null, new HABSpeakerWebsocketContext(apiSecurityHelper,
                    userRegistry, httpService.createDefaultHttpContext()));
            logger.debug("HABSpeaker accepts ws connections at " + WS_PATH);
        } catch (NamespaceException | ServletException e) {
            logger.error("Error during HABSpeakerWebsocketIO, service will not work: {}", e.getMessage());
        }
    }

    @Override
    public void unregister() {
        httpService.unregister(WS_PATH);
        pingTask.cancel(true);
        disconnectHandlers();
    }

    private void pingHandlers() {
        logger.debug("Pinging {} clients...", wsHandlers.size());
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
            ioManager.onDisconnected(handler);
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

    @Override
    public void configure(@Nullable WebSocketServletFactory webSocketServletFactory) {
        if (webSocketServletFactory != null) {
            webSocketServletFactory.getPolicy().setIdleTimeout(60000);
            webSocketServletFactory.setCreator(
                    (request, response) -> new HABSpeakerWebSocketIO(this, configProvider, executor, ioManager));
        }
    }

    public void addHandler(HABSpeakerWebSocketIO habSpeakerWebSocketIO) {
        wsHandlers.add(habSpeakerWebSocketIO);
        ioManager.onConnected(habSpeakerWebSocketIO);
    }

    public void removeHandler(HABSpeakerWebSocketIO habSpeakerWebSocketIO) {
        if (wsHandlers.remove(habSpeakerWebSocketIO)) {
            ioManager.onDisconnected(habSpeakerWebSocketIO);
        }
    }
}
