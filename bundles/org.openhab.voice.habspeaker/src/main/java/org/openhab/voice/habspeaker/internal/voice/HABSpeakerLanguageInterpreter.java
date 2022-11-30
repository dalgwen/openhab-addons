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
package org.openhab.voice.habspeaker.internal.voice;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.core.voice.text.InterpretationException;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfig;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerLanguageInterpreter} class defines the speaker Audio Sink
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerLanguageInterpreter implements HumanLanguageInterpreter {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerLanguageInterpreter.class);
    private final HABSpeakerIO speakerIO;
    private final Supplier<HABSpeakerConfig> configSupplier;

    public HABSpeakerLanguageInterpreter(HABSpeakerIO speakerIO, Supplier<HABSpeakerConfig> configSupplier) {
        this.speakerIO = speakerIO;
        this.configSupplier = configSupplier;
    }

    @Override
    public String getId() {
        return "habspeaker::" + speakerIO.getId() + "::hli";
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return "HAB Speaker Language Interpreter";
    }

    @Override
    public String interpret(Locale locale, String s) throws InterpretationException {
        var config = configSupplier.get();
        if (compareTemplate(config.stopDropInPhrase, s)) {
            speakerIO.dropIn(null);
            return "done";
        }
        throw new InterpretationException("Unknown voice command");
    }

    private boolean compareTemplate(String template, String text) {
        var trimmedText = text.trim();
        return !template.isBlank()
                && Arrays.stream(template.split(";")).anyMatch(t -> t.trim().equalsIgnoreCase(trimmedText));
    }

    @Override
    public @Nullable String getGrammar(Locale locale, String s) {
        return null;
    }

    @Override
    public Set<Locale> getSupportedLocales() {
        return Set.of();
    }

    @Override
    public Set<String> getSupportedGrammarFormats() {
        return Set.of();
    }
}
