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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
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
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfig;
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
        var config = configProvider.getConfig();
        String lowerText = text.toLowerCase();
        boolean interpreted = false;
        logger.debug("Trying to interpret text: {}", text);
        try {
            interpreted = interpretDropInCommands(config, lowerText) || //
                    interpretMediaSearch(config, lowerText) || //
                    interpretMediaTransfer(config, lowerText) || //
                    interpretMediaControl(config, lowerText) || //
                    interpretMediaVolume(config, lowerText);
        } catch (Exception e) {
            logger.warn("Speaker Interpretation error: ", e);
        }
        logger.debug("Text interpreted: {}", interpreted);
        if (!interpreted) {
            throw new InterpretationException("Unknown voice command");
        }
        return config.commandSentMessage;
    }

    private boolean interpretMediaVolume(HABSpeakerConfig config, String lowerText) {
        if (compareTemplate(config.decreaseMediaVolumePhrase, lowerText)) {
            var level = speakerIO.getMediaVolume();
            if (level > config.mediaVolumeStep - 1) {
                speakerIO.setMediaVolume(level - config.mediaVolumeStep);
            } else {
                speakerIO.setMediaVolume(0);
            }
            return true;
        }
        if (compareTemplate(config.increaseMediaVolumePhrase, lowerText)) {
            var level = speakerIO.getMediaVolume();
            if (level < 100 - config.mediaVolumeStep) {
                speakerIO.setMediaVolume(level + config.mediaVolumeStep);
            } else {
                speakerIO.setMediaVolume(100);
            }
            return true;
        }
        return false;
    }

    private boolean interpretMediaControl(HABSpeakerConfig config, String lowerText) {
        @Nullable
        HABSpeakerIO commandTarget = speakerIO;
        var mediaState = speakerIO.getMediaState();
        if (mediaState == null || mediaState.playbackState == HABSpeakerIO.PlaybackStates.STOPPED) {
            // try target another connected speaker how is playing media
            logger.debug("Speaker not playing media, looking for another");
            commandTarget = ioManager.getSpeakerConnections().stream().filter(filterSpeakerNotStopped())
                    .sorted(sortPlayingFirst()).findAny().orElse(null);
        }
        if (commandTarget == null) {
            logger.debug("No devices playing media");
            return false;
        }
        if (compareTemplate(config.resumeMediaPhrase, lowerText)) {
            commandTarget.playerCommand(PlayPauseType.PLAY);
            return true;
        }
        if (compareTemplate(config.pauseMediaPhrase, lowerText)) {
            commandTarget.playerCommand(PlayPauseType.PAUSE);
            return true;
        }
        if (compareTemplate(config.stopMediaPhrase, lowerText)) {
            commandTarget.playerStop();
            return true;
        }
        if (compareTemplate(config.fastForwardMediaProgressPhrase, lowerText)) {
            commandTarget.playerCommand(RewindFastforwardType.FASTFORWARD);
            return true;
        }
        if (compareTemplate(config.rewindMediaProgressPhrase, lowerText)) {
            commandTarget.playerCommand(RewindFastforwardType.REWIND);
            return true;
        }
        if (compareTemplate(config.nextMediaPhrase, lowerText)) {
            commandTarget.playerCommand(NextPreviousType.NEXT);
            return true;
        }
        if (compareTemplate(config.previousMediaPhrase, lowerText)) {
            commandTarget.playerCommand(NextPreviousType.PREVIOUS);
            return true;
        }
        return false;
    }

    private boolean interpretMediaTransfer(HABSpeakerConfig config, String lowerText) {
        String continueMediaOn = compareTemplateWithParameter(config.continueMediaOnPhrase, lowerText);
        if (!continueMediaOn.isBlank() && continueMediaOn(continueMediaOn)) {
            return true;
        }
        return compareTemplate(config.claimMediaPhrase, lowerText) && claimPlayback();
    }

    private boolean interpretMediaSearch(HABSpeakerConfig config, String lowerText) {
        String webAudioSearch = compareTemplateWithParameter(config.listenOnWebPhrase, lowerText);
        if (!webAudioSearch.isBlank()) {
            // assume a valid url
            var localResult = searchMediaFile(WEB_AUDIO_MEDIA_PATH, webAudioSearch);
            if (!localResult.isBlank()) {
                listenOnWebPlayer(localResult);
                return true;
            }
        }
        String webVideoSearch = compareTemplateWithParameter(config.watchOnWebPhrase, lowerText);
        if (!webVideoSearch.isBlank()) {
            // assume a valid url
            var localResult = searchMediaFile(WEB_VIDEO_MEDIA_PATH, webVideoSearch);
            if (!localResult.isBlank()) {
                watchOnWebPlayer(localResult);
                return true;
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
            return true;
        }
        String ytSearch = compareTemplateWithParameter(config.watchOnYouTubePhrase, lowerText);
        if (!ytSearch.isBlank()) {
            watchOnYouTube(ytSearch);
            return true;
        }
        return false;
    }

    private boolean interpretDropInCommands(HABSpeakerConfig config, String lowerText) {
        if (speakerIO.getDropIn() == null) {
            String speakerName = compareTemplateWithParameter(config.startDropInPhrase, lowerText);
            if (!speakerName.isBlank()) {
                var optionalTargetSpeaker = ioManager.getSpeakerConnections().stream() //
                        .filter(filterTargetSpeaker(speakerName)) //
                        .findAny();
                if (optionalTargetSpeaker.isPresent()) {
                    speakerIO.dropIn(optionalTargetSpeaker.get());
                    return true;
                }
            }
        } else {
            if (compareTemplate(config.stopDropInPhrase, lowerText)) {
                speakerIO.dropIn(null);
                return true;
            }
        }
        return false;
    }

    private boolean claimPlayback() {
        var sourceSpeaker = ioManager.getSpeakerConnections().stream().filter(filterSpeakerNotStopped())
                .sorted(sortPlayingFirst()).findAny().orElse(null);
        var sourceMediaState = sourceSpeaker != null ? sourceSpeaker.getMediaState() : null;
        if (sourceSpeaker != null && sourceMediaState != null) {
            var provider = sourceMediaState.provider;
            if (provider != null) {
                // another speaker is playing something
                if (sourceMediaState.provider != HABSpeakerIO.MediaProvider.SPOTIFY) {
                    // Stop media on the source speaker
                    sourceSpeaker.playerStop();
                    // Start media on the current speaker
                    speakerIO.playerStart(new HABSpeakerIO.StartMediaMessage(provider, sourceMediaState.mediaId,
                            sourceMediaState.playlistId, sourceMediaState.playlistIndex,
                            sourceMediaState.currentSecond));
                } else {
                    // For providers that support media claim by itself
                    // Claim media on current speaker
                    speakerIO.playerClaim(provider);
                }
                return true;
            }
        } else if (isPlayingOnSpotify()) {
            // another spotify connected device is playing, claim its playback
            speakerIO.playerClaim(HABSpeakerIO.MediaProvider.SPOTIFY);
            return true;
        }
        return false;
    }

    private boolean continueMediaOn(String continueMediaOn) {
        var optionalTargetSpeaker = ioManager.getSpeakerConnections().stream()
                .filter(filterTargetSpeaker(continueMediaOn)).findAny();
        if (optionalTargetSpeaker.isPresent()) {
            var targetSpeaker = optionalTargetSpeaker.get();
            // Prioritize current speaker media state
            var mediaSourceSpeaker = speakerIO;
            var mediaState = speakerIO.getMediaState();
            if (mediaState == null) {
                // Search for another speaker currently playing media
                mediaSourceSpeaker = ioManager.getSpeakerConnections().stream() //
                        .filter(filterSpeakerNotStopped()) //
                        .sorted(sortPlayingFirst()) //
                        .findAny().orElse(null);
                if (mediaSourceSpeaker != null) {
                    mediaState = mediaSourceSpeaker.getMediaState();
                }
            }
            if (mediaState != null) {
                var provider = mediaState.provider;
                if (provider != null) {
                    if (provider != HABSpeakerIO.MediaProvider.SPOTIFY) {
                        // Stop media on the source speaker
                        mediaSourceSpeaker.playerStop();
                        // Start media on the target speaker
                        targetSpeaker.playerStart(new HABSpeakerIO.StartMediaMessage(provider, mediaState.mediaId,
                                mediaState.playlistId, mediaState.playlistIndex, mediaState.currentSecond));
                    } else {
                        // For providers that support media claim by itself
                        targetSpeaker.playerClaim(provider);
                    }
                    return true;
                }
            } else if (isPlayingOnSpotify()) {
                // another spotify connected device is playing, claim playback on target speaker
                targetSpeaker.playerClaim(HABSpeakerIO.MediaProvider.SPOTIFY);
                return true;
            }
        }
        return false;
    }

    private Predicate<HABSpeakerIO> filterSpeakerNotStopped() {
        return io -> {
            var mediaState = io.getMediaState();
            return mediaState != null && (mediaState.playbackState == HABSpeakerIO.PlaybackStates.PLAYING
                    || mediaState.playbackState == HABSpeakerIO.PlaybackStates.PAUSED);
        };
    }

    private Comparator<HABSpeakerIO> sortPlayingFirst() {
        return (a, b) -> {
            var aMediaState = a.getMediaState();
            var bMediaState = b.getMediaState();
            if (aMediaState != null && bMediaState != null
                    && aMediaState.playbackState == HABSpeakerIO.PlaybackStates.PLAYING
                    && bMediaState.playbackState == HABSpeakerIO.PlaybackStates.PLAYING) {
                return 0;
            }
            return aMediaState != null && aMediaState.playbackState == HABSpeakerIO.PlaybackStates.PLAYING ? 1 : -1;
        };
    }

    private Predicate<HABSpeakerIO> filterTargetSpeaker(String speakerName) {
        return io -> {
            var handler = io.getThingHandler();
            if (handler == null) {
                return false;
            }
            return speakerName.equalsIgnoreCase(handler.getLabel()) || //
            speakerName.equalsIgnoreCase(handler.getLocationLabel());
        };
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

    /**
     * Try to search on a json media.
     * 
     * @param mediaPath path of the media file
     * @param name key on the media file
     * @return value for the provided name on the media file or empty
     */
    private String searchMediaFile(Path mediaPath, String name) {
        var mediaFile = mediaPath.toFile();
        if (mediaFile.exists()) {
            try {
                var mediaMap = new ObjectMapper().readValue(mediaFile, new TypeReference<HashMap<String, String>>() {
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

    /**
     * Try to resolve search from local media json file for YouTube or fallback to YouTube Search API,
     * and play it on the speaker
     * 
     * @param search the text to search
     */
    public void watchOnYouTube(String search) {
        // assume a valid YouTube video id or a list id prefixed by 'playlist:'
        var localResult = searchMediaFile(YOUTUBE_MEDIA_PATH, search);
        if (!localResult.isBlank()) {
            if (localResult.startsWith("playlist:")) {
                speakerIO.playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.YOUTUBE, null,
                        localResult.replace("playlist:", ""), 0, 0));
            } else {
                speakerIO.playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.YOUTUBE,
                        localResult, null, 0, 0));
            }
        } else {
            searchIdAndWatchOnYouTube(search);
        }
    }

    /**
     * Try to resolve search from YouTube Search API,
     * and play it on the speaker
     * 
     * @param search the text to search
     */
    public void searchIdAndWatchOnYouTube(String search) {
        if (search.isBlank()) {
            logger.warn("Search is blank");
            return;
        }
        try {
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
                    var playlistId = getYoutubeChannelUploadsPlaylist(searchItem.id.channelId);
                    speakerIO.playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.YOUTUBE, null,
                            playlistId, 0, 0));
                    return;
                case "youtube#playlist":
                    speakerIO.playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.YOUTUBE, null,
                            searchItem.id.playlistId, 0, 0));
                    return;
                case "youtube#video":
                    speakerIO.playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.YOUTUBE,
                            searchItem.id.videoId, null, 0, 0));
                    return;
                default:
                    throw new IllegalStateException("YouTube returns an unsupported item type");
            }
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

    private boolean isPlayingOnSpotify() {
        var spotifyToken = configProvider.getSpotifyToken();
        if (spotifyToken.isBlank()) {
            return false;
        }
        try {
            var response = httpClient.newRequest("https://api.spotify.com/v1/me/player/currently-playing") //
                    .header(HttpHeader.AUTHORIZATION, "Bearer " + spotifyToken) //
                    .header(HttpHeader.ACCEPT, "application/json") //
                    .header(HttpHeader.CONTENT_TYPE, "application/json").send();
            var responseStatus = response.getStatus();
            return responseStatus != 204 && responseStatus <= 399;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Error while calling spotify API: {}", e.getMessage());
        }
        return false;
    }

    private void listenOnSpotify(SpotifySearchType type, String name) {
        if (name.isBlank()) {
            logger.warn("Search is blank");
            return;
        }
        if (configProvider.getSpotifyToken().isBlank()) {
            logger.warn("Missing spotify token");
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
        speakerIO.playerStart(
                new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.SPOTIFY, spotifyUri, null, 0, 0));
    }

    private void listenOnWebPlayer(String url) {
        speakerIO
                .playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.WEB_AUDIO, url, null, 0, 0));
    }

    private void watchOnWebPlayer(String url) {
        speakerIO
                .playerStart(new HABSpeakerIO.StartMediaMessage(HABSpeakerIO.MediaProvider.WEB_VIDEO, url, null, 0, 0));
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
        @Nullable
        public SpotifyTracks tracks;
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
