/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Reaction to a massage
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class Reaction {

    boolean remove;
    String emoji;

    public Reaction(boolean remove, String emoji) {
        this.remove = remove;
        this.emoji = emoji;
    }

    public boolean isRemove() {
        return remove;
    }

    public String getEmoji() {
        return emoji;
    }
}
