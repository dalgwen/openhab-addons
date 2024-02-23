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

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.profiles.Profile;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileFactory;
import org.openhab.core.thing.profiles.ProfileType;
import org.openhab.core.thing.profiles.ProfileTypeProvider;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.osgi.service.component.annotations.Component;

/**
 * {@link ProfileFactory} that creates the transformation profile for all the timed event profiles}.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
@Component(service = { ProfileFactory.class, ProfileTypeProvider.class })
public class TimedEventProfileFactory implements ProfileFactory, ProfileTypeProvider {

    @Override
    public Collection<ProfileType> getProfileTypes(@Nullable Locale locale) {
        return Arrays.stream(ProfileDescriptor.values()).map(desc -> desc.profileType).toList();
    }

    @Override
    public @Nullable Profile createProfile(ProfileTypeUID profileTypeUID, ProfileCallback callback,
            ProfileContext profileContext) {
        return Arrays.stream(ProfileDescriptor.values()) //
                .filter(desc -> profileTypeUID.equals(desc.profileTypeUID)) //
                .findFirst() //
                .map(desc -> desc.buildProfile(callback, profileContext)) //
                .orElse(null);
    }

    @Override
    public Collection<ProfileTypeUID> getSupportedProfileTypeUIDs() {
        return Arrays.stream(ProfileDescriptor.values()).map(desc -> desc.profileTypeUID).toList();
    }
}
