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

import static org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider.RUSTPOTTER_ADDON_FOLDER;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.auth.Role;
import org.openhab.core.auth.UserRegistry;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
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
    private final UserRegistry userRegistry;

    private final HttpClient httpClient;
    private final ThingRegistry thingRegistry;
    private String lastCodeVerifier = "";

    @Activate
    public HABSpeakerResource(final @Reference HABSpeakerConfigProvider configProvider,
            final @Reference UserRegistry userRegistry, final @Reference HttpClientFactory httpClientFactory,
            final @Reference ThingRegistry thingRegistry) {
        this.configProvider = configProvider;
        this.userRegistry = userRegistry;
        this.thingRegistry = thingRegistry;
        this.httpClient = httpClientFactory.getCommonHttpClient();
        logger.debug("HABSpeaker Resource added at rest/{}", PATH_HABSPEAKER);
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/config/{speaker_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieves the speaker configuration.", responses = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "Server error") })
    public Response config(@PathParam("speaker_id") String id) {
        var thing = thingRegistry.get(new ThingUID("habspeaker", "speaker", id));
        String label = null;
        if (thing != null) {
            label = thing.getLabel();
        }
        var config = configProvider.getConfig();
        Map<String, Object> configResp = new HashMap<>();
        configResp.put("label", (label != null && !label.isBlank()) ? label : "HABSpeaker");
        return addAllowCorsHeaders(Response.ok(configResp)).build();
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/rustpotter/{model_name}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Retrieves a rustpotter model.", responses = {
            @ApiResponse(responseCode = "200", description = "Model file bytes"),
            @ApiResponse(responseCode = "404", description = "Not Found") })
    public Response getRustpotterModel(@PathParam("model_name") String modelName) {
        String fileName = modelName + ".rpw";
        var modelFile = java.nio.file.Path.of(HABSpeakerConfigProvider.RUSTPOTTER_FOLDER, fileName).toFile();
        if (!modelFile.exists()) {
            // fallback to rustpotter add-on dir
            modelFile = java.nio.file.Path.of(RUSTPOTTER_ADDON_FOLDER, fileName).toFile();
            if (!modelFile.exists()) {
                return Response.status(Response.Status.NOT_FOUND).entity("Entity model not found: " + modelName)
                        .build();
            }
        }
        return addAllowCorsHeaders(Response.ok(modelFile, MediaType.APPLICATION_OCTET_STREAM)).build();
    }

    private Response.ResponseBuilder addAllowCorsHeaders(Response.ResponseBuilder builder) {
        return builder.header("Access-Control-Allow-Origin", "*").header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }
}
