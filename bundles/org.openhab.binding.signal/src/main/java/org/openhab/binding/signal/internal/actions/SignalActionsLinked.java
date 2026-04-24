package org.openhab.binding.signal.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.handler.SignalBridgeHandler;
import org.openhab.binding.signal.internal.protocol.DeliveryReport;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.ActionOutputs;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@link SignalActionsLinked} exposes some registration action for linked account
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
@ThingActionsScope(name = "signal")
public class SignalActionsLinked extends SignalActionsMessages implements ThingActions{

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(SignalActionsLinked.class);

    @RuleAction(label = "Register linked account", description = "Try to register a linked number to an existing signal account")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: registration request sent. KO: error during registration attempt"),
            @ActionOutput(name = "ERROR", label = "Error", type = "java.lang.String", description = "Error message")}) Map<String, Object> registerLinked(
    ) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            handler.registerLinked();
            resultMap.put("RESULT", "OK");
        } catch (IncompleteRegistrationException | IOException e) {
            resultMap.put("RESULT", "KO");
            logger.error("Cannot register linked account", e);
            resultMap.put("ERROR", "Cannot update account: " + e.getMessage());
        }
        return resultMap;
    }

    public Map<String, Object> registerLinked(@Nullable ThingActions actions) {
        if (actions instanceof SignalActionsLinked) {
            return ((SignalActionsLinked) actions).registerLinked();
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsLinked class.");
        }
    }
}
