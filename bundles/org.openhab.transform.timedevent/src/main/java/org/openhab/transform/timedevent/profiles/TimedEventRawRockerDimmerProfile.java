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
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.types.Command;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;

/**
 * Profile to offer the timedevent raw rocker dimmer profile
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class TimedEventRawRockerDimmerProfile<T extends Command> extends TimedEventMultiPressProfile {

    public TimedEventRawRockerDimmerProfile(ProfileCallback callback, ProfileContext context) {
        super(callback, context);
    }

    public static <T extends Command> BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> getFactory() {
        return (profileCallback, profileContext) -> new TimedEventRawRockerDimmerProfile<T>(profileCallback,
                profileContext);
    }

    @Override
    public void onFilteredMultiPressFromHandler(ComputedEvent event) {

        if (event.nbLongPress() == 0) { // just one short press
            if (CommonTriggerEvents.DIR1_PRESSED.equals(event.event().toString())) {
                callback.sendCommand(OnOffType.ON);
            } else {
                callback.sendCommand(OnOffType.OFF);
            }
        } else {
            if (CommonTriggerEvents.DIR1_PRESSED.equals(event.event().toString())) {
                callback.sendCommand(IncreaseDecreaseType.INCREASE);
            } else {
                callback.sendCommand(IncreaseDecreaseType.DECREASE);
            }
        }
    }
}
