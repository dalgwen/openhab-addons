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
package org.openhab.voice.habspeaker.internal.voice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.voice.text.HumanLanguageInterpreter;
import org.openhab.core.voice.text.InterpretationException;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfig;
import org.openhab.voice.habspeaker.internal.config.HABSpeakerConfigProvider;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOClient;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link HABSpeakerLanguageInterpreter} class defines built-in interpreter.
 * The commands need to be configured thought the HABSpeaker service configuration.
 *
 * This interpreter is a speaker scoped interpreter, it will be prepended to the configured interpreted chain,
 * this way its commands have priority over the other ones.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerLanguageInterpreter implements HumanLanguageInterpreter {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerLanguageInterpreter.class);
    private final HABSpeakerIOClient speakerIO;
    private final HABSpeakerIOManager ioManager;
    private final HABSpeakerConfigProvider configProvider;

    public HABSpeakerLanguageInterpreter(HABSpeakerIOClient speakerIO, HABSpeakerIOManager ioManager,
            HABSpeakerConfigProvider configProvider) {
        this.speakerIO = speakerIO;
        this.ioManager = ioManager;
        this.configProvider = configProvider;
    }

    @Override
    public String getId() {
        return "habspeaker::" + speakerIO.getId() + "::hli";
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return "HABSpeaker Language Interpreter";
    }

    @Override
    public String interpret(Locale locale, String text) throws InterpretationException {
        var config = configProvider.getConfig();
        String lowerText = text.toLowerCase();
        boolean interpreted = false;
        logger.debug("Trying to interpret text: {}", text);
        try {
            interpreted = interpretDropInCommands(config, lowerText) || //
                    interpretMediaSearch(config, lowerText) || //
                    interpretMediaTransfer(config, lowerText) || //
                    interpretMediaControl(config, lowerText) || //
                    interpretMediaVolume(config, lowerText);
        } catch (Exception e) {
            logger.warn("Speaker Interpretation error: ", e);
        }
        logger.debug("Text interpreted: {}", interpreted);
        if (!interpreted) {
            throw new InterpretationException("Unknown voice command");
        }
        return config.commandSentMessage;
    }

    private boolean interpretMediaVolume(HABSpeakerConfig config, String lowerText) {
        if (compareTemplate(config.decreaseMediaVolumePhrase, lowerText)) {
            var level = speakerIO.getMediaVolume();
            if (level > config.mediaVolumeStep - 1) {
                speakerIO.setMediaVolume(level - config.mediaVolumeStep);
            } else {
                speakerIO.setMediaVolume(0);
            }
            return true;
        }
        if (compareTemplate(config.increaseMediaVolumePhrase, lowerText)) {
            var level = speakerIO.getMediaVolume();
            if (level < 100 - config.mediaVolumeStep) {
                speakerIO.setMediaVolume(level + config.mediaVolumeStep);
            } else {
                speakerIO.setMediaVolume(100);
            }
            return true;
        }
        return false;
    }

    private boolean interpretMediaControl(HABSpeakerConfig config, String lowerText) {
        @Nullable
        HABSpeakerIOClient commandTarget = speakerIO;
        var mediaState = speakerIO.getMediaState();
        if (mediaState == null || mediaState.playbackState == HABSpeakerIOClient.PlaybackStates.STOPPED) {
            // try target another connected speaker how is playing media
            logger.debug("Speaker not playing media, looking for another");
            commandTarget = ioManager.getSpeakerConnections().stream().filter(filterSpeakerNotStopped())
                    .sorted(sortPlayingFirst()).findAny().orElse(null);
        }
        if (commandTarget == null) {
            logger.debug("No devices playing media");
            return false;
        }
        if (compareTemplate(config.resumeMediaPhrase, lowerText)) {
            commandTarget.playerCommand(PlayPauseType.PLAY);
            return true;
        }
        if (compareTemplate(config.pauseMediaPhrase, lowerText)) {
            commandTarget.playerCommand(PlayPauseType.PAUSE);
            return true;
        }
        if (compareTemplate(config.stopMediaPhrase, lowerText)) {
            commandTarget.playerStop();
            return true;
        }
        if (compareTemplate(config.fastForwardMediaProgressPhrase, lowerText)) {
            commandTarget.playerCommand(RewindFastforwardType.FASTFORWARD);
            return true;
        }
        if (compareTemplate(config.rewindMediaProgressPhrase, lowerText)) {
            commandTarget.playerCommand(RewindFastforwardType.REWIND);
            return true;
        }
        if (compareTemplate(config.nextMediaPhrase, lowerText)) {
            commandTarget.playerCommand(NextPreviousType.NEXT);
            return true;
        }
        if (compareTemplate(config.previousMediaPhrase, lowerText)) {
            commandTarget.playerCommand(NextPreviousType.PREVIOUS);
            return true;
        }
        return false;
    }

    private boolean interpretMediaTransfer(HABSpeakerConfig config, String lowerText) {
        String continueMediaOn = compareTemplateWithParameter(config.continueMediaOnPhrase, lowerText);
        if (!continueMediaOn.isBlank() && continueMediaOn(continueMediaOn)) {
            return true;
        }
        return compareTemplate(config.claimMediaPhrase, lowerText) && claimPlayback();
    }

    private boolean interpretMediaSearch(HABSpeakerConfig config, String lowerText) {
        String audioSearch = compareTemplateWithParameter(config.listenAudioPhrase, lowerText);
        var speakerThing = speakerIO.getThingHandler();
        if (!audioSearch.isBlank() && speakerThing != null) {
            speakerThing.updateMusicSearchChannel(audioSearch);
            return true;
        }
        String webVideoSearch = compareTemplateWithParameter(config.watchVideoPhrase, lowerText);
        if (!webVideoSearch.isBlank() && speakerThing != null) {
            speakerThing.updateVideoSearchChannel(webVideoSearch);
            return true;
        }
        return false;
    }

    private boolean interpretDropInCommands(HABSpeakerConfig config, String lowerText) {
        if (speakerIO.getDropIn() == null) {
            String speakerName = compareTemplateWithParameter(config.startDropInPhrase, lowerText);
            if (!speakerName.isBlank()) {
                var optionalTargetSpeaker = ioManager.getSpeakerConnections().stream() //
                        .filter(filterTargetSpeaker(speakerName)) //
                        .findAny();
                if (optionalTargetSpeaker.isPresent()) {
                    speakerIO.dropIn(optionalTargetSpeaker.get());
                    return true;
                }
            }
        } else {
            if (compareTemplate(config.stopDropInPhrase, lowerText)) {
                speakerIO.dropIn(null);
                return true;
            }
        }
        return false;
    }

    private boolean claimPlayback() {
        var sourceSpeaker = ioManager.getSpeakerConnections().stream().filter(filterSpeakerNotStopped())
                .sorted(sortPlayingFirst()).findAny().orElse(null);
        var sourceMediaState = sourceSpeaker != null ? sourceSpeaker.getMediaState() : null;
        if (sourceSpeaker != null && sourceMediaState != null) {
            var provider = sourceMediaState.provider;
            if (provider != null) {
                // Claim media on current speaker
                speakerIO.playerClaim(provider);
                return true;
            }
        }
        return false;
    }

    private boolean continueMediaOn(String continueMediaOn) {
        var optionalTargetSpeaker = ioManager.getSpeakerConnections().stream()
                .filter(filterTargetSpeaker(continueMediaOn)).findAny();
        if (optionalTargetSpeaker.isPresent()) {
            var targetSpeaker = optionalTargetSpeaker.get();
            // Prioritize current speaker media state
            var mediaSourceSpeaker = speakerIO;
            var mediaState = speakerIO.getMediaState();
            if (mediaState == null) {
                // Search for another speaker currently playing media
                mediaSourceSpeaker = ioManager.getSpeakerConnections().stream() //
                        .filter(filterSpeakerNotStopped()) //
                        .sorted(sortPlayingFirst()) //
                        .findAny().orElse(null);
                if (mediaSourceSpeaker != null) {
                    mediaState = mediaSourceSpeaker.getMediaState();
                }
            }
            if (mediaState != null) {
                var provider = mediaState.provider;
                if (provider != null) {
                    // For providers that support media claim by itself
                    targetSpeaker.playerClaim(provider);
                    return true;
                }
            }
        }
        return false;
    }

    private Predicate<HABSpeakerIOClient> filterSpeakerNotStopped() {
        return io -> {
            var mediaState = io.getMediaState();
            return mediaState != null && (mediaState.playbackState == HABSpeakerIOClient.PlaybackStates.PLAYING
                    || mediaState.playbackState == HABSpeakerIOClient.PlaybackStates.PAUSED);
        };
    }

    private Comparator<HABSpeakerIOClient> sortPlayingFirst() {
        return (a, b) -> {
            var aMediaState = a.getMediaState();
            var bMediaState = b.getMediaState();
            if (aMediaState != null && bMediaState != null
                    && aMediaState.playbackState == HABSpeakerIOClient.PlaybackStates.PLAYING
                    && bMediaState.playbackState == HABSpeakerIOClient.PlaybackStates.PLAYING) {
                return 0;
            }
            return aMediaState != null && aMediaState.playbackState == HABSpeakerIOClient.PlaybackStates.PLAYING ? 1
                    : -1;
        };
    }

    private Predicate<HABSpeakerIOClient> filterTargetSpeaker(String speakerName) {
        return io -> {
            var handler = io.getThingHandler();
            if (handler == null) {
                return false;
            }
            return speakerName.equalsIgnoreCase(handler.getLabel()) || //
                    speakerName.equalsIgnoreCase(handler.getLocationLabel());
        };
    }

    private String compareTemplateWithParameter(String template, String lowerText) {
        if (template.isBlank()) {
            return "";
        }
        return Arrays.stream(template.split(";")) //
                .map(templateOption -> Pattern.compile( //
                        templateOption.trim().toLowerCase().replace("$*", "(?<search>.*)") //
                ).matcher(lowerText)) //
                .filter(Matcher::matches).findAny().map(m -> m.group("search")).orElse("");
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

    // Search media

    /**
     * Try to search on a json media.
     * 
     * @param mediaPath path of the media file
     * @param name key on the media file
     * @return value for the provided name on the media file or empty
     */
    private String searchMediaFile(Path mediaPath, String name) {
        var mediaFile = mediaPath.toFile();
        if (mediaFile.exists()) {
            try {
                var mediaMap = new ObjectMapper().readValue(mediaFile, new TypeReference<HashMap<String, String>>() {
                });
                var value = mediaMap.get(name);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (IOException e) {
                logger.warn("Unable to read media file: {}", e.getMessage());
            }
        }
        return "";
    }
}
