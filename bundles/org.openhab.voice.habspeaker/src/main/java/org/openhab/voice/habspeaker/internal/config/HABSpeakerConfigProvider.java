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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.OpenHAB;
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
import org.osgi.service.component.annotations.Deactivate;
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
    public static final String RUSTPOTTER_WEB_KS_ID = "habspeaker::rustpotter_web::ks";
    public static final String HABSPEAKER_FOLDER = Path.of(OpenHAB.getUserDataFolder(), "habspeaker").toString();
    private static final String MEDIA_FOLDER = Path.of(HABSPEAKER_FOLDER, "media").toString();
    private static final String KS_FOLDER = Path.of(HABSPEAKER_FOLDER, "ks").toString();
    public static final String RUSTPOTTER_FOLDER = Path.of(KS_FOLDER, "rustpotter").toString();
    public static final String RUSTPOTTER_ADDON_FOLDER = Path.of(OpenHAB.getUserDataFolder(), "rustpotter").toString();
    private static final String CREDENTIALS_FOLDER = Path.of(HABSPEAKER_FOLDER, "credentials").toString();
    static {
        Logger logger = LoggerFactory.getLogger(HABSpeakerConfigProvider.class);
        ensureDir("root", HABSPEAKER_FOLDER, logger);
        ensureDir("media", MEDIA_FOLDER, logger);
        ensureDir("ks", KS_FOLDER, logger);
        ensureDir("rustpotter", RUSTPOTTER_FOLDER, logger);
        ensureDir("credentials", CREDENTIALS_FOLDER, logger);
    }

    private final Logger logger = LoggerFactory.getLogger(HABSpeakerConfigProvider.class);
    private final VoiceManager voiceManager;
    private final LocaleProvider localeProvider;
    private final HABSpeakerVoiceConfigHelper voiceConfigHelper;
    private HABSpeakerConfig config = new HABSpeakerConfig();
    Set<HABSpeakerConfigProviderListener> listeners = new HashSet<>();

    @Activate
    public HABSpeakerConfigProvider(@Reference VoiceManager voiceManager, @Reference LocaleProvider localeProvider,
            @Reference HABSpeakerVoiceConfigHelper voiceConfigHelper) {
        this.voiceManager = voiceManager;
        this.localeProvider = localeProvider;
        this.voiceConfigHelper = voiceConfigHelper;
    }

    public HABSpeakerConfig getConfig() {
        return config;
    }

    /**
     *
     * @return the keyword configured in "System Settings/Voice"
     */
    public String getSystemKeyword() {
        return voiceConfigHelper.getKeyword();
    }

    @Activate
    public void activate(Map<String, Object> configMap) {
        modified(configMap);
    }

    @Modified
    public void modified(Map<String, Object> configMap) {
        var config = new Configuration(configMap).as(HABSpeakerConfig.class);
        this.config = config;
        listeners.forEach(listener -> listener.onGlobalConfigUpdate(config));
    }

    @Override
    public @Nullable Collection<ParameterOption> getParameterOptions(URI uri, String param, @Nullable String context,
            @Nullable Locale locale) {
        if (context == null && SPEAKER_CONFIG_URI.equals(uri.toString())) {
            switch (param) {
                case "hlis":
                    return voiceManager.getHLIs().stream()
                            .sorted((hli1, hli2) -> hli1.getLabel(locale).compareToIgnoreCase(hli2.getLabel(locale)))
                            .map(hli -> new ParameterOption(hli.getId(), hli.getLabel(locale)))
                            .collect(Collectors.toList());
                case "ks":
                    var clientKsServices = Stream
                            .of(new ParameterOption(RUSTPOTTER_WEB_KS_ID, "Rustpotter Web (Client Spotter)"));
                    var serverKsServices = voiceManager.getKSs().stream()
                            .sorted((ks1, ks2) -> ks1.getLabel(locale).compareToIgnoreCase(ks2.getLabel(locale)))
                            .map(ks -> new ParameterOption(ks.getId(), ks.getLabel(locale)));
                    return Stream.concat(clientKsServices, serverKsServices).collect(Collectors.toList());
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

    public void addListener(HABSpeakerConfigProviderListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HABSpeakerConfigProviderListener listener) {
        listeners.remove(listener);
    }

    @Deactivate
    public void deactivate() {
    }

    public interface HABSpeakerConfigProviderListener {

        void onGlobalConfigUpdate(HABSpeakerConfig config);
    }

    private static void ensureDir(String name, String path, Logger logger) {
        File credentials = new File(path);
        if (!credentials.exists()) {
            if (credentials.mkdir()) {
                logger.info("habspeaker {} dir created {}", name, path);
            }
        }
    }
}
