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
import org.openhab.core.items.events.ItemCommandEvent;
import org.openhab.core.types.Command;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class ItemCommand extends EventInfo {

    @Nullable
    protected final String itemName;
    @Nullable
    protected final Command command;

    public ItemCommand(Map<String, ?> inputs) {
        super(inputs);
        ItemCommandEvent event = (ItemCommandEvent) shouldNotBeNull("event");
        this.itemName = shouldNotBeNull(event.getItemName(), "event.itemName");
        this.command = (Command) shouldNotBeNull("command");
    }

    @Nullable
    public String getItemName() {
        return itemName;
    }

    @Nullable
    public Command getCommand() {
        return command;
    }
}
