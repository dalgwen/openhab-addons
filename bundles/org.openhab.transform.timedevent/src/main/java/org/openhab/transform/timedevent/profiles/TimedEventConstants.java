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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.CommonTriggerEvents;

/**
 * Constants for the profile
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class TimedEventConstants {

    /* All parameters */
    public static final String PARAM_NB_OF_PRESS = "numberOfPress";
    public static final String PARAM_HAS_RELEASEEVENT = "hasReleaseEvent";
    public static final String PARAM_RELEASED = "releasedEventOrState";
    public static final String PARAM_PRESSED = "pressedEventOrState";
    public static final String PARAM_MAXREPETITION = "maxRepetition";
    public static final String PARAM_DELAYBETWEENEVENT = "delay";

    public static final Integer DEFAULT_MAX_REPETITION = 10;
    public static final Integer DEFAULT_DELAYBETWEENEVENT = 500;

    public static final Set<String> DEFAULT_PRESSED_EVENTS = Set.of(CommonTriggerEvents.DIR1_PRESSED,
            CommonTriggerEvents.DIR2_PRESSED, CommonTriggerEvents.PRESSED, CommonTriggerEvents.SHORT_PRESSED,
            OnOffType.ON.toString(), OpenClosedType.OPEN.toString(), PlayPauseType.PLAY.toString(),
            UpDownType.UP.toString());
    public static final Set<String> DEFAULT_RELEASE_EVENTS = Set.of(CommonTriggerEvents.DIR1_RELEASED,
            CommonTriggerEvents.DIR2_RELEASED, CommonTriggerEvents.RELEASED, OnOffType.OFF.toString(),
            OpenClosedType.CLOSED.toString(), PlayPauseType.PAUSE.toString(), UpDownType.DOWN.toString());

}
