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
package org.openhab.voice.habspeaker.internal.io.internal.websocket.internal;

import static org.openhab.voice.habspeaker.internal.io.internal.websocket.HABSpeakerWebSocketProtocol.ALT_AUTH_HEADER;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.internal.websocket.HABSpeakerWebSocketProtocol;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link HttpContext} which will handle security to open ws connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebsocketContext implements HttpContext {
    private static final String HAB_SPEAKER_COOKIE = "X-HABSPEAKER-SESSIONID";
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebsocketContext.class);
    private final HttpContext defaultHttpContext;
    private final HABSpeakerWebSocketProtocol servlet;
    private final HABSpeakerConfigProvider configProvider;

    /**
     * Constructs an {@link HABSpeakerWebsocketContext} with will another {@link HttpContext} as a base.
     *
     * @param configProvider
     * @param defaultHttpContext the base {@link HttpContext} - use {@link HttpService#createDefaultHttpContext()} to
     *            create a default one
     */
    public HABSpeakerWebsocketContext(HABSpeakerWebSocketProtocol servlet, HABSpeakerConfigProvider configProvider,
            HttpContext defaultHttpContext) {
        this.servlet = servlet;
        this.configProvider = configProvider;
        this.defaultHttpContext = defaultHttpContext;
    }

    @Override
    public boolean handleSecurity(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null) {
            return false;
        }
        if (!configProvider.getConfig().secure) {
            // security is disabled
            return defaultHttpContext.handleSecurity(request, response);
        }
        // Allow access to the websocket sending a token in the alternative auth header
        var accessToken = request.getHeader(ALT_AUTH_HEADER);
        // Allow access to the websocket sending a token among the valid protocols
        var tokenProtocolPrefix = "oh_token-";
        var speakerProtocol = "habspeaker";
        var protocols = request.getHeader("Sec-WebSocket-Protocol");
        if (protocols != null) {
            var protocolList = Arrays.stream(protocols.split(",")).map(String::trim).collect(Collectors.toList());
            for (var protocol : protocolList) {
                if (speakerProtocol.equals(protocol) && response != null) {
                    // as per spec one of the protocols should be returned as selected
                    response.setHeader("Sec-WebSocket-Protocol", speakerProtocol);
                } else if (protocol.startsWith(tokenProtocolPrefix) && accessToken == null) {
                    accessToken = protocol.replace(tokenProtocolPrefix, "");
                }
            }
        }
        if (accessToken == null) {
            return false;
        }
        return servlet.isValidToken(accessToken);
    }

    @Override
    public URL getResource(@Nullable String name) {
        return defaultHttpContext.getResource(name);
    }

    @Override
    public String getMimeType(@Nullable String name) {
        return defaultHttpContext.getMimeType(name);
    }
}
