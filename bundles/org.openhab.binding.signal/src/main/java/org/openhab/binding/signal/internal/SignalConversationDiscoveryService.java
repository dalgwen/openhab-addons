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
package org.openhab.binding.signal.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.handler.SignalBridgeHandler;
import org.openhab.binding.signal.internal.handler.SignalConversationHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;

/**
 * This class implements a discovery service for SignalConversation
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalConversationDiscoveryService extends AbstractDiscoveryService
        implements DiscoveryService, ThingHandlerService {

    private @NonNullByDefault({}) SignalBridgeHandler bridgeHandler;
    private @NonNullByDefault({}) ThingUID bridgeUid;

    public SignalConversationDiscoveryService() {
        super(0);
    }

    public SignalConversationDiscoveryService(int timeout) throws IllegalArgumentException {
        super(timeout);
    }

    @Override
    protected void startScan() {
        for (String msisdn : bridgeHandler.getAllSender()) {
            buildDiscovery(msisdn);
        }
    }

    public void buildDiscovery(String identifier) {
        String sanitizedIdentifier = identifier.replaceAll("\\+", "");
        ThingUID thingUID = SignalHandlerFactory.getSignalConversationUID(
                SignalBindingConstants.SIGNALCONVERSATION_THING_TYPE, sanitizedIdentifier, bridgeUid);
        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                .withProperty(SignalBindingConstants.SIGNALCONVERSATION_PARAMETER_RECIPIENT, sanitizedIdentifier)
                .withLabel("Conversation with " + identifier).withBridge(bridgeUid)
                .withThingType(SignalBindingConstants.SIGNALCONVERSATION_THING_TYPE)
                .withRepresentationProperty(SignalBindingConstants.SIGNALCONVERSATION_PARAMETER_RECIPIENT).build();
        thingDiscovered(result);
    }

    public void buildByAutoDiscovery(String sender) {
        if (isBackgroundDiscoveryEnabled()) {
            buildDiscovery(sender);
        }
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return Set.of(SignalConversationHandler.SUPPORTED_THING_TYPES_UIDS);
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.bridgeHandler = (SignalBridgeHandler) handler;
        this.bridgeUid = handler.getThing().getUID();
        this.bridgeHandler.setDiscoveryService(this);
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }
}
