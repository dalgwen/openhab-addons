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
package org.openhab.voice.habspeaker.internal.handler;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.AUDIO_SEARCH_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.DROP_IN_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.MEDIA_CONTROL_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.MEDIA_CURRENT_SECOND_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.MEDIA_PROGRESS_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.MEDIA_TOTAL_SECONDS_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.MEDIA_VOLUME_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.PLAY_AUDIO_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.PLAY_VIDEO_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SINK_VOLUME_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SPOT_CHANNEL;
import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.VIDEO_SEARCH_CHANNEL;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
import org.openhab.voice.habspeaker.internal.config.HABSpeakerThingConfig;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOClient;
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
    private final ItemRegistry itemRegistry;
    private HABSpeakerThingConfig config = new HABSpeakerThingConfig();
    private @Nullable HABSpeakerIOClient speakerIO;
    private @Nullable Integer sinkVolume;
    private @Nullable Integer mediaVolume;

    public HABSpeakerThingHandler(Thing thing, ItemRegistry itemRegistry, HABSpeakerIOManager ioManager) {
        super(thing);
        this.ioManager = ioManager;
        this.itemRegistry = itemRegistry;
    }

    @Override
    public void initialize() {
        this.config = getConfigAs(HABSpeakerThingConfig.class);
        updateStatus();
        if (speakerIO != null) {
            this.sinkVolume = speakerIO.getSinkVolume();
        }
    }

    public void setSpeakerIO(@Nullable HABSpeakerIOClient speakerIO) {
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
                case PLAY_AUDIO_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        playMedia(speakerIO, HABSpeakerIOClient.MediaProvider.AUDIO_PLAYER, command.toFullString());
                    }
                    break;
                case PLAY_VIDEO_CHANNEL:
                    if (command instanceof RefreshType) {
                        return;
                    } else {
                        playMedia(speakerIO, HABSpeakerIOClient.MediaProvider.VIDEO_PLAYER, command.toFullString());
                    }
                    break;
                default:
                    logger.warn("Unsupported channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
    }

    private void playMedia(HABSpeakerIOClient speakerIO, HABSpeakerIOClient.MediaProvider provider, String id) {
        if (id.isBlank() || "NULL".equals(id)) {
            speakerIO.playerStop();
        } else {
            speakerIO.playerStart(new HABSpeakerIOClient.StartMediaMessage(provider, id, 0));
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
    public void onMediaStateUpdate(HABSpeakerIOClient.MediaState mediaState, int volume) {
        String videoMediaUrl = "";
        String audioMediaUrl = "";
        if (mediaState.provider != null && mediaState.mediaId != null) {
            switch (mediaState.provider) {
                case VIDEO_PLAYER:
                    videoMediaUrl = mediaState.mediaId;
                    break;
                case AUDIO_PLAYER:
                    audioMediaUrl = mediaState.mediaId;
                    break;
            }
        }
        if (isLinked(PLAY_VIDEO_CHANNEL)) {
            updateState(PLAY_VIDEO_CHANNEL, videoMediaUrl.isEmpty() ? UnDefType.NULL : new StringType(videoMediaUrl));
        }
        if (isLinked(PLAY_AUDIO_CHANNEL)) {
            updateState(PLAY_AUDIO_CHANNEL, audioMediaUrl.isEmpty() ? UnDefType.NULL : new StringType(audioMediaUrl));
        }
        if (isLinked(MEDIA_CURRENT_SECOND_CHANNEL)) {
            updateState(MEDIA_CURRENT_SECOND_CHANNEL, new DecimalType((Number) mediaState.currentSecond));
        }
        if (isLinked(MEDIA_TOTAL_SECONDS_CHANNEL)) {
            updateState(MEDIA_TOTAL_SECONDS_CHANNEL, new DecimalType((Number) mediaState.totalSeconds));
        }
        if (isLinked(MEDIA_PROGRESS_CHANNEL)) {
            updateState(MEDIA_PROGRESS_CHANNEL, new PercentType(
                    (int) ((((double) mediaState.currentSecond) / ((double) mediaState.totalSeconds)) * 100.0)));
        }
        if (isLinked(MEDIA_CONTROL_CHANNEL)) {
            updateState(MEDIA_CONTROL_CHANNEL,
                    mediaState.playbackState == HABSpeakerIOClient.PlaybackStates.PLAYING ? PlayPauseType.PLAY
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
