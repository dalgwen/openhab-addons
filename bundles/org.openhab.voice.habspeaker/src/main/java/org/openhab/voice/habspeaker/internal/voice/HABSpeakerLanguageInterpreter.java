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
package org.openhab.voice.habspeaker.internal.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeader;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.core.voice.text.InterpretationException;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerLanguageInterpreter} class defines the speaker Audio Sink
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerLanguageInterpreter implements HumanLanguageInterpreter {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerLanguageInterpreter.class);
    private final HABSpeakerIO speakerIO;
    private final HABSpeakerConfigProvider configProvider;
    private final HttpClient httpClient;

    public HABSpeakerLanguageInterpreter(HABSpeakerIO speakerIO, HABSpeakerConfigProvider configProvider,
            HttpClient httpClient) {
        this.speakerIO = speakerIO;
        this.configProvider = configProvider;
        this.httpClient = httpClient;
    }

    @Override
    public String getId() {
        return "habspeaker::" + speakerIO.getId() + "::hli";
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return "HAB Speaker Language Interpreter";
    }

    @Override
    public String interpret(Locale locale, String text) throws InterpretationException {
        var config = configProvider.getConfig();
        var lowerText = text.toLowerCase();
        if (speakerIO.getDropIn() != null && compareTemplate(config.stopDropInPhrase, lowerText)) {
            speakerIO.dropIn(null);
            return "done";
        }
        if (!config.listenOnSpotifyPhrase.isBlank()) {
            var matcher = Arrays.stream(config.listenOnSpotifyPhrase.split(";")) //
                    .map(template -> Pattern.compile(template.toLowerCase().replace("$*", "(?<search>.*)"))
                            .matcher(lowerText)) //
                    .filter(Matcher::matches).findAny();
            if (matcher.isPresent()) {
                listenTrackOnSpotify(matcher.get().group("search"));
                return "done";
            }
        }
        throw new InterpretationException("Unknown voice command");
    }

    private boolean compareTemplate(String template, String text) {
        var trimmedText = text.trim();
        return !template.isBlank()
                && Arrays.stream(template.split(";")).anyMatch(t -> t.trim().equalsIgnoreCase(trimmedText));
    }

    @Override
    public @Nullable String getGrammar(Locale locale, String s) {
        return null;
    }

    @Override
    public Set<Locale> getSupportedLocales() {
        return Set.of();
    }

    @Override
    public Set<String> getSupportedGrammarFormats() {
        return Set.of();
    }

    public void listenTrackOnSpotify(String name) {
        listenOnSpotify(SpotifySearchType.TRACK, name);
    }

    public void listenArtistOnSpotify(String name) {
        listenOnSpotify(SpotifySearchType.ARTIST, name);
    }

    public String searchSpotifyURI(SpotifySearchType type, String search) throws IllegalStateException,
            ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
        var spotifyToken = configProvider.getSpotifyToken();
        if (spotifyToken.isBlank()) {
            throw new IllegalStateException("Missing spotify token");
        }
        var response = httpClient.newRequest("https://api.spotify.com/v1/search") //
                .header(HttpHeader.AUTHORIZATION, "Bearer " + spotifyToken) //
                .header(HttpHeader.ACCEPT, "application/json") //
                .header(HttpHeader.CONTENT_TYPE, "application/json") //
                .param("q", search) //
                .param("type", type.toString()) //
                .param("include_external", "false").send();
        var responseStatus = response.getStatus();
        var responseContent = response.getContentAsString();
        if (responseStatus > 299 || responseStatus < 200) {
            throw new IllegalStateException(
                    String.format("Spotify api returned an error %d: %s", responseStatus, responseContent));
        }
        var jsonResponse = new ObjectMapper().readValue(responseContent, SpotifySearchResponse.class);
        if (jsonResponse.tracks == null || jsonResponse.tracks.items.isEmpty()) {
            logger.warn("CHECK no results");
            throw new IllegalStateException("Spotify return no results");
        }
        return jsonResponse.tracks.items.get(0).uri;
    }

    private void listenOnSpotify(SpotifySearchType type, String name) {
        if (name.isBlank()) {
            logger.warn("Search is blank");
            return;
        }
        try {
            var trackUri = searchSpotifyURI(type, name);
            if (trackUri.isBlank()) {
                throw new IllegalStateException("Spotify uri can not be black");
            }
            speakerIO.playerStart(HABSpeakerIO.MediaProvider.SPOTIFY, trackUri);
        } catch (ExecutionException | InterruptedException | TimeoutException | JsonProcessingException e) {
            logger.warn("listen on spotify has failed:", e);
        }
    }

    private enum SpotifySearchType {
        TRACK("track"),
        ARTIST("artist");

        private final String name;

        SpotifySearchType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SpotifyTrack {
        public String type = "";
        public String uri = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SpotifyTracks {
        public List<SpotifyTrack> items = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SpotifySearchResponse {
        public @Nullable SpotifyTracks tracks;
    }
}
