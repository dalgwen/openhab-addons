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
import org.openhab.core.items.events.ItemStateUpdatedEvent;
import org.openhab.core.types.State;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ItemStateUpdate extends EventInfo {

    @Nullable
    protected final String itemName;
    @Nullable
    protected final State state;

    public ItemStateUpdate(Map<String, ?> inputs) {
        super(inputs);
        ItemStateUpdatedEvent event = (ItemStateUpdatedEvent) shouldNotBeNull("event");
        this.itemName = shouldNotBeNull(event.getItemName(), "event.itemName");
        this.state = shouldNotBeNull(event.getItemState(), "event.itemState");
    }

    @Nullable
    public String getItemName() {
        return itemName;
    }

    @Nullable
    public State getState() {
        return state;
    }
}
