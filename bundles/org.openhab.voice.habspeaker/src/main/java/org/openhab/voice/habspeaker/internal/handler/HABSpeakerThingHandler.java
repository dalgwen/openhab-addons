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

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SINK_VOLUME_CHANNEL;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerThingConfig;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOHandler;
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
    private HABSpeakerThingConfig config = new HABSpeakerThingConfig();
    private @Nullable HABSpeakerIO speakerIO = null;
    private @Nullable Integer sinkVolume = null;

    public HABSpeakerThingHandler(Thing thing) {
        super(thing);
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

    public void updateStatus() {
        updateStatus(speakerIO == null ? ThingStatus.OFFLINE : ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        handleCommand(channelUID.getId(), command);
    }

    public void handleCommand(String channelId, Command command) {
        try {
            var deviceIO = this.speakerIO;
            if (deviceIO == null) {
                logger.warn("speaker {} is not connected", getSpeakerId());
                return;
            }
            switch (channelId) {
                case SINK_VOLUME_CHANNEL:
                    if (command instanceof RefreshType) {
                        onSinkVolumeUpdate(sinkVolume);
                        return;
                    }
                    if (command instanceof DecimalType) {
                        sinkVolume = ((DecimalType) command).intValue();
                        deviceIO.setSinkVolume(sinkVolume);
                        return;
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
    }

    public void onSinkVolumeUpdate(int volume) {
        sinkVolume = volume;
        if (isLinked(SINK_VOLUME_CHANNEL)) {
            updateState(SINK_VOLUME_CHANNEL, new PercentType(volume));
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
