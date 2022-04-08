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
package org.openhab.transform.rawrockermultilongpress.profiles;

import static org.openhab.core.thing.profiles.SystemProfiles.DEFAULT;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.DefaultSystemChannelTypeProvider;
import org.openhab.core.thing.profiles.Profile;
import org.openhab.core.thing.profiles.ProfileAdvisor;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileFactory;
import org.openhab.core.thing.profiles.ProfileType;
import org.openhab.core.thing.profiles.ProfileTypeBuilder;
import org.openhab.core.thing.profiles.ProfileTypeProvider;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * {@link ProfileFactory} that creates the transformation profile for the {@link RawRockerMultiLongPressProfile}.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
@Component(service = { ProfileFactory.class, ProfileTypeProvider.class })
public class RawRockerMultiLongPressProfileFactory implements ProfileFactory, ProfileTypeProvider, ProfileAdvisor {

    private ChannelTypeRegistry channelTypeRegistry;

    @Activate
    public RawRockerMultiLongPressProfileFactory(final @Reference ChannelTypeRegistry channelTypeRegistry) {
        this.channelTypeRegistry = channelTypeRegistry;
    }

    @Override
    public Collection<ProfileType> getProfileTypes(@Nullable Locale locale) {
        return List.of(ProfileTypeBuilder
                .newTrigger(RawRockerMultiLongPressProfile.PROFILE_TYPE_UID, "Raw Rocker To Multi / Long Press String")
                .build());
    }

    @Override
    public @Nullable Profile createProfile(ProfileTypeUID profileTypeUID, ProfileCallback callback,
            ProfileContext profileContext) {
        return new RawRockerMultiLongPressProfile(callback, profileContext);
    }

    @Override
    public Collection<ProfileTypeUID> getSupportedProfileTypeUIDs() {
        return List.of(RawRockerMultiLongPressProfile.PROFILE_TYPE_UID);
    }

    @Override
    public @Nullable ProfileTypeUID getSuggestedProfileTypeUID(@Nullable ChannelType channelType,
            @Nullable String itemType) {
        if (channelType == null) {
            return null;
        }
        switch (channelType.getKind()) {
            case TRIGGER:
                if (DefaultSystemChannelTypeProvider.SYSTEM_RAWROCKER.getUID().equals(channelType.getUID())) {
                    if (CoreItemFactory.STRING.equalsIgnoreCase(itemType)) {
                        return RawRockerMultiLongPressProfile.PROFILE_TYPE_UID;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported channel kind: " + channelType.getKind());
        }
        return null;
    }

    @Override
    public @Nullable ProfileTypeUID getSuggestedProfileTypeUID(Channel channel, @Nullable String itemType) {
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        if (channelType == null) {
            switch (channel.getKind()) {
                case STATE:
                    return DEFAULT;
                case TRIGGER:
                    return null;
                default:
                    throw new IllegalArgumentException("Unsupported channel kind: " + channel.getKind());
            }
        } else {
            return getSuggestedProfileTypeUID(channelType, itemType);
        }
    }
}
