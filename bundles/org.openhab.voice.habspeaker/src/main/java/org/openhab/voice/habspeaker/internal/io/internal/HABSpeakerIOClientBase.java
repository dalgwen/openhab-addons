/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.voice.habspeaker.internal.io.internal;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.voice.KSService;
import org.openhab.core.voice.VoiceManager;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSink;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSource;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOClient;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOHandler;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerKS;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerLanguageInterpreter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerIOClientBase} represents a speaker active connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public abstract class HABSpeakerIOClientBase implements HABSpeakerIOClient {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerIOClientBase.class);
    private final AudioManager audioManager;
    private final VoiceManager voiceManager;
    private final BundleContext bundleContext;
    protected final HABSpeakerConfigProvider configProvider;
    private final HABSpeakerLanguageInterpreter speakerLanguageInterpreter;
    protected @Nullable HABSpeakerIOHandler thingHandler = null;
    private boolean initialized = false;
    protected boolean serverSpotting = false;
    private @Nullable HABSpeakerKS ks = null;
    protected final ScheduledExecutorService scheduler = ThreadPoolManager.getScheduledPool("habspeaker");
    protected final Map<String, ServiceRegistration<?>> audioComponentRegistrations = new ConcurrentHashMap<>();
    private @Nullable HABSpeakerIOClient dropInSpeakerIO = null;
    private @Nullable AudioStream dropInStream = null;
    private @Nullable Future<?> dropInStreamTask = null;

    public HABSpeakerIOClientBase(AudioManager audioManager, VoiceManager voiceManager, BundleContext bundleContext,
            HABSpeakerConfigProvider configProvider, HABSpeakerWebSocketManager wsAdapter) {
        this.audioManager = audioManager;
        this.voiceManager = voiceManager;
        this.bundleContext = bundleContext;
        this.configProvider = configProvider;
        this.speakerLanguageInterpreter = new HABSpeakerLanguageInterpreter(this, wsAdapter, configProvider);
    }

    @Override
    public void setThingHandler(@Nullable HABSpeakerIOHandler handler) {
        thingHandler = handler;
    }

    @Override
    public @Nullable HABSpeakerIOHandler getThingHandler() {
        return thingHandler;
    }

    protected Map<String, Object> getSpeakerConfigMessage(HABSpeakerIOHandler handler) throws IllegalStateException {
        var config = handler.getSpeakerConfig();
        var initializedConfig = new HashMap<String, Object>();
        initializedConfig.put("sinkVolume", config.sinkVolume);
        initializedConfig.put("sampleRate", config.sampleRate);
        initializedConfig.put("resampleMode", config.clientResampleMode);
        initializedConfig.put("screenSaverTime", config.screenSaverTime);
        initializedConfig.put("dimScreen", config.dimScreen);
        initializedConfig.put("keepAwake", config.keepAwake);
        var label = handler.getLabel();
        if (label != null) {
            initializedConfig.put("label", label);
        }
        serverSpotting = false;
        var spotMode = SpotMode.NONE;
        if (!config.ks.isBlank()) {
            if (config.ks.equals(HABSpeakerConfigProvider.RUSTPOTTER_WEB_KS_ID)) {
                String wakewordFileName = config.rustpotterWakeword.toLowerCase();
                if (wakewordFileName.isBlank()) {
                    logger.warn("Missing rustpotter wakeword file, keyword spotting disabled");
                } else if (validateRustpotterWakeword(wakewordFileName)) {
                    logger.debug("Using rustpotter web with keyword '{}'", wakewordFileName);
                    spotMode = SpotMode.RUSTPOTTER_WEB;
                    var spotConfig = new HashMap<String, Object>();
                    spotConfig.put("keyword", wakewordFileName);
                    spotConfig.put("averagedThreshold", config.rustpotterAvgThreshold);
                    spotConfig.put("threshold", config.rustpotterThreshold);
                    spotConfig.put("minScores", config.rustpotterMinScores);
                    spotConfig.put("eager", config.rustpotterEager);
                    spotConfig.put("scoreMode", config.rustpotterScoreMode);
                    spotConfig.put("vadMode", config.rustpotterVADMode);
                    spotConfig.put("minGain", config.rustpotterMinGain);
                    spotConfig.put("maxGain", config.rustpotterMaxGain);
                    spotConfig.put("bandPassEnabled", config.rustpotterBandPass);
                    spotConfig.put("bandPassLowCutoff", config.rustpotterLowCutoff);
                    spotConfig.put("bandPassHighCutoff", config.rustpotterHighCutoff);
                    spotConfig.put("bandSize", config.rustpotterBandSize);
                    spotConfig.put("scoreRef", config.rustpotterScoreRef);
                    spotConfig.put("gainNormalizerEnabled", config.rustpotterGainNormalizer);
                    if (config.rustpotterGainRef != null) {
                        spotConfig.put("gainRef", config.rustpotterGainRef);
                    }
                    initializedConfig.put("spotConfig", spotConfig);
                } else {
                    logger.warn("Missing rustpotter wakeword file '{}', keyword spotting disabled", wakewordFileName);
                }
            } else if (voiceManager.getKS(config.ks) != null) {
                serverSpotting = true;
                spotMode = SpotMode.SERVER;
            } else {
                logger.warn("Missing ks service {}", config.ks);
            }
        }
        initializedConfig.put("spotMode", spotMode.toString());
        return initializedConfig;
    }

    private boolean validateRustpotterWakeword(String modelName) {
        String suffix = ".rpw";
        String fileName = modelName;
        if (!fileName.endsWith(suffix)) {
            fileName = fileName + suffix;
        }
        var modelFile = java.nio.file.Path.of(HABSpeakerConfigProvider.RUSTPOTTER_FOLDER, fileName).toFile();
        return modelFile.exists();
    }

    protected synchronized void registerSpeakerComponents(String id, long clientSampleRate,
            @Nullable HABSpeakerIOHandler thingHandler) throws IOException {
        if (id.isBlank()) {
            throw new IOException("Unable to register audio components");
        }
        String label = "HABSpeaker (" + id + ")";
        var sinkStereo = false;
        @Nullable
        String listeningItem = null;
        if (thingHandler != null) {
            // send speaker configuration before initialization
            var config = thingHandler.getSpeakerConfig();
            var thingLabel = thingHandler.getLabel();
            if (thingLabel != null && !thingLabel.isBlank()) {
                label = thingLabel;
            }
            listeningItem = !config.listeningItem.isBlank() ? config.listeningItem : null;
            sinkStereo = config.sinkStereo;
        }
        initialized = true;
        // register source
        var source = new HABSpeakerAudioSource(getSourceId(id), label, this, clientSampleRate);
        registerAudioComponent(source);
        // register sink
        var sink = new HABSpeakerAudioSink(getSinkId(id), label, this, sinkStereo ? 2 : 1, clientSampleRate);
        registerAudioComponent(sink);
        // init dialog
        var defaultHLI = voiceManager.getHLI();
        List<HumanLanguageInterpreter> defaultHlis = defaultHLI == null ? List.of(speakerLanguageInterpreter)
                : List.of(speakerLanguageInterpreter, defaultHLI);
        List<HumanLanguageInterpreter> hlis = defaultHlis;
        @Nullable
        KSService ks = voiceManager.getKS();
        var dCBuilder = voiceManager.getDialogContextBuilder();
        dCBuilder.withSource(source).withSink(sink).withListeningItem(listeningItem);
        if (thingHandler != null) {
            var speakerConfig = thingHandler.getSpeakerConfig();
            if (!speakerConfig.stt.isBlank()) {
                dCBuilder.withSTT(voiceManager.getSTT(speakerConfig.stt));
            }
            if (!speakerConfig.tts.isBlank()) {
                dCBuilder.withTTS(voiceManager.getTTS(speakerConfig.tts));
            }
            if (!speakerConfig.voice.isBlank()) {
                dCBuilder.withVoice(voiceManager.getAllVoices().stream()//
                        .filter(v -> v.getUID().equals(speakerConfig.voice))//
                        .findAny().orElse(null));
            }
            var interpreters = voiceManager.getHLIsByIds(speakerConfig.hlis);
            if (!interpreters.isEmpty()) {
                ArrayList<HumanLanguageInterpreter> finalInterpreters = new ArrayList<>();
                finalInterpreters.add(speakerLanguageInterpreter);
                finalInterpreters.addAll(interpreters);
                hlis = finalInterpreters;
            }
            if (!speakerConfig.keyword.isBlank()) {
                dCBuilder.withKeyword(speakerConfig.keyword);
            }
            if (!speakerConfig.ks.isBlank()) {
                ks = voiceManager.getKS(speakerConfig.ks);
            }
        }
        this.ks = new HABSpeakerKS(this, ks);
        voiceManager.startDialog(dCBuilder.withKS(this.ks)//
                .withHLIs(hlis)//
                .build());
    }

    protected synchronized void unregisterSpeakerComponents(String id) {
        if (initialized) {
            stopDropIn();
            var source = audioManager.getSource(getSourceId(id));
            if (source != null) {
                try {
                    voiceManager.stopDialog(source);
                } catch (Exception e) {
                    logger.debug("Unable to stop dialog for {}", source.getId());
                }
                unregisterAudioComponent(source);
            }
            var sink = audioManager.getSink(getSinkId(id));
            if (sink != null) {
                unregisterAudioComponent(sink);
            }
        }
    }

    public HABSpeakerLanguageInterpreter getLanguageInterpreter() {
        return speakerLanguageInterpreter;
    }

    @Override
    public @Nullable HABSpeakerIOClient getDropIn() {
        return dropInSpeakerIO;
    }

    @Override
    public synchronized void dropIn(@Nullable HABSpeakerIOClient speakerIO) {
        if (speakerIO == null) {
            stopDropIn();
            return;
        }
        if (speakerIO.equals(dropInSpeakerIO)) {
            return;
        }
        if (dropInSpeakerIO != null) {
            throw new IllegalStateException("Unable to drop-in, speaker is occupied");
        }
        dropInSpeakerIO = speakerIO;
        dropInStreamTask = scheduler.submit(() -> {
            var dropInSink = audioManager.getSink(getSinkId(speakerIO.getId()));
            var source = audioManager.getSource(getSourceId(getId()));
            if (dropInSink == null || source == null) {
                stopDropIn();
                throw new IllegalStateException("Unable to drop-in to speaker, missing audio components");
            }
            logger.debug("Starting drop-in to {}", speakerIO.getId());
            try {
                dropInSink.process(source.getInputStream(((HABSpeakerAudioSource) source).getInternalStreamFormat()));
            } catch (AudioException | ArrayIndexOutOfBoundsException e) {
                logger.warn("{} while running drop-in {}: ", e.getClass().getName(), getId(), e);
                stopDropIn();
            }
        });
        speakerIO.dropIn(this);
    }

    protected synchronized void stopDropIn() {
        if (dropInSpeakerIO == null && dropInStream == null && dropInStreamTask == null) {
            return;
        }
        logger.debug("Stopping drop-in {}", getId());
        var speakerIO = dropInSpeakerIO;
        var task = dropInStreamTask;
        var stream = dropInStream;
        dropInSpeakerIO = null;
        dropInStreamTask = null;
        dropInStream = null;
        if (speakerIO != null) {
            speakerIO.dropIn(null);
        }
        if (task != null) {
            task.cancel(true);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        logger.debug("Drop-in stopped {}", getId());
    }

    protected void onRemoteSpot() {
        var ks = this.ks;
        if (ks != null) {
            ks.onRemoteSpot();
        }
    }

    private void registerAudioComponent(AudioSink sink) {
        logger.debug("Registering audio sink {}", sink.getId());
        audioComponentRegistrations.put(sink.getId(),
                bundleContext.registerService(AudioSink.class.getName(), sink, new Hashtable<>()));
    }

    private void registerAudioComponent(AudioSource source) {
        logger.debug("Registering audio source {}", source.getId());
        audioComponentRegistrations.put(source.getId(),
                bundleContext.registerService(AudioSource.class.getName(), source, new Hashtable<>()));
    }

    private void unregisterAudioComponent(AudioSink sink) {
        ServiceRegistration<?> sinkReg = audioComponentRegistrations.remove(sink.getId());
        if (sinkReg != null) {
            logger.debug("Unregistering audio sink {}", sink.getId());
            sinkReg.unregister();
        }
    }

    private void unregisterAudioComponent(AudioSource source) {
        ServiceRegistration<?> sourceReg = audioComponentRegistrations.remove(source.getId());
        if (sourceReg != null) {
            logger.debug("Unregistering audio source {}", source.getId());
            sourceReg.unregister();
        }
    }

    private String getSinkId(String id) {
        return SERVICE_ID + "::" + id + "::sink";
    }

    private String getSourceId(String id) {
        return SERVICE_ID + "::" + id + "::source";
    }
}
