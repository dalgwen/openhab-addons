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
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.types.Command;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;

/**
 * Profile to offer the timedevent raw rocker profile
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class TimedEventRawRockerProfile<T extends Command> extends TimedEventMultiPressProfile {

    private T pressed1State;
    private T pressed2State;

    public TimedEventRawRockerProfile(ProfileCallback callback, ProfileContext context, T pressed1State,
            T pressed2State) {
        super(callback, context);
        this.pressed1State = pressed1State;
        this.pressed2State = pressed2State;
    }

    public static <T extends Command> BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> getFactory(
            T pressed1State, T pressed2State) {
        return (profileCallback, profileContext) -> new TimedEventRawRockerProfile<T>(profileCallback, profileContext,
                pressed1State, pressed2State);
    }

    @Override
    public void onFilteredMultiPressFromHandler(ComputedEvent event) {
        if (event.nbLongPress() <= 1) {
            if (CommonTriggerEvents.DIR1_PRESSED.equals(event.event().toString())) {
                // send only the first press:
                callback.sendCommand(pressed1State);
            } else if (CommonTriggerEvents.DIR2_PRESSED.equals(event.toString())) {
                callback.sendCommand(pressed2State);
            }
        }
    }
}
