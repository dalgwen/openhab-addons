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
package org.openhab.voice.habspeaker.internal.config;

import static java.util.stream.Collectors.joining;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.HttpMethod;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.openhab.core.OpenHAB;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.voice.TTSService;
import org.openhab.core.voice.Voice;
import org.openhab.core.voice.VoiceManager;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerConfigProvider} class defines the speaker configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = { HABSpeakerConfigProvider.class,
        ConfigOptionProvider.class }, configurationPid = SERVICE_PID, property = Constants.SERVICE_PID + "="
                + SERVICE_PID)
@ConfigurableService(category = SERVICE_CATEGORY, label = SERVICE_NAME, description_uri = SERVICE_CATEGORY + ":"
        + SERVICE_ID)
@NonNullByDefault
public class HABSpeakerConfigProvider implements ConfigOptionProvider {
    protected static final String SPEAKER_CONFIG_URI = "thing-type:habspeaker:speaker";
    public static final String RUSTPOTTER_WEB_KS_ID = "habspeaker::rustpotter_web::ks";
    public static final String HABSPEAKER_FOLDER = Path.of(OpenHAB.getUserDataFolder(), "habspeaker").toString();
    private static final String MEDIA_FOLDER = Path.of(HABSPEAKER_FOLDER, "media").toString();
    private static final String KS_FOLDER = Path.of(HABSPEAKER_FOLDER, "ks").toString();
    public static final String RUSTPOTTER_FOLDER = Path.of(KS_FOLDER, "rustpotter").toString();
    public static final String RUSTPOTTER_ADDON_FOLDER = Path.of(OpenHAB.getUserDataFolder(), "rustpotter").toString();
    private static final String CREDENTIALS_FOLDER = Path.of(HABSPEAKER_FOLDER, "credentials").toString();
    private static final String SPOTIFY_REFRESH_TOKEN_FILE = Path.of(CREDENTIALS_FOLDER, "spotify_refresh_token")
            .toString();
    public static final Path WEB_VIDEO_MEDIA_PATH = Path.of(MEDIA_FOLDER, "web-video.json");
    public static final Path WEB_AUDIO_MEDIA_PATH = Path.of(MEDIA_FOLDER, "web-audio.json");
    public static final Path SPOTIFY_MEDIA_PATH = Path.of(MEDIA_FOLDER, "spotify.json");
    public static final Path YOUTUBE_MEDIA_PATH = Path.of(MEDIA_FOLDER, "youtube.json");
    static {
        Logger logger = LoggerFactory.getLogger(HABSpeakerConfigProvider.class);
        ensureDir("root", HABSPEAKER_FOLDER, logger);
        ensureDir("media", MEDIA_FOLDER, logger);
        ensureDir("ks", KS_FOLDER, logger);
        ensureDir("rustpotter", RUSTPOTTER_FOLDER, logger);
        ensureDir("credentials", CREDENTIALS_FOLDER, logger);
    }

    private final Logger logger = LoggerFactory.getLogger(HABSpeakerConfigProvider.class);
    private final VoiceManager voiceManager;
    private final LocaleProvider localeProvider;
    private final HttpClient httpClient;
    private final HABSpeakerVoiceConfigHelper voiceConfigHelper;
    private String spotifyToken = "";
    private String spotifyRefreshToken = "";
    private @Nullable ScheduledFuture<?> spotifyRenewTask = null;
    private HABSpeakerConfig config = new HABSpeakerConfig();
    private final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("habspeaker");
    Set<HABSpeakerConfigProviderListener> listeners = new HashSet<>();

    @Activate
    public HABSpeakerConfigProvider(@Reference VoiceManager voiceManager, @Reference LocaleProvider localeProvider,
            final @Reference HttpClientFactory httpClientFactory,
            @Reference HABSpeakerVoiceConfigHelper voiceConfigHelper) {
        this.voiceManager = voiceManager;
        this.localeProvider = localeProvider;
        this.voiceConfigHelper = voiceConfigHelper;
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.spotifyRefreshToken = loadSpotifyRefreshToken();
    }

    private String loadSpotifyRefreshToken() {
        var filePath = Path.of(SPOTIFY_REFRESH_TOKEN_FILE);
        if (filePath.toFile().exists()) {
            logger.debug("found refresh token for spotify");
            try (var lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                return lines.findAny().orElse("");
            } catch (IOException e) {
                logger.warn("Unable to load spotify refresh token");
            }
        }
        return "";
    }

    public HABSpeakerConfig getConfig() {
        return config;
    }

    public String getSpotifyToken() {
        return spotifyToken;
    }

    /**
     *
     * @return the keyword configured in "System Settings/Voice"
     */
    public String getSystemKeyword() {
        return voiceConfigHelper.getKeyword();
    }

    @Activate
    public void activate(Map<String, Object> configMap) {
        modified(configMap);
    }

    @Modified
    public void modified(Map<String, Object> configMap) {
        var config = new Configuration(configMap).as(HABSpeakerConfig.class);
        this.config = config;
        renewSpotifyToken();
        listeners.forEach(listener -> listener.onGlobalConfigUpdate(config));
    }

    public synchronized void onSpotifyToken(String accessToken, String refreshToken, int expireSeconds) {
        var spotifyRenewTask = this.spotifyRenewTask;
        if (spotifyRenewTask != null) {
            spotifyRenewTask.cancel(true);
            this.spotifyRenewTask = null;
        }
        spotifyToken = accessToken;
        spotifyRefreshToken = refreshToken;
        saveSpotifyRefreshToken(refreshToken);
        var nextRenewSeconds = Double.valueOf(expireSeconds * 0.9).longValue();
        logger.debug("next spotify token renew in {} seconds", nextRenewSeconds);
        this.spotifyRenewTask = scheduler.schedule(this::renewSpotifyToken, 120, TimeUnit.SECONDS);
        listeners.forEach(listener -> listener.onSpotifyTokenUpdate(spotifyToken));
    }

    private void saveSpotifyRefreshToken(String refreshToken) {
        try {
            FileWriter writer = new FileWriter(Path.of(SPOTIFY_REFRESH_TOKEN_FILE).toFile(), false);
            writer.write(refreshToken);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            logger.warn("Unable to persist spotify refreshToken");
        }
    }

    private synchronized void renewSpotifyToken() {
        var spotifyRenewTask = this.spotifyRenewTask;
        if (spotifyRenewTask != null) {
            spotifyRenewTask.cancel(false);
            this.spotifyRenewTask = null;
        }
        String clientId = config.spotifyClientId;
        String refreshToken = spotifyRefreshToken;
        if (clientId.isBlank() || refreshToken.isBlank()) {
            spotifyToken = "";
            spotifyRefreshToken = "";
        } else {
            long nextRenewSeconds = 120;
            try {
                logger.debug("Renewing spotify token");
                Map<String, String> requestParams = new HashMap<>();
                requestParams.put("client_id", getConfig().spotifyClientId);
                requestParams.put("grant_type", "refresh_token");
                requestParams.put("refresh_token", refreshToken);
                String formData = requestParams.keySet().stream().map(k -> k + "=" + requestParams.get(k))
                        .collect(joining("&", "", ""));
                var tokenRes = httpClient.newRequest("https://accounts.spotify.com/api/token").method(HttpMethod.POST)
                        .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .content(new StringContentProvider(formData)).send();
                if (tokenRes.getStatus() == 200) {
                    ObjectMapper mapper = new ObjectMapper();
                    var tokenData = mapper.readValue(tokenRes.getContentAsString(),
                            new TypeReference<HashMap<String, Object>>() {
                            });
                    this.spotifyToken = tokenData.getOrDefault("access_token", "").toString();
                    this.spotifyRefreshToken = tokenData.getOrDefault("refresh_token", "").toString();
                    saveSpotifyRefreshToken(spotifyRefreshToken);
                    var expiresIn = Integer.parseInt(tokenData.getOrDefault("expires_in", 0).toString());
                    nextRenewSeconds = Double.valueOf(expiresIn * 0.9).longValue();
                } else {
                    logger.warn("spotify login failed with code: {}", tokenRes.getStatus());
                    logger.warn("spotify login fail: {}", tokenRes.getContentAsString());
                }
                if (tokenRes.getStatus() == 401) {
                    logger.warn("Spotify refresh token is not valid");
                    this.spotifyToken = "";
                    this.spotifyRefreshToken = "";
                }
            } catch (InterruptedException | TimeoutException | ExecutionException | JsonProcessingException e) {
                logger.warn("Unable to renew spotify token: ", e);
            }
            if (!spotifyToken.isBlank()) {
                listeners.forEach(listener -> listener.onSpotifyTokenUpdate(spotifyToken));
            }
            logger.debug("next spotify token renew in {} seconds", nextRenewSeconds);
            this.spotifyRenewTask = scheduler.schedule(this::renewSpotifyToken, nextRenewSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (context == null && SPEAKER_CONFIG_URI.equals(uri.toString())) {
            switch (param) {
                case "hli":
                    return voiceManager.getHLIs().stream()
                            .sorted((hli1, hli2) -> hli1.getLabel(locale).compareToIgnoreCase(hli2.getLabel(locale)))
                            .map(hli -> new ParameterOption(hli.getId(), hli.getLabel(locale)))
                            .collect(Collectors.toList());
                case "ks":
                    var clientKsServices = Stream
                            .of(new ParameterOption(RUSTPOTTER_WEB_KS_ID, "Rustpotter Web (Client Spotter)"));
                    var serverKsServices = voiceManager.getKSs().stream()
                            .sorted((ks1, ks2) -> ks1.getLabel(locale).compareToIgnoreCase(ks2.getLabel(locale)))
                            .map(ks -> new ParameterOption(ks.getId(), ks.getLabel(locale)));
                    return Stream.concat(clientKsServices, serverKsServices).collect(Collectors.toList());
                case "stt":
                    return voiceManager.getSTTs().stream()
                            .sorted((stt1, stt2) -> stt1.getLabel(locale).compareToIgnoreCase(stt2.getLabel(locale)))
                            .map(stt -> new ParameterOption(stt.getId(), stt.getLabel(locale)))
                            .collect(Collectors.toList());
                case "tts":
                    return voiceManager.getTTSs().stream()
                            .sorted((tts1, tts2) -> tts1.getLabel(locale).compareToIgnoreCase(tts2.getLabel(locale)))
                            .map(tts -> new ParameterOption(tts.getId(), tts.getLabel(locale)))
                            .collect(Collectors.toList());
                case "voice":
                    Locale nullSafeLocale = locale != null ? locale : localeProvider.getLocale();
                    return voiceManager.getAllVoices().stream().filter(v -> getVoiceTTS(v) != null)
                            .map(v -> new ParameterOption(v.getUID(),
                                    String.format("%s - %s - %s", getVoiceTTS(v).getLabel(nullSafeLocale),
                                            v.getLocale().getDisplayName(nullSafeLocale), v.getLabel())))
                            .collect(Collectors.toList());
            }
        }
        return null;
    }

    private @Nullable TTSService getVoiceTTS(Voice voice) {
        return voiceManager.getTTS(voice.getUID().split(":")[0]);
    }

    public void addListener(HABSpeakerConfigProviderListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HABSpeakerConfigProviderListener listener) {
        listeners.remove(listener);
    }

    @Deactivate
    public void deactivate() {
        if (spotifyRenewTask != null) {
            spotifyRenewTask.cancel(true);
        }
    }

    public interface HABSpeakerConfigProviderListener {
        void onSpotifyTokenUpdate(String accessToken);

        void onGlobalConfigUpdate(HABSpeakerConfig config);
    }

    private static void ensureDir(String name, String path, Logger logger) {
        File credentials = new File(path);
        if (!credentials.exists()) {
            if (credentials.mkdir()) {
                logger.info("habspeaker {} dir created {}", name, path);
            }
        }
    }
}
