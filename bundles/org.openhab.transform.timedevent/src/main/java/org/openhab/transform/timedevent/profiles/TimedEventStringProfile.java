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
import org.openhab.core.types.Command;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;

/**
 * Profile to offer the timedevent string event profile
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class TimedEventStringProfile<T extends Command> extends TimedEventMultiPressProfile {

    public TimedEventStringProfile(ProfileCallback callback, ProfileContext context) {
        super(callback, context);
    }

    public static <T extends Command> BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> getFactory() {
        return (profileCallback, profileContext) -> new TimedEventStringProfile<T>(profileCallback, profileContext);
    }

    @Override
    public void onFilteredMultiPressFromHandler(ComputedEvent event) {
        if (event.nbLongPress() <= 1) { // send only the first press
            callback.sendCommand(new StringType(event.event().toString()));
        }
    }
}
