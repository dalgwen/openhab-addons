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
package org.openhab.binding.voltronic.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link VoltronicHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Дилян Палаузов - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.voltronic", service = ThingHandlerFactory.class)
public class VoltronicHandlerFactory extends BaseThingHandlerFactory {
    private static final ThingTypeUID THING_TYPE_INVERTER = new ThingTypeUID("voltronic", "inverter");
    private final TranslationProvider translationProvider;
    private final SerialPortManager serialPortManager;

    @Activate
    public VoltronicHandlerFactory(@Reference TranslationProvider translationProvider,
            final @Reference SerialPortManager serialPortManager) {
        this.translationProvider = translationProvider;
        this.serialPortManager = serialPortManager;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return THING_TYPE_INVERTER.equals(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (THING_TYPE_INVERTER.equals(thing.getThingTypeUID())) {
            return new Serial(thing, serialPortManager, translationProvider);
        }

        return null;
    }
}
