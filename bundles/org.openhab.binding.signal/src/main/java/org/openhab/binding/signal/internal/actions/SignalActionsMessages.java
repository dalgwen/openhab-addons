package org.openhab.binding.signal.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.handler.SignalBridgeHandler;
import org.openhab.binding.signal.internal.protocol.DeliveryReport;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
import org.openhab.core.automation.annotation.ActionOutputs;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link SignalActionsMessages} exposes some messages actions for both main and linked accounts
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public abstract class SignalActionsMessages implements ThingActions {

    protected @NonNullByDefault({}) SignalBridgeHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (SignalBridgeHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(SignalActionsMessages.class);


    @RuleAction(label = "Send Message With Signal", description = "Send a message with Signal")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: message sent. KO: error during sending"),}) Map<String, Object> sendSignal(
            @ActionInput(name = "recipient", label = "recipient", description = "Recipient of the message") @Nullable String recipient,
            @ActionInput(name = "message", label = "message", description = "Message to send") @Nullable String message) {
        Map<String, Object> resultMap = new HashMap<>();
        if (recipient != null && !recipient.isEmpty() && message != null) {
            DeliveryReport report = handler.send(recipient, message);
            switch (report.deliveryStatus) {
                case DELIVERED:
                case READ:
                case SENT:
                    resultMap.put("RESULT", "OK");
                    break;
                case FAILED:
                case UNKNOWN:
                    resultMap.put("RESULT", "KO");
                    break;
            }
        } else {
            resultMap.put("RESULT", "KO");
            logger.error("Signal cannot send a message with no recipient or text");
        }
        return resultMap;
    }

    public static Map<String, Object> sendSignal(@Nullable ThingActions actions, @Nullable String recipient,
                                                       @Nullable String message) {
        if (actions instanceof SignalActionsMessages) {
            return ((SignalActionsMessages) actions).sendSignal(recipient, message);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMessage class.");
        }
    }

    @RuleAction(label = "Send Image With Signal", description = "Send an Image with Signal")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: message sent. KO: error during sending")}) Map<String, Object> sendSignalImage(
            @ActionInput(name = "recipient", label = "recipient", description = "Recipient of the message") @Nullable String recipient,
            @ActionInput(name = "image", label = "image", description = "Image to send") @Nullable String image,
            @ActionInput(name = "text", label = "text", description = "Text to send") @Nullable String text) {
        Map<String, Object> resultMap = new HashMap<>();

        if (recipient != null && !recipient.isEmpty() && image != null) {
            DeliveryReport report = handler.sendImage(recipient, image, text);
            switch (report.deliveryStatus) {
                case DELIVERED:
                case READ:
                case SENT:
                    resultMap.put("RESULT", "OK");
                    break;
                case FAILED:
                case UNKNOWN:
                    resultMap.put("RESULT", "KO");
                    break;
            }
        } else {
            logger.error("Signal cannot send a photo with no recipient or text");
            resultMap.put("RESULT", "KO");
        }
        return resultMap;
    }

    public static Map<String, Object> sendSignalImage(@Nullable ThingActions actions, @Nullable String recipient,
                                                            @Nullable String image, @Nullable String text) {
        if (actions instanceof SignalActionsMessages) {
            return ((SignalActionsMessages) actions).sendSignalImage(recipient, image, text);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMessage class.");
        }
    }
}
