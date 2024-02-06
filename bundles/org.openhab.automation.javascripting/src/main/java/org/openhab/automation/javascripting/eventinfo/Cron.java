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
import org.openhab.core.automation.events.TimerEvent;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Cron extends EventInfo {

    @Nullable
    protected final String cronId;

    public Cron(Map<String, ?> inputs) {
        super(inputs);
        TimerEvent timerEvent = (TimerEvent) shouldNotBeNull("event");
        this.cronId = shouldNotBeNull(timerEvent.getSource(), "event.source");
    }

    @Nullable
    public String getCronId() {
        return cronId;
    }
}
