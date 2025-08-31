package org.openhab.binding.mini2.internal;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
@Component(configurationPid = "binding.mini2", service = ThingHandlerFactory.class)
public class mini2HandlerFactory extends BaseThingHandlerFactory {
    private static final Logger logger = LoggerFactory.getLogger(mini2HandlerFactory.class);

    @Activate
    public mini2HandlerFactory(BundleContext bundleContext, Map<String, Object> properties) {
        logger.warn("LOADED");
    }

    @Deactivate
    protected void unload() {
        logger.warn("UNLOAD");
    }

    @Modified
    protected void modified(Map<String, ?> config) {
        logger.warn("MODIFIED");
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return false;
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        return null;
    }
}
