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
package org.openhab.automation.javascripting.eventinfo;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ThingStatusUpdate implements EventInfo {

    @Nullable
    public ThingUID thingUID;
    @Nullable
    public ThingStatus status;

    @Override
    public void fill(Map<String, ?> inputs) {
        this.thingUID = (ThingUID) inputs.get("thingUID");
        this.status = (ThingStatus) inputs.get("status");
    }
}
