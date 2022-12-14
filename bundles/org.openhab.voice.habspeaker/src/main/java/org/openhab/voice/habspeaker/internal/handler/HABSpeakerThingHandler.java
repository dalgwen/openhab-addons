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
package org.openhab.voice.habspeaker.internal.handler;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerThingConfig;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOHandler;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerThingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerThingHandler extends BaseThingHandler implements HABSpeakerIOHandler {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerThingHandler.class);
    private final HABSpeakerIOManager ioManager;
    private final HABSpeakerConfigProvider globalConfigProvider;
    private HABSpeakerThingConfig config = new HABSpeakerThingConfig();
    private @Nullable HABSpeakerIO speakerIO;
    private @Nullable Integer sinkVolume;
    private @Nullable Integer mediaVolume;
    private String youtubeMediaId = "";
    private String spotifyMediaId = "";
    private String videoMediaUrl = "";
    private String audioMediaUrl = "";

    public HABSpeakerThingHandler(Thing thing, HABSpeakerIOManager ioManager, HABSpeakerConfigProvider configProvider) {
        super(thing);
        this.ioManager = ioManager;
        this.globalConfigProvider = configProvider;
    }

    @Override
    public void initialize() {
        this.config = getConfigAs(HABSpeakerThingConfig.class);
        updateStatus();
        if (speakerIO != null) {
            this.sinkVolume = speakerIO.getSinkVolume();
        }
    }

    public void setSpeakerIO(@Nullable HABSpeakerIO speakerIO) {
        this.speakerIO = speakerIO;
        if (speakerIO != null) {
            speakerIO.setThingHandler(this);
        }
    }

    public void dropIn(String speakerId) {
        var anotherSpeaker = ioManager.getSpeakerConnection(speakerId);
        if (anotherSpeaker == null || speakerIO == null) {
            logger.warn("Speaker not available");
            return;
        }
        try {
            speakerIO.dropIn(anotherSpeaker);
            try {
                anotherSpeaker.dropIn(speakerIO);
            } catch (IllegalStateException e) {
                speakerIO.dropIn(null);
                throw e;
            }
            if (isLinked(DROP_IN_CHANNEL)) {
                updateState(DROP_IN_CHANNEL, StringType.valueOf(speakerId));
            }
        } catch (IllegalStateException e) {
            logger.warn("Unable to drop-in: {}", e.getMessage());
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        // forces a disconnection so the speaker reconnects with the new config
        if (speakerIO != null) {
            speakerIO.disconnect();
        }
        super.handleConfigurationUpdate(configurationParameters);
    }

    public HABSpeakerThingConfig getSpeakerConfig() {
        return config;
    }

    @Override
    public @Nullable String getLabel() {
        return getThing().getLabel();
    }

    @Override
    public String getSpotifyToken() {
        return globalConfigProvider.getSpotifyToken();
    }

    public void updateStatus() {
        var newStatus = speakerIO == null ? ThingStatus.OFFLINE : ThingStatus.ONLINE;
        if (newStatus == ThingStatus.OFFLINE && getThing().getStatus() == ThingStatus.ONLINE) {
            cleanChannels();
        }
        updateStatus(newStatus);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        handleCommand(channelUID.getId(), command);
    }

    public void handleCommand(String channelId, Command command) {
        try {
            var speakerIO = this.speakerIO;
            if (speakerIO == null) {
                logger.warn("speaker {} is not connected", getSpeakerId());
                return;
            }
            switch (channelId) {
                case SINK_VOLUME_CHANNEL:
                    if (command instanceof RefreshType) {
                        if (sinkVolume != null) {
                            onSinkVolumeUpdate(sinkVolume);
                        }
                        return;
                    }
                    if (command instanceof DecimalType) {
                        sinkVolume = ((DecimalType) command).intValue();
                        speakerIO.setSinkVolume(sinkVolume);
                        return;
                    }
                    break;
                case MEDIA_VOLUME_CHANNEL:
                    if (command instanceof RefreshType) {
                        if (mediaVolume != null) {
                            onMediaVolumeUpdate(mediaVolume);
                        }
                        return;
                    }
                    if (command instanceof DecimalType) {
                        mediaVolume = ((DecimalType) command).intValue();
                        speakerIO.setMediaVolume(mediaVolume);
                        return;
                    }
                    break;
                case SPOT_CHANNEL:
                    if (command instanceof RefreshType) {
                        if (isLinked(SPOT_CHANNEL)) {
                            updateState(SPOT_CHANNEL, OnOffType.OFF);
                        }
                        return;
                    }
                    if (OnOffType.valueOf(command.toFullString()) == OnOffType.ON) {
                        speakerIO.spot();
                        if (isLinked(SPOT_CHANNEL)) {
                            updateState(SPOT_CHANNEL, OnOffType.OFF);
                        }
                    }
                    break;
                case DROP_IN_CHANNEL:
                    if (command instanceof RefreshType) {
                        var dropInSpeaker = speakerIO.getDropIn();
                        if (dropInSpeaker != null) {
                            updateState(DROP_IN_CHANNEL, StringType.valueOf(dropInSpeaker.getId()));
                        } else {
                            updateState(DROP_IN_CHANNEL, OnOffType.OFF);
                        }
                        return;
                    } else {
                        var commandText = command.toFullString();
                        if (commandText.isBlank() || commandText.equals("OFF") || commandText.equals("NULL")) {
                            speakerIO.dropIn(null);
                        } else if (!commandText.isBlank()) {
                            dropIn(commandText);
                        }
                    }
                    break;
                case MEDIA_CURRENT_SECOND_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    }
                    if (command instanceof DecimalType) {
                        speakerIO.playerSeekToSecond(((DecimalType) command).longValue());
                    }
                    break;
                case MEDIA_TOTAL_SECONDS_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    }
                    break;
                case MEDIA_PROGRESS_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    }
                    if (command instanceof PercentType) {
                        speakerIO.playerSeekToPercent(((PercentType) command).intValue());
                    }
                    break;
                case MEDIA_CONTROL_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    }
                    if (command instanceof PlayPauseType) {
                        speakerIO.playerCommand((PlayPauseType) command);
                    }
                    if (command instanceof RewindFastforwardType) {
                        speakerIO.playerCommand((RewindFastforwardType) command);
                    }
                    if (command instanceof NextPreviousType) {
                        speakerIO.playerCommand((NextPreviousType) command);
                    }
                    break;
                case YOUTUBE_ID_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        playMedia(speakerIO, HABSpeakerIO.MediaProvider.YOUTUBE, command.toFullString());
                    }
                    break;
                case YOUTUBE_SEARCH_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        speakerIO.getLanguageInterpreter().watchOnYouTube(command.toFullString());
                    }
                    break;
                case SPOTIFY_ID_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        playMedia(speakerIO, HABSpeakerIO.MediaProvider.SPOTIFY, command.toFullString());
                    }
                    break;
                case SPOTIFY_SEARCH_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        speakerIO.getLanguageInterpreter().listenTrackOnSpotify(command.toFullString());
                    }
                    break;
                case WEB_AUDIO_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        playMedia(speakerIO, HABSpeakerIO.MediaProvider.WEB_AUDIO, command.toFullString());
                    }
                    break;
                case WEB_VIDEO_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        playMedia(speakerIO, HABSpeakerIO.MediaProvider.WEB_VIDEO, command.toFullString());
                    }
                    break;
                default:
                    logger.warn("Unsupported channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
    }

    private void playMedia(HABSpeakerIO speakerIO, HABSpeakerIO.MediaProvider provider, String id) {
        if (id.isBlank() || "NULL".equals(id)) {
            speakerIO.playerStop();
        } else {
            speakerIO.playerStart(provider, id);
        }
    }

    public void onMediaVolumeUpdate(int volume) {
        mediaVolume = volume;
        if (isLinked(MEDIA_VOLUME_CHANNEL)) {
            updateState(MEDIA_VOLUME_CHANNEL, new PercentType(volume));
        }
    }

    public void onSinkVolumeUpdate(int volume) {
        sinkVolume = volume;
        if (isLinked(SINK_VOLUME_CHANNEL)) {
            updateState(SINK_VOLUME_CHANNEL, new PercentType(volume));
        }
    }

    @Override
    public void onMediaStateUpdate(String provider, String mediaId, int volume, long currentSecond, long totalSeconds,
            HABSpeakerIO.PlaybackStates playbackState) {
        String youtubeMediaId = "";
        String spotifyMediaId = "";
        String videoMediaUrl = "";
        String audioMediaUrl = "";
        switch (provider) {
            case "youtube":
                youtubeMediaId = mediaId;
                break;
            case "spotify":
                spotifyMediaId = mediaId;
                break;
            case "web-video":
                videoMediaUrl = mediaId;
                break;
            case "web-music":
                audioMediaUrl = mediaId;
                break;
        }
        this.youtubeMediaId = youtubeMediaId;
        this.spotifyMediaId = spotifyMediaId;
        this.videoMediaUrl = videoMediaUrl;
        this.audioMediaUrl = audioMediaUrl;
        if (isLinked(YOUTUBE_ID_CHANNEL)) {
            updateState(YOUTUBE_ID_CHANNEL, youtubeMediaId.isEmpty() ? UnDefType.NULL : new StringType(youtubeMediaId));
        }
        if (isLinked(SPOTIFY_ID_CHANNEL)) {
            updateState(SPOTIFY_ID_CHANNEL, spotifyMediaId.isEmpty() ? UnDefType.NULL : new StringType(spotifyMediaId));
        }
        if (isLinked(WEB_VIDEO_CHANNEL)) {
            updateState(WEB_VIDEO_CHANNEL, videoMediaUrl.isEmpty() ? UnDefType.NULL : new StringType(videoMediaUrl));
        }
        if (isLinked(WEB_AUDIO_CHANNEL)) {
            updateState(WEB_AUDIO_CHANNEL, audioMediaUrl.isEmpty() ? UnDefType.NULL : new StringType(audioMediaUrl));
        }
        if (isLinked(MEDIA_CURRENT_SECOND_CHANNEL)) {
            updateState(MEDIA_CURRENT_SECOND_CHANNEL, new DecimalType(currentSecond));
        }
        if (isLinked(MEDIA_TOTAL_SECONDS_CHANNEL)) {
            updateState(MEDIA_TOTAL_SECONDS_CHANNEL, new DecimalType(totalSeconds));
        }
        if (isLinked(MEDIA_PROGRESS_CHANNEL)) {
            updateState(MEDIA_PROGRESS_CHANNEL,
                    new PercentType((int) ((((double) currentSecond) / ((double) totalSeconds)) * 100.0)));
        }
        if (isLinked(MEDIA_CONTROL_CHANNEL)) {
            updateState(MEDIA_CONTROL_CHANNEL,
                    playbackState == HABSpeakerIO.PlaybackStates.PLAYING ? PlayPauseType.PLAY : PlayPauseType.PAUSE);
        }
        if (isLinked(MEDIA_VOLUME_CHANNEL)) {
            updateState(MEDIA_VOLUME_CHANNEL, new PercentType(volume));
        }
    }

    private void cleanChannels() {
        if (isLinked(YOUTUBE_ID_CHANNEL)) {
            updateState(YOUTUBE_ID_CHANNEL, UnDefType.NULL);
        }
        if (isLinked(SPOTIFY_ID_CHANNEL)) {
            updateState(SPOTIFY_ID_CHANNEL, UnDefType.NULL);
        }
        if (isLinked(WEB_VIDEO_CHANNEL)) {
            updateState(WEB_VIDEO_CHANNEL, UnDefType.NULL);
        }
        if (isLinked(WEB_AUDIO_CHANNEL)) {
            updateState(WEB_AUDIO_CHANNEL, UnDefType.NULL);
        }
    }

    public @Nullable Integer getSinkVolume() {
        return sinkVolume;
    }

    @Override
    public void dispose() {
        var deviceIO = this.speakerIO;
        if (deviceIO != null) {
            deviceIO.setThingHandler(null);
        }
        super.dispose();
    }

    public String getSpeakerId() {
        return this.thing.getUID().getId();
    }
}
