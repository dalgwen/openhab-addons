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

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.Authentication;
import org.openhab.core.auth.AuthenticationException;
import org.openhab.core.auth.User;
import org.openhab.core.auth.UserApiTokenCredentials;
import org.openhab.core.auth.UserRegistry;
import org.openhab.voice.habspeaker.internal.auth.HABSpeakerSystemSecurityHelper;
import org.osgi.service.http.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link HttpContext} which will handle security to open ws connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerWebsocketContext implements HttpContext {
    private static final String API_TOKEN_PREFIX = "oh.";
    public static final String ALT_AUTH_HEADER = "X-OPENHAB-TOKEN";
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerWebsocketContext.class);
    private final HttpContext defaultHttpContext;
    private final HABSpeakerSystemSecurityHelper apiSecurityHelper;
    private final UserRegistry userRegistry;

    public HABSpeakerWebsocketContext(HABSpeakerSystemSecurityHelper apiSecurityHelper, UserRegistry userRegistry,
            HttpContext defaultHttpContext) {
        this.userRegistry = userRegistry;
        this.apiSecurityHelper = apiSecurityHelper;
        this.defaultHttpContext = defaultHttpContext;
    }

    @Override
    public boolean handleSecurity(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null) {
            return false;
        }
        // this checks if implicit user role has been granted based on the system api security configuration
        if (apiSecurityHelper.isImplicitUserRole(request)) {
            // security is disabled
            logger.debug("No auth checks, implicit member role");
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
        if (accessToken == null || !isValidToken(accessToken)) {
            logger.debug("Speaker authorized missing jwt token");
            return false;
        }
        logger.debug("Speaker authorized by jwt token");
        return true;
    }

    private boolean isValidToken(String token) {
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
                apiSecurityHelper.verifyAndParseJwtAccessToken(token);
                return true;
            }
        } catch (AuthenticationException e) {
            logger.debug("AuthenticationException: {}", e.getMessage());
            return false;
        }
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
