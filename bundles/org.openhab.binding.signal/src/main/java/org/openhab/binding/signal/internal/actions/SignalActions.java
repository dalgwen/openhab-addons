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
package org.openhab.binding.signal.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.handler.SignalBridgeHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SignalActions} exposes some actions
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@ThingActionsScope(name = "signal")
@NonNullByDefault
public class SignalActions implements ThingActions {

    private @NonNullByDefault({}) SignalBridgeHandler handler;

    private final Logger logger = LoggerFactory.getLogger(SignalActions.class);

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (SignalBridgeHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "Send Message With Signal", description = "Send a message with Signal")
    public void sendSignal(
            @ActionInput(name = "recipient", label = "recipient", description = "Recipient of the message") @Nullable String recipient,
            @ActionInput(name = "message", label = "message", description = "Message to send") @Nullable String message) {
        if (recipient != null && !recipient.isEmpty() && message != null) {
            handler.send(recipient, message);
        } else {
            logger.error("Signal cannot send a message with no recipient or text");
        }
    }

    public static void sendSignal(@Nullable ThingActions actions, @Nullable String recipient,
            @Nullable String message) {
        if (actions instanceof SignalActions) {
            ((SignalActions) actions).sendSignal(recipient, message);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActions class.");
        }
    }

    @RuleAction(label = "Send Image With Signal", description = "Send an Image with Signal")
    public void sendSignalImage(
            @ActionInput(name = "recipient", label = "recipient", description = "Recipient of the message") @Nullable String recipient,
            @ActionInput(name = "image", label = "image", description = "Image to send") @Nullable String image,
            @ActionInput(name = "text", label = "text", description = "Text to send") @Nullable String text) {
        if (recipient != null && !recipient.isEmpty() && image != null) {
            handler.sendImage(recipient, image, text);
        } else {
            logger.error("Signal cannot send a photo with no recipient or text");
        }
    }

    public static void sendSignalImage(@Nullable ThingActions actions, @Nullable String recipient,
            @Nullable String image, @Nullable String text) {
        if (actions instanceof SignalActions) {
            ((SignalActions) actions).sendSignalImage(recipient, image, text);
        } else {
            throw new IllegalArgumentException("Instance is not an SignalActions class.");
        }
    }
}
