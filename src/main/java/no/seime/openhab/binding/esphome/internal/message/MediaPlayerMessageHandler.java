/**
 * Copyright (c) 2023 Contributors to the Seime Openhab Addons project
 * Copyright (c) 2025 Contributors to the ESPHome binding extension
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.esphome.internal.message;

import static org.openhab.core.library.CoreItemFactory.STRING;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.esphome.api.*;
import no.seime.openhab.binding.esphome.internal.EntityTypes;
import no.seime.openhab.binding.esphome.internal.comm.ProtocolAPIError;
import no.seime.openhab.binding.esphome.internal.handler.ESPHomeHandler;

public class MediaPlayerMessageHandler
        extends AbstractMessageHandler<ListEntitiesMediaPlayerResponse, MediaPlayerStateResponse> {

    private final Logger logger = LoggerFactory.getLogger(MediaPlayerMessageHandler.class);

    public MediaPlayerMessageHandler(ESPHomeHandler handler) {
        super(handler);
    }

    @Override
    public void handleCommand(Channel channel, Command command, int key) throws ProtocolAPIError {
        MediaPlayerCommandRequest.Builder builder = MediaPlayerCommandRequest.newBuilder().setKey(key);

        String commandString = command.toString().toUpperCase();

        if ("PLAY".equals(commandString) || "ON".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PLAY_VALUE);
        } else if ("PAUSE".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PAUSE_VALUE);
        } else if ("STOP".equals(commandString) || "OFF".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_STOP_VALUE);
        } else if ("MUTE".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_MUTE_VALUE);
        } else if ("UNMUTE".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_UNMUTE_VALUE);
        } else if ("TOGGLE".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_TOGGLE_VALUE);
        } else if ("VOLUME_UP".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_VOLUME_UP_VALUE);
        } else if ("VOLUME_DOWN".equals(commandString)) {
            builder.setHasCommand(true);
            builder.setCommandValue(MediaPlayerCommand.MEDIA_PLAYER_COMMAND_VOLUME_DOWN_VALUE);
        } else if (command instanceof PercentType percent) {
            builder.setHasVolume(true);
            builder.setVolume(percent.floatValue() / 100.0f);
        } else {
            logger.debug("[{}] Unsupported media player command: {}", handler.getLogPrefix(), command);
            return;
        }

        handler.sendMessage(builder.build());
    }

    public void buildChannels(ListEntitiesMediaPlayerResponse rsp) {
        Configuration configuration = configuration(EntityTypes.MEDIA_PLAYER, rsp.getKey(), null);

        String icon = getChannelIcon(rsp.getIcon(), "media");

        List<String> stateOptions = new ArrayList<>();
        stateOptions.add(stripEnumPrefix(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE));
        stateOptions.add(stripEnumPrefix(MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING));
        stateOptions.add(stripEnumPrefix(MediaPlayerState.MEDIA_PLAYER_STATE_PAUSED));
        stateOptions.add(stripEnumPrefix(MediaPlayerState.MEDIA_PLAYER_STATE_OFF));
        stateOptions.add(stripEnumPrefix(MediaPlayerState.MEDIA_PLAYER_STATE_ON));

        List<String> commandOptions = new ArrayList<>();
        commandOptions.add("PLAY");
        commandOptions.add("PAUSE");
        commandOptions.add("STOP");
        commandOptions.add("MUTE");
        commandOptions.add("UNMUTE");
        commandOptions.add("TOGGLE");

        ChannelType channelType = addChannelType(rsp.getName(), STRING, Set.of("Switch"), icon, rsp.getEntityCategory(),
                rsp.getDisabledByDefault());
        StateDescription stateDescription = optionListStateDescription(stateOptions);
        CommandDescription commandDescription = optionListCommandDescription(commandOptions);

        Channel channel = ChannelBuilder.create(createChannelUID(rsp.getObjectId(), EntityTypes.MEDIA_PLAYER))
                .withLabel(createChannelLabel(rsp.getName())).withKind(ChannelKind.STATE).withType(channelType.getUID())
                .withAcceptedItemType(STRING).withConfiguration(configuration).build();

        super.registerChannel(channel, channelType, stateDescription, commandDescription);

        // Register AudioSink for this device if not already registered
        handler.registerAudioSink(null);
    }

    public void handleState(MediaPlayerStateResponse rsp) {
        State state;
        switch (rsp.getState()) {
            case MEDIA_PLAYER_STATE_IDLE:
                state = new StringType("IDLE");
                break;
            case MEDIA_PLAYER_STATE_PLAYING:
                state = new StringType("PLAYING");
                break;
            case MEDIA_PLAYER_STATE_PAUSED:
                state = new StringType("PAUSED");
                break;
            case MEDIA_PLAYER_STATE_OFF:
                state = new StringType("OFF");
                break;
            case MEDIA_PLAYER_STATE_ON:
                state = new StringType("ON");
                break;
            case MEDIA_PLAYER_STATE_ANNOUNCING:
                state = new StringType("ANNOUNCING");
                break;
            case MEDIA_PLAYER_STATE_NONE:
            default:
                state = UnDefType.NULL;
        }
        findChannelByKey(rsp.getKey()).ifPresent(channel -> handler.updateState(channel.getUID(), state));

        // Log volume if available
        logger.debug("[{}] Media player state: {}, volume: {}, muted: {}", handler.getLogPrefix(), rsp.getState(),
                rsp.getVolume(), rsp.getMuted());
    }

    public static String stripEnumPrefix(MediaPlayerState state) {
        String name = state.toString();
        int idx = name.indexOf('_');
        return idx > 0 ? name.substring(idx + 1) : name;
    }
}
