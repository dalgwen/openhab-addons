package org.openhab.binding.signal.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.handler.SignalBridgeHandler;
import org.openhab.binding.signal.internal.protocol.DeliveryReport;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.RegistrationType;
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
 * The {@link SignalActionsMain} exposes some registration action for main account
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
@ThingActionsScope(name = "signal")
public class SignalActionsMain implements ThingActions {

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(SignalActionsMain.class);

    private @NonNullByDefault({}) SignalBridgeHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (SignalBridgeHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "Register account", description = "Try to register a new number to the signal service")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: registration request sent. KO: error during registration attempt"),
            @ActionOutput(name = "ERROR", label = "Error", type = "java.lang.String", description = "Error message")}) Map<String, Object> registerAccount(
            @ActionInput(name = "captcha", label = "captcha", description = "Captcha") String captcha,
            @ActionInput(name = "verificationMethod", label = "Verification method (TextMessage or PhoneCall)", description = "verificationMethod", defaultValue = "TextMessage") @Nullable String verificationMethod
    ) {
        Map<String, Object> resultMap = new HashMap<>();
        if (verificationMethod == null || verificationMethod.isEmpty()) {
            verificationMethod = "TextMessage";
        }
        RegistrationType registrationType = RegistrationType.valueOf(verificationMethod);
        try {
            handler.register(captcha, registrationType);
            resultMap.put("RESULT", "OK");
        } catch (IOException e) {
            resultMap.put("RESULT", "KO");
            logger.error("Cannot register account", e);
            resultMap.put("ERROR", "Cannot register account: " + e.getMessage());
        }
        return resultMap;
    }


    public Map<String, Object> registerAccount(@Nullable ThingActions actions, String captcha, @Nullable String verificationMethod) {
        if (actions instanceof SignalActionsMain) {
            return ((SignalActionsMain) actions).registerAccount(captcha, verificationMethod);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMain class.");
        }
    }

    @RuleAction(label = "Verify account", description = "Verify an account number with the code sent by Signal")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: verify request OK. KO: error during code verification"),
            @ActionOutput(name = "ERROR", label = "Error", type = "java.lang.String", description = "Error message")}) Map<String, Object> verifyAccount(
            @ActionInput(name = "verificationCode", label = "Verification Code", description = "Verification Code") String verificationCode
    ) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            handler.verify(verificationCode);
            resultMap.put("RESULT", "OK");
        } catch (IncompleteRegistrationException | IOException e) {
            resultMap.put("RESULT", "KO");
            logger.error("Cannot verify account", e);
            resultMap.put("ERROR", "Cannot verify message: " + e.getMessage());
        }
        return resultMap;
    }

    public Map<String, Object> verifyAccount(@Nullable ThingActions actions, String verificationCode) {
        if (actions instanceof SignalActionsMain) {
            return ((SignalActionsMain) actions).verifyAccount(verificationCode);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMain class.");
        }
    }

    @RuleAction(label = "Update Profile", description = "Force profile update to change profile name and avatar (only for main account)")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: update profile OK. KO: error during profile update"),
            @ActionOutput(name = "ERROR", label = "Error", type = "java.lang.String", description = "Error message")}) Map<String, Object> updateProfile(
            ) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            handler.updateProfile();
            resultMap.put("RESULT", "OK");
        } catch (IOException e) {
            resultMap.put("RESULT", "KO");
            logger.error("Cannot update account profile", e);
            resultMap.put("ERROR", "Cannot update account profile: " + e.getMessage());
        }
        return resultMap;
    }

    public Map<String, Object> updateProfile(@Nullable ThingActions actions) {
        if (actions instanceof SignalActionsMain) {
            return ((SignalActionsMain) actions).updateProfile();
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMain class.");
        }
    }


    @RuleAction(label = "Send Message With Signal", description = "Send a message with Signal")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: message sent. KO: error during sending")}) Map<String, Object> sendSignalMain(
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

    public static Map<String, Object> sendSignalMain(@Nullable ThingActions actions, @Nullable String recipient,
                                                 @Nullable String message) {
        if (actions instanceof SignalActionsMain) {
            return ((SignalActionsMain) actions).sendSignalMain(recipient, message);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMessage class.");
        }
    }

    @RuleAction(label = "Send Image With Signal", description = "Send an Image with Signal")
    public @ActionOutputs({
            @ActionOutput(name = "RESULT", label = "Result", type = "java.lang.String", description = "OK: message sent. KO: error during sending")}) Map<String, Object> sendSignalImageMain(
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

    public static Map<String, Object> sendSignalImageMain(@Nullable ThingActions actions, @Nullable String recipient,
                                                      @Nullable String image, @Nullable String text) {
        if (actions instanceof SignalActionsMain) {
            return ((SignalActionsMain) actions).sendSignalImageMain(recipient, image, text);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActionsMessage class.");
        }
    }

}
