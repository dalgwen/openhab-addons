/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.habspeaker.internal.handler;

import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.AUDIO_SEARCH_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.MEDIA_CONTROL_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.MEDIA_CURRENT_SECOND_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.MEDIA_PROGRESS_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.MEDIA_TOTAL_SECONDS_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.MEDIA_VOLUME_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.PLAY_AUDIO_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.PLAY_VIDEO_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.SINK_VOLUME_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.SOURCE_VOLUME_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.SPOT_CHANNEL;
import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.VIDEO_SEARCH_CHANNEL;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.habspeaker.internal.config.HABSpeakerThingConfig;
import org.openhab.binding.habspeaker.internal.io.HABSpeakerIOConnection;
import org.openhab.binding.habspeaker.internal.io.HABSpeakerIOHandler;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
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
    private final ItemRegistry itemRegistry;
    private HABSpeakerThingConfig config = new HABSpeakerThingConfig();
    private @Nullable HABSpeakerIOConnection speakerIO;
    private int sinkVolume;
    private int sourceVolume;
    private int mediaVolume;

    public HABSpeakerThingHandler(Thing thing, ItemRegistry itemRegistry) {
        super(thing);
        this.itemRegistry = itemRegistry;
    }

    @Override
    public void initialize() {
        this.config = getConfigAs(HABSpeakerThingConfig.class);
        sinkVolume = this.config.sinkVolume;
        sourceVolume = this.config.sourceVolume;
        mediaVolume = this.config.mediaVolume;
        updateStatus();
    }

    public void setSpeakerIO(@Nullable HABSpeakerIOConnection speakerIO) {
        this.speakerIO = speakerIO;
        if (speakerIO != null) {
            speakerIO.setSinkVolume(this.sinkVolume);
            speakerIO.setSourceVolume(this.sourceVolume);
            speakerIO.setMediaVolume(this.mediaVolume);
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
    public @Nullable String getLocationLabel() {
        if (!config.location.isBlank()) {
            try {
                return itemRegistry.getItem(config.location).getLabel();
            } catch (ItemNotFoundException e) {
                logger.warn("location item {} for speaker {} not found", getSpeakerId(), config.location);
            }
        }
        return null;
    }

    public void updateStatus() {
        var newStatus = speakerIO == null ? ThingStatus.OFFLINE : ThingStatus.ONLINE;
        if (newStatus == ThingStatus.OFFLINE && getThing().getStatus() == ThingStatus.ONLINE) {
            cleanChannels();
        }
        updateStatus(newStatus);
        if (getThing().getStatus() == ThingStatus.ONLINE && speakerIO != null) {
            if (speakerIO.getSinkVolume() != -1) {
                if (isLinked(SINK_VOLUME_CHANNEL)) {
                    updateState(SINK_VOLUME_CHANNEL, new PercentType(speakerIO.getSinkVolume()));
                }
            }
            if (speakerIO.getSourceVolume() != -1) {
                if (isLinked(SOURCE_VOLUME_CHANNEL)) {
                    updateState(SOURCE_VOLUME_CHANNEL, new PercentType(speakerIO.getSourceVolume()));
                }
            }
            if (speakerIO.getMediaVolume() != -1) {
                if (isLinked(MEDIA_VOLUME_CHANNEL)) {
                    updateState(MEDIA_VOLUME_CHANNEL, new PercentType(mediaVolume));
                }
            }
        }
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
                case SINK_VOLUME_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        if (isLinked(SINK_VOLUME_CHANNEL)) {
                            updateState(SINK_VOLUME_CHANNEL, new PercentType(sinkVolume));
                        }
                        return;
                    }
                    if (command instanceof DecimalType volumeCommand) {
                        sinkVolume = volumeCommand.intValue();
                        if (sinkVolume != speakerIO.getSinkVolume()) {
                            speakerIO.setSinkVolume(sinkVolume);
                        }
                        if (isLinked(SINK_VOLUME_CHANNEL)) {
                            updateState(SINK_VOLUME_CHANNEL, new PercentType(sinkVolume));
                        }
                    }
                }
                case SOURCE_VOLUME_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        if (isLinked(SOURCE_VOLUME_CHANNEL)) {
                            updateState(SOURCE_VOLUME_CHANNEL, new PercentType(sourceVolume));
                        }
                        return;
                    }
                    if (command instanceof DecimalType volumeCommand) {
                        sourceVolume = volumeCommand.intValue();
                        if (sourceVolume != speakerIO.getSourceVolume()) {
                            speakerIO.setSourceVolume(sourceVolume);
                        }
                        if (isLinked(SOURCE_VOLUME_CHANNEL)) {
                            updateState(SOURCE_VOLUME_CHANNEL, new PercentType(sourceVolume));
                        }
                    }
                }
                case MEDIA_VOLUME_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        if (isLinked(MEDIA_VOLUME_CHANNEL)) {
                            updateState(MEDIA_VOLUME_CHANNEL, new PercentType(mediaVolume));
                        }
                        return;
                    }
                    if (command instanceof DecimalType) {
                        mediaVolume = ((DecimalType) command).intValue();
                        speakerIO.setMediaVolume(mediaVolume);
                        if (isLinked(MEDIA_VOLUME_CHANNEL)) {
                            updateState(MEDIA_VOLUME_CHANNEL, new PercentType(mediaVolume));
                        }
                    }
                }
                case SPOT_CHANNEL -> {
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
                }
                case MEDIA_CURRENT_SECOND_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        return;
                    }
                    if (command instanceof DecimalType) {
                        speakerIO.playerSeekToSecond(((DecimalType) command).longValue());
                    }
                }
                case MEDIA_TOTAL_SECONDS_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        return;
                    }
                }
                case MEDIA_PROGRESS_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        return;
                    }
                    if (command instanceof PercentType) {
                        speakerIO.playerSeekToPercent(((PercentType) command).intValue());
                    }
                }
                case MEDIA_CONTROL_CHANNEL -> {
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
                }
                case PLAY_AUDIO_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        return;
                    }
                    playMedia(speakerIO, HABSpeakerIOConnection.MediaProvider.AUDIO_PLAYER, command.toFullString());
                }
                case PLAY_VIDEO_CHANNEL -> {
                    if (command instanceof RefreshType) {
                        return;
                    }
                    playMedia(speakerIO, HABSpeakerIOConnection.MediaProvider.VIDEO_PLAYER, command.toFullString());
                }
                default -> logger.warn("Unsupported channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
    }

    private void playMedia(HABSpeakerIOConnection speakerIO, HABSpeakerIOConnection.MediaProvider provider, String id) {
        if (id.isBlank() || "NULL".equals(id)) {
            speakerIO.playerStop();
        } else {
            speakerIO.playerStart(new HABSpeakerIOConnection.StartMediaMessage(provider, id, 0));
        }
    }

    @Override
    public void onMediaStateUpdate(HABSpeakerIOConnection.MediaState mediaState, int volume) {
        String videoMediaUrl = "";
        String audioMediaUrl = "";
        if (mediaState.provider != null && mediaState.mediaId != null) {
            switch (mediaState.provider) {
                case VIDEO_PLAYER -> videoMediaUrl = mediaState.mediaId;
                case AUDIO_PLAYER -> audioMediaUrl = mediaState.mediaId;
            }
        }
        if (isLinked(PLAY_VIDEO_CHANNEL)) {
            updateState(PLAY_VIDEO_CHANNEL, videoMediaUrl.isEmpty() ? UnDefType.NULL : new StringType(videoMediaUrl));
        }
        if (isLinked(PLAY_AUDIO_CHANNEL)) {
            updateState(PLAY_AUDIO_CHANNEL, audioMediaUrl.isEmpty() ? UnDefType.NULL : new StringType(audioMediaUrl));
        }
        if (isLinked(MEDIA_CURRENT_SECOND_CHANNEL)) {
            updateState(MEDIA_CURRENT_SECOND_CHANNEL, new DecimalType(mediaState.currentSecond));
        }
        if (isLinked(MEDIA_TOTAL_SECONDS_CHANNEL)) {
            updateState(MEDIA_TOTAL_SECONDS_CHANNEL, new DecimalType(mediaState.totalSeconds));
        }
        if (isLinked(MEDIA_PROGRESS_CHANNEL)) {
            updateState(MEDIA_PROGRESS_CHANNEL, new PercentType(
                    (int) ((((double) mediaState.currentSecond) / ((double) mediaState.totalSeconds)) * 100.0)));
        }
        if (isLinked(MEDIA_CONTROL_CHANNEL)) {
            updateState(MEDIA_CONTROL_CHANNEL,
                    mediaState.playbackState == HABSpeakerIOConnection.PlaybackStates.PLAYING ? PlayPauseType.PLAY
                            : PlayPauseType.PAUSE);
        }
        if (isLinked(MEDIA_VOLUME_CHANNEL)) {
            updateState(MEDIA_VOLUME_CHANNEL, new PercentType(volume));
        }
    }

    @Override
    public void updateVideoSearchChannel(String searchText) {
        if (isLinked(VIDEO_SEARCH_CHANNEL)) {
            updateState(VIDEO_SEARCH_CHANNEL, new StringType(searchText));
        }
    }

    @Override
    public void updateMusicSearchChannel(String searchText) {
        if (isLinked(AUDIO_SEARCH_CHANNEL)) {
            updateState(AUDIO_SEARCH_CHANNEL, new StringType(searchText));
        }
    }

    private void cleanChannels() {
        if (isLinked(PLAY_VIDEO_CHANNEL)) {
            updateState(PLAY_VIDEO_CHANNEL, UnDefType.NULL);
        }
        if (isLinked(PLAY_AUDIO_CHANNEL)) {
            updateState(PLAY_AUDIO_CHANNEL, UnDefType.NULL);
        }
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
