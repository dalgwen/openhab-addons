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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.audio.AudioStream;
import org.openhab.core.voice.KSException;
import org.openhab.core.voice.KSListener;
import org.openhab.core.voice.KSService;
import org.openhab.core.voice.KSServiceHandle;
import org.openhab.core.voice.KSpottedEvent;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerKS} class defines the speaker Audio Sink
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class HABSpeakerKS implements KSService {
    private static final HashSet<AudioFormat> SUPPORTED_FORMATS = new HashSet<>();

    static {
        SUPPORTED_FORMATS.add(AudioFormat.WAV);
    }
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerKS.class);
    private final HABSpeakerIOClient speakerIO;
    private final @Nullable KSService serverKsService;
    private @Nullable KSServiceHandle serverKsServiceHandler;
    private @Nullable KSListener ksListener;

    public HABSpeakerKS(HABSpeakerIOClient speakerIO, @Nullable KSService serverKsService) {
        this.speakerIO = speakerIO;
        this.serverKsService = serverKsService;
    }

    public void onRemoteSpot() {
        var ksListener = this.ksListener;
        if (ksListener != null) {
            ksListener.ksEventReceived(new KSpottedEvent());
        }
    }

    @Override
    public String getId() {
        return "habspeaker::" + speakerIO.getId() + "::ks";
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return "HABSpeaker KS";
    }

    @Override
    public Set<Locale> getSupportedLocales() {
        return serverKsService != null ? serverKsService.getSupportedLocales() : Set.of();
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return serverKsService != null ? serverKsService.getSupportedFormats() : SUPPORTED_FORMATS;
    }

    @Override
    public KSServiceHandle spot(KSListener ksListener, AudioStream audioStream, Locale locale, String keyword)
            throws KSException {
        this.ksListener = ksListener;
        var serverKsService = this.serverKsService;
        if (serverKsService == null) {
            logger.debug("speaker {}: keyword spotting is disabled", speakerIO.getId());
            try {
                audioStream.close();
            } catch (IOException ignored) {
            }
        } else {
            logger.debug("speaker {}: keyword spotting is running on the server using {}", speakerIO.getId(),
                    serverKsService.getId());
            serverKsServiceHandler = serverKsService.spot(ksListener, audioStream, locale, keyword);
        }
        return () -> {
            if (serverKsServiceHandler != null) {
                serverKsServiceHandler.abort();
            }
            speakerIO.disconnect();
        };
    }
}
