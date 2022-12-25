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

import static org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.core.voice.text.InterpretationException;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final HABSpeakerIOManager ioManager;
    private final HABSpeakerConfigProvider configProvider;
    private final HttpClient httpClient;

    public HABSpeakerLanguageInterpreter(HABSpeakerIO speakerIO, HABSpeakerIOManager ioManager,
            HABSpeakerConfigProvider configProvider, HttpClient httpClient) {
        this.speakerIO = speakerIO;
        this.ioManager = ioManager;
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
        try {
            var config = configProvider.getConfig();
            var lowerText = text.toLowerCase();
            // drop-in phrases
            if (speakerIO.getDropIn() == null) {
                String speakerName = compareTemplateWithParameter(config.startDropInPhrase, lowerText);
                if (!speakerName.isBlank()) {
                    var optionalSpeakerIO = ioManager.getSpeakerConnections().stream().filter(io -> {
                        var handler = io.getThingHandler();
                        if (handler == null) {
                            return false;
                        }
                        return speakerName.equalsIgnoreCase(handler.getLabel()) || //
                        speakerName.equalsIgnoreCase(handler.getLocationLabel());
                    }).findAny();
                    if (optionalSpeakerIO.isPresent()) {
                        speakerIO.dropIn(optionalSpeakerIO.get());
                        return config.commandSentMessage;
                    }
                }
            } else {
                if (compareTemplate(config.stopDropInPhrase, lowerText)) {
                    speakerIO.dropIn(null);
                    return config.commandSentMessage;
                }
            }
            // media search phrases
            String webAudioSearch = compareTemplateWithParameter(config.listenOnWebPhrase, lowerText);
            if (!webAudioSearch.isBlank()) {
                // assume a valid url
                var localResult = searchMediaFile(WEB_AUDIO_MEDIA_PATH, webAudioSearch);
                if (!localResult.isBlank()) {
                    listenOnWebPlayer(localResult);
                    return config.commandSentMessage;
                }
            }
            String webVideoSearch = compareTemplateWithParameter(config.watchOnWebPhrase, lowerText);
            if (!webVideoSearch.isBlank()) {
                // assume a valid url
                var localResult = searchMediaFile(WEB_VIDEO_MEDIA_PATH, webVideoSearch);
                if (!localResult.isBlank()) {
                    watchOnWebPlayer(localResult);
                    return config.commandSentMessage;
                }
            }
            String spotifySearch = compareTemplateWithParameter(config.listenOnSpotifyPhrase, lowerText);
            if (!spotifySearch.isBlank()) {
                // assume a valid spotify URI
                var localResult = searchMediaFile(SPOTIFY_MEDIA_PATH, spotifySearch);
                if (!localResult.isBlank()) {
                    listenOnSpotify(localResult);
                } else {
                    listenTrackOnSpotify(spotifySearch);
                }
                return config.commandSentMessage;
            }
            String ytSearch = compareTemplateWithParameter(config.watchOnYouTubePhrase, lowerText);
            if (!ytSearch.isBlank()) {
                // assume a valid YouTube video id or a list id prefixed by 'playlist:'
                var localResult = searchMediaFile(YOUTUBE_MEDIA_PATH, ytSearch);
                if (!localResult.isBlank()) {
                    playOnYouTube(localResult);
                } else {
                    watchOnYouTube(ytSearch);
                }
                return config.commandSentMessage;
            }
            // media playback control phrases
            if (compareTemplate(config.resumeMediaPhrase, lowerText)) {
                speakerIO.playerCommand(PlayPauseType.PLAY);
                return config.commandSentMessage;
            }
            if (compareTemplate(config.pauseMediaPhrase, lowerText)) {
                speakerIO.playerCommand(PlayPauseType.PAUSE);
                return config.commandSentMessage;
            }
            if (compareTemplate(config.stopMediaPhrase, lowerText)) {
                speakerIO.playerStop();
                return config.commandSentMessage;
            }
            if (compareTemplate(config.fastForwardMediaProgressPhrase, lowerText)) {
                speakerIO.playerCommand(RewindFastforwardType.FASTFORWARD);
                return config.commandSentMessage;
            }
            if (compareTemplate(config.rewindMediaProgressPhrase, lowerText)) {
                speakerIO.playerCommand(RewindFastforwardType.REWIND);
                return config.commandSentMessage;
            }
            if (compareTemplate(config.nextMediaPhrase, lowerText)) {
                speakerIO.playerCommand(NextPreviousType.NEXT);
                return config.commandSentMessage;
            }
            if (compareTemplate(config.previousMediaPhrase, lowerText)) {
                speakerIO.playerCommand(NextPreviousType.PREVIOUS);
                return config.commandSentMessage;
            }
            // media playback volume phrases
            if (compareTemplate(config.decreaseMediaVolumePhrase, lowerText)) {
                var level = speakerIO.getMediaVolume();
                if (level > config.mediaVolumeStep - 1) {
                    speakerIO.setMediaVolume(level - config.mediaVolumeStep);
                } else {
                    speakerIO.setMediaVolume(0);
                }
                return config.commandSentMessage;
            }
            if (compareTemplate(config.increaseMediaVolumePhrase, lowerText)) {
                var level = speakerIO.getMediaVolume();
                if (level < 100 - config.mediaVolumeStep) {
                    speakerIO.setMediaVolume(level + config.mediaVolumeStep);
                } else {
                    speakerIO.setMediaVolume(100);
                }
                return config.commandSentMessage;
            }
        } catch (Exception e) {
            logger.warn("Speaker Interpretation error: ", e);
        }
        throw new InterpretationException("Unknown voice command");
    }

    private String compareTemplateWithParameter(String template, String lowerText) {
        if (template.isBlank()) {
            return "";
        }
        return Arrays.stream(template.split(";")) //
                .map(templateOption -> Pattern.compile( //
                        templateOption.trim().toLowerCase().replace("$*", "(?<search>.*)") //
                ).matcher(lowerText)) //
                .filter(Matcher::matches).findAny().map(m -> m.group("search")).orElse("");
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

    // Search media
    private String searchMediaFile(Path mediaPath, String name) {
        if (mediaPath.toFile().exists()) {
            try (var is = HashMap.class.getResourceAsStream(mediaPath.toAbsolutePath().toString())) {
                var mediaMap = new ObjectMapper().readValue(is, new TypeReference<HashMap<String, String>>() {
                });
                var value = mediaMap.get(name);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (IOException e) {
                logger.warn("Unable to read media file: {}", e.getMessage());
            }
        }
        return "";
    }

    // Third party integrations

    public void playOnYouTube(String ytId) {
        speakerIO.playerStart(HABSpeakerIO.MediaProvider.YOUTUBE, ytId);
    }

    public void watchOnYouTube(String name) {
        if (name.isBlank()) {
            logger.warn("Search is blank");
            return;
        }
        try {
            var youTubeId = searchYouTubeId(name);
            if (youTubeId.isBlank()) {
                throw new IllegalStateException("YouTube id can not be black");
            }
            playOnYouTube(youTubeId);
        } catch (ExecutionException | InterruptedException | TimeoutException | JsonProcessingException e) {
            logger.warn("watch on YouTube has failed:", e);
        }
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
        logger.debug("Spotify search api response {}: {}", responseStatus, responseContent);
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

    public String searchYouTubeId(String search) throws IllegalStateException, ExecutionException, InterruptedException,
            TimeoutException, JsonProcessingException {
        logger.warn("searching in youtube: {}", search);
        var apiKey = configProvider.getConfig().youtubeAPIKey;
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Missing Youtube api key");
        }
        var response = httpClient.newRequest("https://youtube.googleapis.com/youtube/v3/search") //
                .header(HttpHeader.ACCEPT, "application/json") //
                .param("q", search) //
                .param("maxResults", "1") //
                .param("key", apiKey).send();
        var responseStatus = response.getStatus();
        var responseContent = response.getContentAsString();
        logger.debug("YouTube search response {}: {}", responseStatus, responseContent);
        if (responseStatus > 299 || responseStatus < 200) {
            throw new IllegalStateException(
                    String.format("Youtube api returned an error %d: %s", responseStatus, responseContent));
        }
        var jsonResponse = new ObjectMapper().readValue(responseContent, YouTubeSearchResponse.class);
        if (jsonResponse.items.isEmpty()) {
            throw new IllegalStateException("Youtube return no results");
        }
        var searchItem = jsonResponse.items.get(0);
        switch (searchItem.id.kind) {
            case "youtube#channel":
                return "playlist:" + getYoutubeChannelUploadsPlaylist(searchItem.id.channelId);
            case "youtube#playlist":
                return "playlist:" + searchItem.id.playlistId;
            case "youtube#video":
                return searchItem.id.videoId;
            default:
                throw new IllegalStateException("YouTube returns an unsupported item type");
        }
    }

    private String getYoutubeChannelUploadsPlaylist(String channelId)
            throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
        var apiKey = configProvider.getConfig().youtubeAPIKey;
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Missing Youtube api key");
        }
        var response = httpClient.newRequest("https://youtube.googleapis.com/youtube/v3/channels") //
                .header(HttpHeader.ACCEPT, "application/json") //
                .param("part", "contentDetails") //
                .param("id", channelId) //
                .param("maxResults", "1") //
                .param("key", apiKey).send();
        var responseStatus = response.getStatus();
        var responseContent = response.getContentAsString();
        logger.debug("YouTube channels response {}: {}", responseStatus, responseContent);
        if (responseStatus > 299 || responseStatus < 200) {
            throw new IllegalStateException(
                    String.format("Youtube api returned an error %d: %s", responseStatus, responseContent));
        }
        var jsonResponse = new ObjectMapper().readValue(responseContent, YouTubeChannelsResponse.class);
        if (jsonResponse.items.isEmpty()) {
            throw new IllegalStateException("Youtube return no results");
        }
        var uploadsPlayList = jsonResponse.items.get(0).contentDetails.relatedPlaylists.uploads;
        if (uploadsPlayList.isEmpty()) {
            throw new IllegalStateException("Youtube return no results");
        }
        return uploadsPlayList;
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
            listenOnSpotify(trackUri);
        } catch (ExecutionException | InterruptedException | TimeoutException | JsonProcessingException e) {
            logger.warn("listen on spotify has failed:", e);
        }
    }

    private void listenOnSpotify(String spotifyUri) {
        speakerIO.playerStart(HABSpeakerIO.MediaProvider.SPOTIFY, spotifyUri);
    }

    private void listenOnWebPlayer(String url) {
        speakerIO.playerStart(HABSpeakerIO.MediaProvider.WEB_AUDIO, url);
    }

    private void watchOnWebPlayer(String url) {
        speakerIO.playerStart(HABSpeakerIO.MediaProvider.WEB_VIDEO, url);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeSearchItemId {
        public String kind = "";
        public String videoId = "";
        public String channelId = "";
        public String playlistId = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeSearchItem {
        public YouTubeSearchItemId id = new YouTubeSearchItemId();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeChannelRelatedPlaylists {
        public String likes = "";
        public String uploads = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeChannelContentDetails {
        public YouTubeChannelRelatedPlaylists relatedPlaylists = new YouTubeChannelRelatedPlaylists();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeChannelItem {
        public YouTubeChannelContentDetails contentDetails = new YouTubeChannelContentDetails();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeSearchResponse extends YouTubeItemsResponse<YouTubeSearchItem> {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeChannelsResponse extends YouTubeItemsResponse<YouTubeChannelItem> {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class YouTubeItemsResponse<T> {
        public List<T> items = new ArrayList<>();
    }
}
