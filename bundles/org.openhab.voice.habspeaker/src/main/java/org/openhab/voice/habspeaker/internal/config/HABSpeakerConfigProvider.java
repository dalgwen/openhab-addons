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
package org.openhab.voice.habspeaker.internal.config;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.*;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigOptionProvider;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.voice.TTSService;
import org.openhab.core.voice.Voice;
import org.openhab.core.voice.VoiceManager;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerConfigProvider} class defines the speaker configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = { HABSpeakerConfigProvider.class,
        ConfigOptionProvider.class }, configurationPid = SERVICE_PID, property = Constants.SERVICE_PID + "="
                + SERVICE_PID)
@ConfigurableService(category = SERVICE_CATEGORY, label = SERVICE_NAME, description_uri = SERVICE_CATEGORY + ":"
        + SERVICE_ID)
@NonNullByDefault
public class HABSpeakerConfigProvider implements ConfigOptionProvider {
    protected static final String SPEAKER_CONFIG_URI = "thing-type:habspeaker:speaker";
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerConfigProvider.class);
    private final VoiceManager voiceManager;
    private final LocaleProvider localeProvider;

    private HABSpeakerConfig config = new HABSpeakerConfig();

    @Activate
    public HABSpeakerConfigProvider(@Reference VoiceManager voiceManager, @Reference LocaleProvider localeProvider) {
        this.voiceManager = voiceManager;
        this.localeProvider = localeProvider;
    }

    public HABSpeakerConfig getConfig() {
        return config;
    }

    @Activate
    public void activate(Map<String, Object> configMap) {
        modified(configMap);
    }

    @Modified
    public void modified(Map<String, Object> configMap) {
        var config = new Configuration(configMap).as(HABSpeakerConfig.class);
        this.config = config;
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (context == null && SPEAKER_CONFIG_URI.equals(uri.toString())) {
            switch (param) {
                case "hli":
                    return voiceManager.getHLIs().stream()
                            .sorted((hli1, hli2) -> hli1.getLabel(locale).compareToIgnoreCase(hli2.getLabel(locale)))
                            .map(hli -> new ParameterOption(hli.getId(), hli.getLabel(locale)))
                            .collect(Collectors.toList());
                case "ks":
                    return voiceManager.getKSs().stream()
                            .sorted((ks1, ks2) -> ks1.getLabel(locale).compareToIgnoreCase(ks2.getLabel(locale)))
                            .map(ks -> new ParameterOption(ks.getId(), ks.getLabel(locale)))
                            .collect(Collectors.toList());
                case "stt":
                    return voiceManager.getSTTs().stream()
                            .sorted((stt1, stt2) -> stt1.getLabel(locale).compareToIgnoreCase(stt2.getLabel(locale)))
                            .map(stt -> new ParameterOption(stt.getId(), stt.getLabel(locale)))
                            .collect(Collectors.toList());
                case "tts":
                    return voiceManager.getTTSs().stream()
                            .sorted((tts1, tts2) -> tts1.getLabel(locale).compareToIgnoreCase(tts2.getLabel(locale)))
                            .map(tts -> new ParameterOption(tts.getId(), tts.getLabel(locale)))
                            .collect(Collectors.toList());
                case "voice":
                    Locale nullSafeLocale = locale != null ? locale : localeProvider.getLocale();
                    return voiceManager.getAllVoices().stream().filter(v -> getVoiceTTS(v) != null)
                            .map(v -> new ParameterOption(v.getUID(),
                                    String.format("%s - %s - %s", getVoiceTTS(v).getLabel(nullSafeLocale),
                                            v.getLocale().getDisplayName(nullSafeLocale), v.getLabel())))
                            .collect(Collectors.toList());
            }
        }
        return null;
    }

    private @Nullable TTSService getVoiceTTS(Voice voice) {
        return voiceManager.getTTS(voice.getUID().split(":")[0]);
    }
}
