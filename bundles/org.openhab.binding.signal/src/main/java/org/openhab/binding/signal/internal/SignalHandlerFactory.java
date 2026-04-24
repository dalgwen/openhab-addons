/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.signal.internal.handler.SignalBridgeHandler;
import org.openhab.binding.signal.internal.handler.SignalConversationHandler;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.SignalAccount;
import org.openhab.binding.signal.internal.protocol.SignalAccountManager;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SignalHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@Component(configurationPid = "binding.signal", service = ThingHandlerFactory.class)
@ConfigurableService(category = "binding", label = "Signal", description_uri = "binding:signal")
@NonNullByDefault
public class SignalHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(BaseThingHandlerFactory.class);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Stream
            .concat(SignalBridgeHandler.SUPPORTED_THING_TYPES_UIDS.stream(),
                    Stream.of(SignalConversationHandler.SUPPORTED_THING_TYPES_UIDS))
            .collect(Collectors.toSet());

    private final HttpClient httpClient;
    private final SignalAccountManager signalAccountManager;

    @Activate
    public SignalHandlerFactory(@Reference final HttpClientFactory httpClientFactory, Map<String, ?> config) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        SignalConfiguration.Kind kind = ConfigParser.valueAsOrElse(config.get(SignalConfiguration.CFG_KIND), SignalConfiguration.Kind.class, SignalConfiguration.Kind.MANAGED);
        String signalCliConnectionConfiguration = ConfigParser.valueAsOrElse(config.get(SignalConfiguration.CFG_CONFIGURATION), String.class, "");
        signalAccountManager = new SignalAccountManager();
        SignalService signalService = SignalService.createSignalService(signalAccountManager, kind, signalCliConnectionConfiguration, httpClient);
        signalAccountManager.setSignalService(signalService);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (SignalBridgeHandler.SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)) {
            return new SignalBridgeHandler((Bridge) thing, thingTypeUID, httpClient, signalAccountManager);
        } else if (SignalConversationHandler.SUPPORTED_THING_TYPES_UIDS.equals(thingTypeUID)) {
            return new SignalConversationHandler(thing);
        }
        return null;
    }

    @Override
    public @Nullable Thing createThing(ThingTypeUID thingTypeUID, Configuration configuration,
            @Nullable ThingUID thingUID, @Nullable ThingUID bridgeUID) {
        if (SignalBridgeHandler.SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)) {
            return super.createThing(thingTypeUID, configuration, thingUID, null);
        }
        if (SignalConversationHandler.SUPPORTED_THING_TYPES_UIDS.equals(thingTypeUID)) {
            if (bridgeUID != null) {
                ThingUID safethingUID = thingUID == null
                        ? getSignalConversationUID(thingTypeUID,
                                (String) configuration
                                        .get(SignalBindingConstants.SIGNALCONVERSATION_PARAMETER_RECIPIENT),
                                bridgeUID)
                        : thingUID;
                return super.createThing(thingTypeUID, configuration, safethingUID, bridgeUID);
            } else {
                throw new IllegalArgumentException("Cannot create a SignalConversation without a Signal bridge");
            }
        }
        throw new IllegalArgumentException("The thing type " + thingTypeUID + " is not supported by the binding.");
    }

    public static ThingUID getSignalConversationUID(ThingTypeUID thingTypeUID, String recipient, ThingUID bridgeUID) {
        return new ThingUID(thingTypeUID, recipient, bridgeUID.getId());
    }

    @Modified
    protected void modified(Map<String, ?> config) {
        SignalConfiguration.Kind kind = ConfigParser.valueAsOrElse(config.get(SignalConfiguration.CFG_KIND), SignalConfiguration.Kind.class, SignalConfiguration.Kind.MANAGED);
        String signalCliConnectionConfiguration = ConfigParser.valueAsOrElse(config.get(SignalConfiguration.CFG_CONFIGURATION), String.class, "");
        SignalService newSignalService = SignalService.createSignalService(signalAccountManager, kind, signalCliConnectionConfiguration, httpClient);
        signalAccountManager.setSignalService(newSignalService);
        for (SignalAccount account : signalAccountManager.getAllAccounts()) {
            try {
                account.check();
            } catch (IncompleteRegistrationException | IOException e) {
                logger.warn("Account not ready");
            }
        }
    }
}
