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
package org.openhab.binding.signal.internal.downloader;

import java.io.Serial;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.service.UnrecoverableException;

/**
 * To notify fatal download error
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class VersionDownloaderException extends UnrecoverableException {
    public VersionDownloaderException(String message, Exception cause) {
        super(message, cause);
    }

    public VersionDownloaderException(String message) {
        super(message);
    }
}
