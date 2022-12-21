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
package org.openhab.voice.habspeaker.internal.io.internal;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.SERVICE_ID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.audio.AudioException;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.audio.AudioSource;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.voice.KSService;
import org.openhab.core.voice.STTService;
import org.openhab.core.voice.TTSService;
import org.openhab.core.voice.Voice;
import org.openhab.core.voice.VoiceManager;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSink;
import org.openhab.voice.habspeaker.internal.audio.HABSpeakerAudioSource;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOHandler;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerKS;
import org.openhab.voice.habspeaker.internal.voice.HABSpeakerLanguageInterpreter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerIOBase} represents a speaker active connection.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public abstract class HABSpeakerIOBase implements HABSpeakerIO {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerIOBase.class);
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
    private @Nullable HABSpeakerIO dropInSpeakerIO = null;
    private @Nullable AudioStream dropInStream = null;
    private @Nullable Future<?> dropInStreamTask = null;

    public HABSpeakerIOBase(AudioManager audioManager, VoiceManager voiceManager, HttpClient httpClient,
            BundleContext bundleContext, HABSpeakerConfigProvider configProvider) {
        this.audioManager = audioManager;
        this.voiceManager = voiceManager;
        this.bundleContext = bundleContext;
        this.configProvider = configProvider;
        this.speakerLanguageInterpreter = new HABSpeakerLanguageInterpreter(this, configProvider, httpClient);
    }

    @Override
    public void setThingHandler(@Nullable HABSpeakerIOHandler handler) {
        thingHandler = handler;
    }

    protected @Nullable Map<String, Object> getSpeakerConfig(@Nullable HABSpeakerIOHandler handler) {
        if (handler == null) {
            return null;
        }
        var config = handler.getSpeakerConfig();
        var initializedConfig = new HashMap<String, Object>();
        var currentVolume = handler.getSinkVolume();
        initializedConfig.put("sinkVolume", currentVolume != null ? currentVolume : config.sinkVolume);
        initializedConfig.put("screenSaverTime", config.screenSaverTime);
        initializedConfig.put("spotifyToken", handler.getSpotifyToken());
        var label = handler.getLabel();
        if (label != null) {
            initializedConfig.put("label", label);
        }
        if (!handler.getSpeakerConfig().ks.isBlank() && voiceManager.getKS(handler.getSpeakerConfig().ks) != null) {
            serverSpotting = true;
            initializedConfig.put("remoteSpot", true);
        }
        return initializedConfig;
    }

    protected synchronized void registerSpeakerComponents(String id, long requiredSinkSampleRate,
            @Nullable HABSpeakerIOHandler thingHandler) throws IOException {
        if (id.isBlank()) {
            throw new IOException("Unable to register audio components");
        }
        String label = "HAB Speaker (" + id + ")";
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
        var source = new HABSpeakerAudioSource(getSourceId(id), label, this);
        registerAudioComponent(source);
        // register sink
        var sink = new HABSpeakerAudioSink(getSinkId(id), label, this, sinkStereo ? 2 : 1);
        registerAudioComponent(sink);
        // init dialog
        STTService stt = null;
        TTSService tts = null;
        Voice voice = null;
        KSService ks = null;
        List<HumanLanguageInterpreter> hlis = List.of(speakerLanguageInterpreter);
        if (thingHandler != null) {
            var speakerConfig = thingHandler.getSpeakerConfig();
            if (!speakerConfig.stt.isBlank()) {
                stt = voiceManager.getSTT(speakerConfig.stt);
            }
            if (!speakerConfig.tts.isBlank()) {
                tts = voiceManager.getTTS(speakerConfig.tts);
            }
            if (!speakerConfig.voice.isBlank()) {
                voice = voiceManager.getAllVoices().stream().filter(v -> v.getUID().equals(speakerConfig.voice))
                        .findAny().orElse(null);
            }
            HumanLanguageInterpreter hli = !speakerConfig.hli.isBlank() ? voiceManager.getHLI(speakerConfig.hli)
                    : voiceManager.getHLI();
            if (hli != null) {
                hlis = List.of(speakerLanguageInterpreter, hli);
            }
            if (!speakerConfig.ks.isBlank()) {
                ks = voiceManager.getKS(speakerConfig.ks);
            }
        }
        var hsKS = new HABSpeakerKS(this, ks);
        this.ks = hsKS;
        voiceManager.startDialog(hsKS, stt, tts, voice, hlis, source, sink, null, null, listeningItem);
    }

    protected synchronized void unregisterSpeakerComponents(String id) {
        if (initialized) {
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
    public @Nullable HABSpeakerIO getDropIn() {
        return dropInSpeakerIO;
    }

    @Override
    public synchronized void dropIn(@Nullable HABSpeakerIO speakerIO) {
        if (speakerIO == null) {
            stopDropIn();
            return;
        }
        if (dropInSpeakerIO != null) {
            throw new IllegalStateException("Unable to drop-in, speaker is occupied");
        }
        dropInSpeakerIO = speakerIO;
        var dropInSink = audioManager.getSink(getSinkId(speakerIO.getId()));
        var source = audioManager.getSource(getSourceId(getId()));
        if (dropInSink == null || source == null) {
            stopDropIn();
            throw new IllegalStateException("Unable to drop-in to speaker, missing audio components");
        }
        dropInStreamTask = scheduler.submit(() -> {
            logger.debug("Starting drop-in to {}", speakerIO.getId());
            try {
                dropInSink.process(source.getInputStream(HABSpeakerAudioSource.HABSPEAKER_SOURCE_FORMAT));
            } catch (AudioException | ArrayIndexOutOfBoundsException e) {
                logger.warn("{} while running drop-in {}: ", e.getClass().getName(), getId(), e);
                stopDropIn();
            }
        });
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
