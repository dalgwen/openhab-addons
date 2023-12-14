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
package org.openhab.binding.habspeaker.internal.discovery;

import static org.openhab.binding.habspeaker.internal.HABSpeakerConstants.SPEAKER_THING_TYPE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.habspeaker.internal.io.HABSpeakerIOConnection;
import org.openhab.binding.habspeaker.internal.io.HABSpeakerIOManager;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerDiscoveryService} discover speakers connected to the server.
 *
 * @author Miguel Alvarez - Initial contribution
 */
@NonNullByDefault
@Component(service = DiscoveryService.class, configurationPid = "discovery.habspeaker")
public class HABSpeakerDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerDiscoveryService.class);
    private static final long DISCOVERY_RESULT_TTL_SEC = 300;
    private final HABSpeakerIOManager wsAdapter;

    @Activate
    public HABSpeakerDiscoveryService(@Reference HABSpeakerIOManager wsAdapter) throws IllegalArgumentException {
        super(Set.of(SPEAKER_THING_TYPE), 10);
        this.wsAdapter = wsAdapter;
    }

    @Override
    protected void startScan() {
        wsAdapter.getSpeakerConnections().forEach(this::discoverDevice);
    }

    public void discoverDevice(HABSpeakerIOConnection speaker) {
        var id = speaker.getId();
        logger.debug("Speaker {} discovered", id);
        Map<String, Object> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, id);
        thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(SPEAKER_THING_TYPE, id))
                .withTTL(DISCOVERY_RESULT_TTL_SEC).withRepresentationProperty(Thing.PROPERTY_SERIAL_NUMBER)
                .withProperties(properties).withLabel("HABSpeaker (" + id + ")").build());
    }

    public void activate() {
        activate(new HashMap<>());
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }
}
