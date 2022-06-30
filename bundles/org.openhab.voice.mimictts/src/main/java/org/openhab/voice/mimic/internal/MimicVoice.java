/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.voice.mimic.internal;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.voice.Voice;

/**
 * Mimic Voice representation.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class MimicVoice implements Voice {

    @Nullable
    private String speaker;

    private final Locale locale;

    private final String key;

    private final String name;

    public MimicVoice(String key, String language, String name, @Nullable String speaker) {
        this.key = key;
        this.locale = Locale.forLanguageTag(language.replaceAll("_", "-"));
        this.name = name;
        this.speaker = speaker;
    }

    /**
     * Globally unique identifier of the voice.
     *
     * @return A String uniquely identifying the voice globally
     */
    @Override
    public String getUID() {
        return "mimictts:" + getTechnicalName().replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * Technical name of the voice.
     *
     * @return A String voice technical name
     */
    String getTechnicalName() {
        String speakerId = (speaker != null) ? "#" + speaker : "";
        return (key + speakerId);
    }

    @Override
    public String getLabel() {
        return name + ((speaker != null) ? "#" + speaker : "");
    }

    @Override
    public Locale getLocale() {
        return locale;
    }
}
