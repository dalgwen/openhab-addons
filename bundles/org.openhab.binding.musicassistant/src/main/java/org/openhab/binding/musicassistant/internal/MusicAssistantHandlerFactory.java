/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.musicassistant.internal;

import static org.openhab.binding.musicassistant.internal.MusicAssistantBindingConstants.*;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.musicassistant.internal.handler.MusicAssistantPlayerHandler;
import org.openhab.binding.musicassistant.internal.handler.MusicAssistantServerHandler;
import org.openhab.core.audio.AudioHTTPServer;
import org.openhab.core.audio.AudioSink;
import org.openhab.core.net.HttpServiceUtil;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MusicAssistantHandlerFactory} is responsible for creating things and
 * thing handlers.
 *
 * @author Dan Cunningham - Initial contribution
 * @author Mark Hilbush - Cancel request player job when handler removed
 * @author Mark Hilbush - Add callbackUrl
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.musicassistant")
@NonNullByDefault
public class MusicAssistantHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(MusicAssistantHandlerFactory.class);

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Stream
            .concat(MusicAssistantServerHandler.SUPPORTED_THING_TYPES_UIDS.stream(),
                    MusicAssistantPlayerHandler.SUPPORTED_THING_TYPES_UIDS.stream())
            .collect(Collectors.toSet());

    private final AudioHTTPServer audioHTTPServer;
    private final NetworkAddressService networkAddressService;
    private final MusicAssistantStateDescriptionOptionsProvider stateDescriptionProvider;

    private Map<String, ServiceRegistration<AudioSink>> audioSinkRegistrations = new ConcurrentHashMap<>();

    // Callback url (scheme+server+port) to use for playing notification sounds
    private @Nullable String callbackUrl = null;

    @Activate
    public MusicAssistantHandlerFactory(final @Reference AudioHTTPServer audioHTTPServer,
            final @Reference NetworkAddressService networkAddressService,
            final @Reference MusicAssistantStateDescriptionOptionsProvider stateDescriptionProvider) {
        this.audioHTTPServer = audioHTTPServer;
        this.networkAddressService = networkAddressService;
        this.stateDescriptionProvider = stateDescriptionProvider;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);
        Dictionary<String, Object> properties = componentContext.getProperties();
        callbackUrl = (String) properties.get("callbackUrl");
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (MUSICASSISTANTSERVER_THING_TYPE.equals(thingTypeUID)) {
            logger.trace("Creating handler for bridge thing {}", thing);
            return new MusicAssistantServerHandler((Bridge) thing);
        }

        if (MUSICASSISTANTPLAYER_THING_TYPE.equals(thingTypeUID)) {
            logger.trace("Creating handler for player thing {}", thing);
            MusicAssistantPlayerHandler playerHandler = new MusicAssistantPlayerHandler(thing, createCallbackUrl(),
                    stateDescriptionProvider);

            // Register the player as an audio sink
            logger.trace("Registering an audio sink for player thing {}", thing.getUID());
            MusicAssistantAudioSink audioSink = new MusicAssistantAudioSink(playerHandler, audioHTTPServer, callbackUrl);
            ServiceRegistration<AudioSink> audioSinkRegistration = bundleContext.registerService(AudioSink.class,
                    audioSink, new Hashtable<>());
            audioSinkRegistrations.put(thing.getUID().toString(), audioSinkRegistration);

            return playerHandler;
        }

        return null;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof MusicAssistantPlayerHandler playerHandler) {
            MusicAssistantServerHandler bridge = playerHandler.getMusicAssistantServerHandler();
            if (bridge != null) {
                // Unregister the player's audio sink
                logger.trace("Unregistering the audio sync service for player thing {}",
                        thingHandler.getThing().getUID());
                ServiceRegistration<AudioSink> audioSinkRegistration = audioSinkRegistrations
                        .remove(thingHandler.getThing().getUID().toString());
                if (audioSinkRegistration != null) {
                    audioSinkRegistration.unregister();
                }

                logger.trace("Removing handler for player thing {}", thingHandler.getThing());
                bridge.removePlayerCache(playerHandler.getMac());
            }
        }
    }

    private @Nullable String createCallbackUrl() {
        final String ipAddress = networkAddressService.getPrimaryIpv4HostAddress();
        if (ipAddress == null) {
            logger.warn("No network interface could be found.");
            return null;
        }

        final int port = HttpServiceUtil.getHttpServicePort(bundleContext);
        if (port == -1) {
            logger.warn("Cannot find port of the http service.");
            return null;
        }

        return "http://" + ipAddress + ":" + port;
    }
}
