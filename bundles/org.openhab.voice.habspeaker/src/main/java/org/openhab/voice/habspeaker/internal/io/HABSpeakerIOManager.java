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
package org.openhab.voice.habspeaker.internal.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.auth.UserRegistry;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.voice.VoiceManager;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfig;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.internal.websocket.HABSpeakerWebSocketProtocol;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerIOManager} manages the {@link HABSpeakerIOProtocol} implementations and the active speaker
 * connections
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = HABSpeakerIOManager.class)
@NonNullByDefault
public class HABSpeakerIOManager
        implements HABSpeakerIOProtocolListener, HABSpeakerConfigProvider.HABSpeakerConfigProviderListener {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerIOManager.class);
    private final List<HABSpeakerWebSocketProtocol> ioProtocols;
    private final Set<HABSpeakerIO> speakerConnections = Collections.synchronizedSet(new HashSet<>());
    private final HABSpeakerConfigProvider configProvider;
    private final HttpClientFactory httpClientFactory;
    private @Nullable HABSpeakerIOProtocolListener protocolListener = null;

    @Activate
    public HABSpeakerIOManager(BundleContext bundleContext, final @Reference HttpService httpService,
            final @Reference AudioManager audioManager, final @Reference VoiceManager voiceManager,
            final @Reference HttpClientFactory httpClientFactory, final @Reference UserRegistry userRegistry,
            final @Reference HABSpeakerConfigProvider configProvider) {
        this.configProvider = configProvider;
        this.httpClientFactory = httpClientFactory;
        this.ioProtocols = List.of(new HABSpeakerWebSocketProtocol(this, configProvider, bundleContext,
                httpClientFactory.getCommonHttpClient(), httpService, audioManager, voiceManager, userRegistry));
        configProvider.addListener(this);
    }

    public List<HABSpeakerIO> getSpeakerConnections() {
        synchronized (speakerConnections) {
            return new ArrayList<>(speakerConnections);
        }
    }

    public @Nullable HABSpeakerIO getSpeakerConnection(String id) {
        synchronized (speakerConnections) {
            return speakerConnections.stream()
                    .filter(speakerConnection -> speakerConnection.getId().equalsIgnoreCase(id)).findAny().orElse(null);
        }
    }

    public void setProtocolListener(HABSpeakerIOProtocolListener protocolListener) {
        this.protocolListener = protocolListener;
    }

    @Activate
    public synchronized void activate() {
        logger.debug("Registering protocols");
        ioProtocols.forEach(HABSpeakerIOProtocol::register);
    }

    @Deactivate
    public synchronized void dispose() {
        logger.debug("Unregistering protocols");
        configProvider.removeListener(this);
        ioProtocols.forEach(HABSpeakerIOProtocol::unregister);
    }

    @Override
    public void onConnected(HABSpeakerIO speaker) throws IllegalStateException {
        logger.debug("connecting speakers {}", speakerConnections.size());
        synchronized (speakerConnections) {
            if (getSpeakerConnection(speaker.getId()) != null) {
                throw new IllegalStateException("speaker already registered");
            }
            speakerConnections.add(speaker);
            var protocolListener = this.protocolListener;
            if (protocolListener != null) {
                protocolListener.onConnected(speaker);
            }
        }
    }

    @Override
    public void onDisconnected(HABSpeakerIO speaker) {
        logger.debug("speaker disconnected '{}'", speaker.getId());
        synchronized (speakerConnections) {
            speakerConnections.remove(speaker);
            logger.debug("connected speakers {}", speakerConnections.size());
            var protocolListener = this.protocolListener;
            if (protocolListener != null) {
                protocolListener.onDisconnected(speaker);
            }
        }
    }

    @Override
    public void onSpotifyTokenUpdate(String accessToken) {
        speakerConnections.forEach(speakerIO -> speakerIO.updateSpotifyToken(accessToken));
    }

    @Override
    public void onGlobalConfigUpdate(HABSpeakerConfig config) {
        // TODO: disconnect speakers?
    }
}
