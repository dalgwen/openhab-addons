package org.openhab.binding.voltronic.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(scope = ServiceScope.PROTOTYPE, service = VoltronicActions.class)
@ThingActionsScope(name = "voltronic")
@NonNullByDefault
public class VoltronicActions implements ThingActions {
    private final Logger logger = LoggerFactory.getLogger(VoltronicActions.class);
    private @Nullable Base handler;

    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (Base) handler;
    }

    @Nullable
    public ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "@text/action.label", description = "@text/action.description")
    @Nullable
    public @ActionOutput(label = "@text/action.result", type = "java.lang.String") String voltronicSend(
            @ActionInput(name = "command", label = "@text/action.param-label") String command) {
        Base voltronicHandler = handler;
        if (voltronicHandler == null) {
            logger.warn("VoltronicAction service ThingHandler is null");
            return null;
        }
        return voltronicHandler.sendCommand(command);
    }

    @Nullable
    static public String voltronicSend(ThingActions actions, String command) {
        return ((VoltronicActions) actions).voltronicSend(command);
    }
}
