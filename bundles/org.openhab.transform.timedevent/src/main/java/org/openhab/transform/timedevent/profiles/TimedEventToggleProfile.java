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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;

/**
 * Profile to offer the timedevent toggle profile
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class TimedEventToggleProfile<T extends State & Command> extends TimedEventMultiPressProfile {

    private T initialState;
    private T alternativeState;
    private final ProfileCallback callback;

    private @Nullable State previousState;

    public TimedEventToggleProfile(ProfileCallback callback, ProfileContext context, T initialState,
            T alternativeState) {
        super(callback, context);
        this.initialState = initialState;
        this.alternativeState = alternativeState;
        this.callback = callback;
    }

    public static <T extends State & Command> BiFunction<ProfileCallback, ProfileContext, TimedEventProfile> getFactory(
            T initialState, T alternativeState) {
        return (profileCallback, profileContext) -> new TimedEventToggleProfile<T>(profileCallback, profileContext,
                initialState, alternativeState);
    }

    @Override
    public void onStateUpdateFromItem(State state) {
        previousState = state.as(initialState.getClass());
    }

    @Override
    public void onFilteredMultiPressFromHandler(ComputedEvent event) {
        if (pressedEvents.contains(event.event().toString()) && event.nbLongPress() <= 1) {
            // send only the first press
            T newState = initialState.equals(previousState) ? alternativeState : initialState;
            callback.sendCommand(newState);
            previousState = newState;
        }
    }
}
