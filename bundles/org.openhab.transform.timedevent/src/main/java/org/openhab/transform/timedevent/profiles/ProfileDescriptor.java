/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.transform.timedevent.profiles;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.DefaultSystemChannelTypeProvider;
import org.openhab.core.thing.profiles.Profile;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileType;
import org.openhab.core.thing.profiles.ProfileTypeBuilder;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.profiles.TriggerProfileType;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.transform.TransformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store all profiles for timed event
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public enum ProfileDescriptor {

    BUTTON_TOGGLE_SWITCH("button-multipress-toggle-switch", "Button Multipress Toggle Switch",
            Set.of(CoreItemFactory.SWITCH, CoreItemFactory.DIMMER, CoreItemFactory.COLOR),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventToggleProfile.getFactory(OnOffType.ON, OnOffType.OFF)),
    BUTTON_TOGGLE_PLAYER("button-multipress-toggle-player", "Button Multipress Toggle Player",
            Set.of(CoreItemFactory.PLAYER), Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventToggleProfile.getFactory(PlayPauseType.PLAY, PlayPauseType.PAUSE)),
    BUTTON_TOGGLE_ROLLERSHUTTER("button-multipress-toggle-rollershutter", "Button Multipress Toggle Rollershutter",
            Set.of(CoreItemFactory.ROLLERSHUTTER),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventToggleProfile.getFactory(UpDownType.UP, UpDownType.DOWN)),
    RAWBUTTON_ON_OFF_SWITCH("rawbutton-multipress-on-off-switch", "Raw Button Multipress To On Off",
            Set.of(CoreItemFactory.SWITCH, CoreItemFactory.DIMMER, CoreItemFactory.COLOR),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventRawButtonProfile.getFactory(OnOffType.ON, OnOffType.OFF)),
    RAWBUTTON_MOVE_STOP_SWITCH("rawbutton-multipress-move-stop", "Raw Button Multipress To Move Stop",
            Set.of(CoreItemFactory.SWITCH, CoreItemFactory.DIMMER, CoreItemFactory.COLOR),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventRawButtonProfile.getFactory(StopMoveType.MOVE, StopMoveType.STOP)),
    RAWBUTTON_UP_DOWN_SWITCH("rawbutton-multipress-up-down", "Raw Button Multipress To Up Down",
            Set.of(CoreItemFactory.SWITCH, CoreItemFactory.DIMMER, CoreItemFactory.COLOR),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventRawButtonProfile.getFactory(UpDownType.UP, UpDownType.DOWN)),
    RAWBUTTON_TOGGLE_PLAYER("rawbutton-multipress-toggle-player", "Raw Button Multipress Toggle Player",
            Set.of(CoreItemFactory.PLAYER), Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventToggleProfile.getFactory(PlayPauseType.PLAY, PlayPauseType.PAUSE)),
    RAWBUTTON_TOGGLE_ROLLERSHUTTER("rawbutton-multipress-toggle-rollershutter",
            "Raw Button Multipress Toggle Rollershutter", Set.of(CoreItemFactory.ROLLERSHUTTER),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventToggleProfile.getFactory(UpDownType.UP, UpDownType.DOWN)),
    RAWBUTTON_TOGGLE_SWITCH("rawbutton-multipress-toggle-switch", "Raw Button Multipress Toggle Switch",
            Set.of(CoreItemFactory.SWITCH, CoreItemFactory.DIMMER, CoreItemFactory.COLOR),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWBUTTON),
            TimedEventToggleProfile.getFactory(OnOffType.ON, OnOffType.OFF)),
    RAWROCKER_DIMMER("rawrocker-multipress-to-dimmer", "Raw Rocker Multipress To Dimmer",
            Set.of(CoreItemFactory.DIMMER), Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerDimmerProfile.getFactory()),
    RAWROCKER_NEXT_PREVIOUS("rawrocker-multipress-to-next-previous", "Raw Rocker Multipress To Next/Previous",
            Set.of(CoreItemFactory.PLAYER), Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerProfile.getFactory(NextPreviousType.NEXT, NextPreviousType.PREVIOUS)),
    RAWROCKER_ON_OFF("rawrocker-multipress-to-on-off", "Raw Rocker Multipress To On Off",
            Set.of(CoreItemFactory.SWITCH, CoreItemFactory.DIMMER, CoreItemFactory.COLOR),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerProfile.getFactory(OnOffType.ON, OnOffType.OFF)),
    RAWROCKER_PLAY_PAUSE("rawrocker-multipress-to-play-pause", "Raw Rocker Multipress To Play/Pause",
            Set.of(CoreItemFactory.PLAYER), Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerProfile.getFactory(PlayPauseType.PLAY, PlayPauseType.PAUSE)),
    RAWROCKER_REWIND_FASTFORWARD("rawrocker-multipress-to-rewind-fastforward",
            "Raw Rocker Multipress To Rewind/Fastforward", Set.of(CoreItemFactory.PLAYER),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerProfile.getFactory(RewindFastforwardType.REWIND, RewindFastforwardType.FASTFORWARD)),
    RAWROCKER_STOP_MOVE("rawrocker-multipress-to-stop-move", "Raw Rocker Multipress To Stop/Move",
            Set.of(CoreItemFactory.ROLLERSHUTTER),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerProfile.getFactory(StopMoveType.MOVE, StopMoveType.STOP)),
    RAWROCKER_UP_DOWN("rawrocker-multipress-to-up-down", "Raw Rocker Multipress To Up/Down",
            Set.of(CoreItemFactory.ROLLERSHUTTER),
            Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_RAWROCKER),
            TimedEventRawRockerProfile.getFactory(UpDownType.UP, UpDownType.DOWN)),
    TRIGGER_EVENT_STRING("trigger-multipress-event-string", "Trigger Multipress Event String",
            Set.of(CoreItemFactory.STRING), Set.of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_TYPE_UID_TRIGGER),
            TimedEventStringProfile.getFactory()),
    TRIGGER_ENHANCED_EVENT_STRING("trigger-multipresslong-event-string", "Trigger Multi/Long Press Event String",
            Set.of(CoreItemFactory.STRING), Collections.emptySet(), TimedEventMultiAndLongProfile.getFactory());

    protected static final Logger logger = LoggerFactory.getLogger(ProfileDescriptor.class);

    public final ProfileType profileType;
    public final ProfileTypeUID profileTypeUID;
    private BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> profileBuilder;

    ProfileDescriptor(String id, String label, Set<String> itemTypes, Set<ChannelTypeUID> supportedChannelTypeUIDs,
            BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> profileBuilder) {
        this.profileTypeUID = new ProfileTypeUID(TransformationService.TRANSFORM_PROFILE_SCOPE, id);
        ProfileTypeBuilder<TriggerProfileType> builder = ProfileTypeBuilder.newTrigger(profileTypeUID, label)
                .withSupportedItemTypes(itemTypes);
        if (!supportedChannelTypeUIDs.isEmpty()) {
            builder = builder.withSupportedChannelTypeUIDs(supportedChannelTypeUIDs);
        }
        this.profileType = builder.build();
        this.profileBuilder = profileBuilder;
    }

    public @Nullable Profile buildProfile(ProfileCallback callback, ProfileContext context) {
        TimedEventProfile profile = profileBuilder.apply(callback, context);
        profile.setProfileTypeUID(profileTypeUID);
        return profile;
    }
}
