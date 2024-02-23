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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;

/**
 * Profile to offer the timedevent multi press Profile
 * This profile is like the standard system profiles, but adds a filter with the number of button press.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public abstract class TimedEventMultiPressProfile extends TimedEventProfile {

    private final int nbOfPress;

    TimedEventMultiPressProfile(ProfileCallback callback, ProfileContext context) {
        super(callback, context);
        this.nbOfPress = getParameterAs(TimedEventConstants.PARAM_NB_OF_PRESS, Integer.class).orElse(2);
    }

    @Override
    protected void event(ComputedEvent event) {
        if (event.nbOccurence() != nbOfPress) {
            return;
        }
        onFilteredMultiPressFromHandler(event);
    }

    public abstract void onFilteredMultiPressFromHandler(ComputedEvent event);
}
