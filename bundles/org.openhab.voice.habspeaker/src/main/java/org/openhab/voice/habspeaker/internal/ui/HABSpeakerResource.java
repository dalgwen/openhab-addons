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

import static java.util.stream.Collectors.joining;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;
import static org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider.RUSTPOTTER_ADDON_FOLDER;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.openhab.core.auth.ManagedUser;
import org.openhab.core.auth.Role;
import org.openhab.core.auth.User;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private static final String HAB_SPEAKER_COOKIE = "X-HABSPEAKER-SESSIONID";
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
        logger.debug("HAB Speaker Resource added at rest/{}", PATH_HABSPEAKER);
    }

    @POST
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/cookie")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED })
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieves the speaker cookie.", responses = {
            @ApiResponse(responseCode = "200", description = "OK"),
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
        configResp.put("secure", config.secure);
        configResp.put("spotifyEnabled",
                !config.spotifyClientId.isBlank() && !configProvider.getSpotifyToken().isBlank());
        configResp.put("label", (label != null && !label.isBlank()) ? label : "HAB Speaker");
        return Response.ok(configResp).build();
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
        return Response.ok(modelFile, MediaType.APPLICATION_OCTET_STREAM).build();
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/spotify/login")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Redirects to spotify login.", responses = {
            @ApiResponse(responseCode = "303", description = "See other redirect to spotify login"),
            @ApiResponse(responseCode = "500", description = "Server error") })
    public Response spotifyLogin(@Context UriInfo uriInfo) {
        var scope = "streaming+user-read-email+user-modify-playback-state+user-read-private";
        String redirect;
        String challenge;
        try {
            lastCodeVerifier = generateCodeVerifier();
            challenge = generateCodeChallenge(lastCodeVerifier);
            redirect = getRedirectUrlEncoded(uriInfo);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            logger.warn("Error generating redirect url", e);
            return Response.serverError().build();
        }
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("response_type", "code");
        requestParams.put("client_id", configProvider.getConfig().spotifyClientId);
        requestParams.put("scope", scope);
        requestParams.put("code_challenge_method", "S256");
        requestParams.put("code_challenge", challenge);
        requestParams.put("redirect_uri", redirect);
        String loginUrl = requestParams.keySet().stream().map(k -> k + "=" + requestParams.get(k))
                .collect(joining("&", "https://accounts.spotify.com/en/authorize?", ""));
        return Response.seeOther(URI.create(loginUrl)).build();
    }

    private String getRedirectUrlEncoded(UriInfo uriInfo) throws UnsupportedEncodingException {
        var host = uriInfo.getRequestUri().getHost();
        var port = uriInfo.getRequestUri().getPort();
        var scheme = uriInfo.getRequestUri().getScheme();
        var portSegment = port == -1 ? "" : (":" + port);
        var url = String.format("%s://%s%s/rest/habspeaker/spotify/login/callback", scheme, host, portSegment);
        return URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/spotify/login/callback")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Redirects to spotify login.", responses = {
            @ApiResponse(responseCode = "303", description = "See other redirect to HABSpeaker"),
            @ApiResponse(responseCode = "500", description = "Server error") })
    public Response spotifyLoginCallback(@Context UriInfo uriInfo, @QueryParam("code") @Nullable String code) {
        var codeVerifier = lastCodeVerifier;
        if (code == null || code.isBlank() || codeVerifier.isBlank()) {
            // TODO: return error page
            return Response.serverError().build();
        }
        try {
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("client_id", configProvider.getConfig().spotifyClientId);
            requestParams.put("grant_type", "authorization_code");
            requestParams.put("code", code);
            requestParams.put("code_verifier", codeVerifier);
            requestParams.put("redirect_uri", getRedirectUrlEncoded(uriInfo));
            String body = requestParams.keySet().stream().map(k -> k + "=" + requestParams.get(k))
                    .collect(joining("&", "", ""));
            var tokenRes = httpClient.newRequest("https://accounts.spotify.com/api/token").method(HttpMethod.POST)
                    .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .content(new StringContentProvider(body)).send();
            if (tokenRes.getStatus() != 200) {
                logger.warn("spotify authentication failed");
                return Response.status(tokenRes.getStatus(), tokenRes.getContentAsString()).build();
            }
            ObjectMapper mapper = new ObjectMapper();
            var tokenData = mapper.readValue(tokenRes.getContentAsString(),
                    new TypeReference<HashMap<String, Object>>() {
                    });
            var accessToken = tokenData.getOrDefault("access_token", "").toString();
            var refreshToken = tokenData.getOrDefault("refresh_token", "").toString();
            var expiresIn = Integer.parseInt(tokenData.getOrDefault("expires_in", 0).toString());
            configProvider.onSpotifyToken(accessToken, refreshToken, expiresIn);
        } catch (InterruptedException | TimeoutException | ExecutionException | UnsupportedEncodingException
                | JsonProcessingException e) {
            logger.warn("Error calling spotify login: ", e);
            return Response.serverError().build();
        }
        return Response.ok("LOGIN DONE").build();
    }

    String generateCodeVerifier() throws UnsupportedEncodingException {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }

    String generateCodeChallenge(String codeVerifier) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] bytes = codeVerifier.getBytes("US-ASCII");
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(bytes, 0, bytes.length);
        byte[] digest = messageDigest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
