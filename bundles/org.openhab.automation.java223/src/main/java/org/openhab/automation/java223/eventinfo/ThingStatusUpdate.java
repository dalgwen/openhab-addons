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
package org.openhab.automation.java223.eventinfo;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.events.ThingStatusInfoEvent;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ThingStatusUpdate extends EventInfo {

    @Nullable
    protected final ThingUID thingUID;
    @Nullable
    protected final ThingStatus status;

    public ThingStatusUpdate(Map<String, ?> inputs) {
        super(inputs);
        ThingStatusInfoEvent event = (ThingStatusInfoEvent) shouldNotBeNull("event");
        this.thingUID = shouldNotBeNull(event.getThingUID(), "event.thingUID");
        this.status = (ThingStatus) shouldNotBeNull("status");
    }

    @Nullable
    public ThingUID getThingUID() {
        return thingUID;
    }

    @Nullable
    public ThingStatus getStatus() {
        return status;
    }
}
