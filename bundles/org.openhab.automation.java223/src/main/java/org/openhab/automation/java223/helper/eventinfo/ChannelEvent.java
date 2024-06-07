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
package org.openhab.automation.java223.helper.eventinfo;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ChannelEvent extends EventInfo {

    @Nullable
    protected final String channelUUID;
    @Nullable
    protected final String event;

    public ChannelEvent(Map<String, ?> inputs) {
        super(inputs);
        this.event = (String) shouldNotBeNull("event");
        this.channelUUID = (String) shouldNotBeNull("channelUUID");
    }

    @Nullable
    public String getChannelUUID() {
        return channelUUID;
    }

    @Nullable
    public String getEvent() {
        return event;
    }

}
