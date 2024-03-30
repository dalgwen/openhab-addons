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
package org.openhab.transform.timedevent.profiles;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.thing.Channel;
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
 * {@link ProfileFactory} that creates the transformation profile for the {@link TimedEventMultiAndLongProfile}.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
@Component(service = { ProfileFactory.class, ProfileTypeProvider.class })
public class TimedEventProfileFactory implements ProfileFactory, ProfileTypeProvider, ProfileAdvisor {

    private ChannelTypeRegistry channelTypeRegistry;

    @Activate
    public TimedEventProfileFactory(final @Reference ChannelTypeRegistry channelTypeRegistry) {
        this.channelTypeRegistry = channelTypeRegistry;
    }

    @Override
    public Collection<ProfileType> getProfileTypes(@Nullable Locale locale) {
        return List.of(ProfileTypeBuilder
                .newTrigger(TimedEventMultiAndLongProfile.PROFILE_TYPE_UID, "Button Press to Multi / Long Press String")
                .build());
    }

    @Override
    public @Nullable Profile createProfile(ProfileTypeUID profileTypeUID, ProfileCallback callback,
            ProfileContext profileContext) {
        return new TimedEventMultiAndLongProfile(callback, profileContext);
    }

    @Override
    public Collection<ProfileTypeUID> getSupportedProfileTypeUIDs() {
        return List.of(TimedEventMultiAndLongProfile.PROFILE_TYPE_UID);
    }

    @Override
    public @Nullable ProfileTypeUID getSuggestedProfileTypeUID(@Nullable ChannelType channelType,
            @Nullable String itemType) {
        if (channelType == null || !CoreItemFactory.STRING.equalsIgnoreCase(itemType)) {
            return null;
        }
        return TimedEventMultiAndLongProfile.PROFILE_TYPE_UID;
    }

    @Override
    public @Nullable ProfileTypeUID getSuggestedProfileTypeUID(Channel channel, @Nullable String itemType) {
        ChannelType channelType = channelTypeRegistry.getChannelType(channel.getChannelTypeUID());
        return getSuggestedProfileTypeUID(channelType, itemType);
    }
}
