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
package org.openhab.binding.signal.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link SignalConfiguration} class contains configuration for managing (or connecting to) signal-cli.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalConfiguration {

    public SignalConfiguration(Kind kind, String configuration) {
        this.kind = kind;
        this.configuration = configuration;
    }

    static final String CFG_KIND = "kind" ;
    static final String CFG_CONFIGURATION = "configuration" ;
    /**
     * Embedded or network mode for signal-cli
     */
    public Kind kind;

    /**
     * Network url (kind = network), or version number (kind = embedded)
     */
    public String configuration;

    public enum Kind {
        LOCAL,
        MANAGED,
        NETWORK
    }
}
