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

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.auth.Role;
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
    public static final String PATH_HABSPEAKER = "habspeaker";
    private final HABSpeakerConfigProvider configProvider;

    @Activate
    public HABSpeakerResource(final @Reference HABSpeakerConfigProvider configProvider) {
        this.configProvider = configProvider;
        logger.debug("HAB Speaker Resource added at rest/{}", PATH_HABSPEAKER);
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
