/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.wyoming.internal;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.WyomingClient;
import org.openhab.binding.wyoming.internal.protocol.WyomingProtocolException;
import org.openhab.binding.wyoming.internal.protocol.WyomingStateListener;
import org.openhab.binding.wyoming.internal.protocol.message.data.InfoData;
import org.openhab.binding.wyoming.internal.protocol.sound.SoundManager;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WyomingHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class WyomingHandler extends BaseThingHandler implements WyomingStateListener {

    private final Logger logger = LoggerFactory.getLogger(WyomingHandler.class);
    private final BundleContext bundleContext;

    @Nullable
    private WyomingClient wyomingClient;

    private boolean hasSatellite = false;
    private boolean hasMicrophone = false;
    private boolean hasSound = false;
    private boolean hasWake = false;
    private boolean hasSTT = false;
    private boolean hasTTS = false;
    @Nullable
    private ServiceRegistration<AudioSink> audioSinkRegistration;

    public WyomingHandler(Thing thing, BundleContext bundleContext) {
        super(thing);
        this.bundleContext = bundleContext;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void dispose() {
        ServiceRegistration<AudioSink> audioSinkRegistrationLocal = audioSinkRegistration;
        if (audioSinkRegistrationLocal != null) {
            audioSinkRegistrationLocal.unregister();
        }
        Optional.ofNullable(wyomingClient).ifPresent(WyomingClient::disconnectAndStop);
        super.dispose();
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);

        var configLocal = getConfigAs(WyomingConfiguration.class);

        var wyomingClientLocal = new WyomingClient(configLocal.hostname, configLocal.port);
        wyomingClient = wyomingClientLocal;
        wyomingClientLocal.registerStateListener(this);

        scheduler.execute(() -> {
            try {
                analyzeAndCreateServices();
            } catch (IOException | WyomingProtocolException e) {
                logger.error("Error analyzing and creating services", e);
                wyomingClientLocal.disconnectAndStop();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        });
    }

    private void analyzeAndCreateServices() throws IOException, WyomingProtocolException {

        WyomingClient wyomingClientLocal = Optional.ofNullable(wyomingClient)
                .orElseThrow(() -> new IllegalStateException(
                        "WyomingClient is null, cannot analyze and create services. Should not happen"));

        wyomingClientLocal.connectAndListen(true);
        InfoData data = wyomingClientLocal.getInfoData();

        if (data.getSatellite() != null) {
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_SND, "true");
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_MIC, "true");
            hasSound = true;
            hasMicrophone = true;
            hasSatellite = true;
        }
        if (data.getMic() != null) {
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_MIC, "true");
            hasMicrophone = true;
        }
        if (data.getSnd() != null) {
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_SND, "true");
            hasSound = true;
            WyomingAudioSink wyomingAudioSink = new WyomingAudioSink(this, new SoundManager(wyomingClientLocal),
                    scheduler);
            audioSinkRegistration = (ServiceRegistration<AudioSink>) bundleContext
                    .registerService(AudioSink.class.getName(), wyomingAudioSink, new Hashtable<>());
        }
        if (data.getWake() != null) {
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_WAKE, "true");
            hasWake = true;
        }
        if (data.getAsr() != null) {
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_STT, "true");
            hasSTT = true;
        }
        if (data.getTts() != null) {
            thing.setProperty(WyomingBindingConstants.THING_PROPERTY_TTS, "true");
            hasTTS = true;
        }
    }

    @Override
    public void onState(WyomingState state, @Nullable String detailedMessage) {
        switch (state) {
            case Ready -> {
                updateStatus(ThingStatus.ONLINE);
            }
            case Disconnected -> {
                updateStatus(ThingStatus.OFFLINE);
            }
            case ProtocolError -> {
                updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.COMMUNICATION_ERROR, detailedMessage);
            }
        }
    }
}
