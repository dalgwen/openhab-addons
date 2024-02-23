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

import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;

/**
 * Profile to offer the timedevent long/multipress profile
 * This profile transform multiple presses or a long button press into a descriptive string.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class TimedEventMultiAndLongProfile extends TimedEventProfile {

    TimedEventMultiAndLongProfile(ProfileCallback callback, ProfileContext context) {
        super(callback, context);
    }

    public static BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> getFactory() {
        return (profileCallback, profileContext) -> new TimedEventMultiAndLongProfile(profileCallback, profileContext);
    }

    @Override
    protected void event(ComputedEvent event) {
        if (releaseEvents.contains(event.event().toString())) {
            return;
        }
        if (this.mode == CommandOrUpdate.COMMAND) {
            callback.sendCommand(new StringType(toStringEvent(event)));
        } else {
            callback.sendUpdate(new StringType(toStringEvent(event)));
        }
    }

    private String toStringEvent(ComputedEvent computedEvent) {
        String StringEvent = computedEvent.event() + "." + computedEvent.nbOccurence();
        if (computedEvent.nbLongPress() > 0) {
            StringEvent += ".LONG";
        }
        return StringEvent;
    }
}
