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
package org.openhab.voice.habspeaker.internal.ui;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.auth.ManagedUser;
import org.openhab.core.auth.Role;
import org.openhab.core.auth.User;
import org.openhab.core.auth.UserRegistry;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfig;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JSONRequired;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * An implementation of {@link HttpContext} which will handle the gzip-compressed assets.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component
@JaxrsResource
@JaxrsName(HABSpeakerResource.PATH_HABSPEAKER)
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@JSONRequired
@RolesAllowed({ Role.USER, Role.ADMIN })
@Path(HABSpeakerResource.PATH_HABSPEAKER)
@Tag(name = HABSpeakerResource.PATH_HABSPEAKER)
@NonNullByDefault
public class HABSpeakerResource implements RESTResource {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerResource.class);
    private static final String HAB_SPEAKER_COOKIE = "X-HABSPEAKER-SESSIONID";
    public static final String PATH_HABSPEAKER = "habspeaker";
    private final HABSpeakerConfigProvider configProvider;
    private final UserRegistry userRegistry;

    @Activate
    public HABSpeakerResource(final @Reference HABSpeakerConfigProvider configProvider,
            final @Reference UserRegistry userRegistry) {
        this.configProvider = configProvider;
        this.userRegistry = userRegistry;
        logger.debug("HAB Speaker Resource added at rest/{}", PATH_HABSPEAKER);
    }

    @POST
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/cookie")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED })
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieves the speaker cookie.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HABSpeakerConfig.class))),
            @ApiResponse(responseCode = "500", description = "Server error") })
    public Response cookie(@Context SecurityContext securityContext, @Context UriInfo uriInfo,
            @FormParam("refresh_token") String refreshToken) {
        var responseBuilder = Response.ok();
        if (securityContext.isSecure()) {
            var principal = securityContext.getUserPrincipal();
            if (principal instanceof User) {
                var uid = ((User) principal).getUID();
                userRegistry.getAll().stream().filter(u -> u.getUID().contentEquals(uid)).findAny()
                        .flatMap(user -> ((ManagedUser) user).getSessions().stream()
                                .filter(s -> s.getRefreshToken().contentEquals(refreshToken)).findAny())
                        .ifPresent((session) -> {
                            logger.debug("Setting speaker cookie for user {}", principal.getName());
                            responseBuilder.cookie(new NewCookie(HAB_SPEAKER_COOKIE, session.getSessionId(),
                                    "/" + SERVICE_ID, uriInfo.getBaseUri().getHost(), null, 2147483647, false, true));
                        });
            }
        }
        return responseBuilder.build();
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieves the speaker configuration.", responses = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HABSpeakerConfig.class))),
            @ApiResponse(responseCode = "500", description = "Server error") })
    public Response config() {
        return Response.ok(configProvider.getConfig()).build();
    }
}
