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
package org.openhab.voice.habspeaker.internal.handler;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.audio.AudioManager;
import org.openhab.core.auth.UserRegistry;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.voice.VoiceManager;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIO;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOManager;
import org.openhab.voice.habspeaker.internal.io.HABSpeakerIOProtocolListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HabSpeakerHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.habspeaker", property = Constants.SERVICE_PID
        + "=" + SERVICE_PID)
@NonNullByDefault
public class HabSpeakerHandlerFactory extends BaseThingHandlerFactory implements HABSpeakerIOProtocolListener {
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(SPEAKER_THING_TYPE);
    private final Logger logger = LoggerFactory.getLogger(HabSpeakerHandlerFactory.class);
    private final Map<String, HABSpeakerThingHandler> speakerHandlers = new ConcurrentHashMap<>();
    private final HABSpeakerIOManager ioManager;

    @Activate
    public HabSpeakerHandlerFactory(BundleContext bundleContext, final @Reference HttpService httpService,
            final @Reference AudioManager audioManager, final @Reference VoiceManager voiceManager,
            final @Reference UserRegistry userRegistry, @Reference HABSpeakerIOManager ioManager) {
        ioManager.setProtocolListener(this);
        this.ioManager = ioManager;
    }
    
    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(SPEAKER_THING_TYPE)) {
            var handler = new HABSpeakerThingHandler(thing);
            var speakerIO = ioManager.getSpeakerConnection(handler.getSpeakerId());
            handler.setSpeakerIO(speakerIO);
            speakerHandlers.put(handler.getSpeakerId(), handler);
            return handler;
        }
        return null;
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        super.removeHandler(thingHandler);
    }

    @Override
    public void onConnected(HABSpeakerIO speaker) {
        var handler = speakerHandlers.get(speaker.getId());
        if (handler != null) {
            logger.debug("connecting speaker {} handler", speaker.getId());
            handler.setSpeakerIO(speaker);
            handler.updateStatus();
        }
    }

    @Override
    public void onDisconnected(HABSpeakerIO speaker) {
        var handler = speakerHandlers.get(speaker.getId());
        if (handler != null) {
            logger.debug("disconnecting speaker {} handler", speaker.getId());
            handler.setSpeakerIO(null);
            handler.updateStatus();
        }
    }
}
