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
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.auth.Authentication;
import org.openhab.core.auth.AuthenticationException;
import org.openhab.core.auth.User;
import org.openhab.core.auth.UserApiTokenCredentials;
import org.openhab.core.auth.UserRegistry;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.voice.VoiceManager;
import org.openhab.voice.habspeaker.internal.auth.HABSpeakerJwtHelper;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOProtocol;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOProtocolListener;
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
    private static final String API_TOKEN_PREFIX = "oh.";
    public static final String ALT_AUTH_HEADER = "X-OPENHAB-TOKEN";
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebSocketProtocol.class);
    private final HttpService httpService;
    private final List<HABSpeakerWebSocketIO> wsHandlers = new ArrayList<>();
    private final ScheduledExecutorService executor = ThreadPoolManager.getScheduledPool("OH-ui-habspeaker");
    protected final BundleContext bundleContext;
    protected final VoiceManager voiceManager;
    protected final AudioManager audioManager;
    private final UserRegistry userRegistry;
    private final HABSpeakerJwtHelper jwtHelper;
    private final ScheduledFuture<?> pingTask;
    private final HABSpeakerIOProtocolListener protocolListener;
    private final HABSpeakerConfigProvider configProvider;
    private final HttpClient httpClient;

    public HABSpeakerWebSocketProtocol(HABSpeakerIOProtocolListener protocolListener,
            HABSpeakerConfigProvider configProvider, BundleContext bundleContext, HttpClient httpClient,
            HttpService httpService, AudioManager audioManager, VoiceManager voiceManager, UserRegistry userRegistry) {
        this.protocolListener = protocolListener;
        this.configProvider = configProvider;
        this.bundleContext = bundleContext;
        this.httpService = httpService;
        this.audioManager = audioManager;
        this.voiceManager = voiceManager;
        this.userRegistry = userRegistry;
        this.httpClient = httpClient;
        this.jwtHelper = new HABSpeakerJwtHelper();
        this.pingTask = executor.scheduleWithFixedDelay(this::pingHandlers, 60, 30, TimeUnit.SECONDS);
    }

    @Override
    public void register() {
        try {
            httpService.registerServlet(WS_PATH, this, null, new HABSpeakerWebsocketContext(this, userRegistry,
                    configProvider, httpService.createDefaultHttpContext()));
            logger.debug("Started HABSpeaker at " + WS_PATH);
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
            try {
                if (handler != null) {
                    var remote = handler.getRemote();
                    if (remote != null) {
                        remote.sendPing(ByteBuffer.wrap("oh".getBytes(StandardCharsets.UTF_8)));
                    }
                }
            } catch (IOException e) {
                logger.debug("Ping failed: {}", e.getMessage());
            }
        }
    }

    private void disconnectHandlers() {
        logger.debug("Disconnecting {} clients...", wsHandlers.size());
        var handlers = new ArrayList<>(wsHandlers);
        for (var handler : handlers) {
            protocolListener.onDisconnected(handler);
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

    public boolean isValidToken(String token) {
        try {
            if (token.startsWith(API_TOKEN_PREFIX)) {
                // Allow access to the websocket using user generated tokens
                logger.debug("Validating access through oh token");
                UserApiTokenCredentials credentials = new UserApiTokenCredentials(token);
                Authentication auth = userRegistry.authenticate(credentials);
                User user = userRegistry.get(auth.getUsername());
                if (user == null) {
                    throw new AuthenticationException("User not found in registry");
                }
                return true;
            } else {
                logger.debug("Validating jwt token");
                jwtHelper.verifyAndParseJwtAccessToken(token);
                return true;
            }
        } catch (AuthenticationException e) {
            logger.debug("AuthenticationException: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void configure(@Nullable WebSocketServletFactory webSocketServletFactory) {
        if (webSocketServletFactory != null) {
            webSocketServletFactory.getPolicy().setIdleTimeout(60000);
            webSocketServletFactory.setCreator(
                    (request, response) -> new HABSpeakerWebSocketIO(this, configProvider, httpClient, executor));
        }
    }

    public void addHandler(HABSpeakerWebSocketIO habSpeakerWebSocketIO) {
        wsHandlers.add(habSpeakerWebSocketIO);
        protocolListener.onConnected(habSpeakerWebSocketIO);
    }

    public void removeHandler(HABSpeakerWebSocketIO habSpeakerWebSocketIO) {
        wsHandlers.remove(habSpeakerWebSocketIO);
        protocolListener.onDisconnected(habSpeakerWebSocketIO);
    }
}
