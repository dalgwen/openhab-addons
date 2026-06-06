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
import org.eclipse.jdt.annotation.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Recipient address
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class CorrespondentAddress {
    public static final UUID UNKNOWN_UUID = new UUID(0, 0);

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
    private final @Nullable String number;
    private final @Nullable String uuid;

    public static boolean isUuid(@Nullable String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }

    public CorrespondentAddress(@Nullable String number, @Nullable String uuid) {
        this.number = number;
        this.uuid = uuid;
        if (number == null && uuid == null) {
            throw new IllegalArgumentException("At least one of number or uuid must be provided");
        }
    }

    public Optional<String> uuid() {
        return Optional.ofNullable(uuid);
    }

    public Optional<String> number() {
        return Optional.ofNullable(number);
    }

    @Override
    public String toString() {
        return "{number='" + number + '\'' +
                ", uuid='" + uuid + '\'' +
                '}';
    }
}
