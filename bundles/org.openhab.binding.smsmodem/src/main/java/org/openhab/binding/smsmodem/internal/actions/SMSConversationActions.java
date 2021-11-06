/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.smsmodem.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smsmodem.internal.handler.SMSConversationHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SMSModemActions} expose some actions
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@ThingActionsScope(name = "smsmodem") // Your bindings id is usually the scope
@NonNullByDefault
public class SMSConversationActions implements ThingActions {

    private @NonNullByDefault({}) SMSConversationHandler handler;

    private final Logger logger = LoggerFactory.getLogger(SMSConversationActions.class);

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.handler = (SMSConversationHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "Send Message", description = "Send a message")
    public void send(
            @ActionInput(name = "message", label = "message", description = "Message to send") @Nullable String message) {
        if (message != null) {
            handler.send(message);
        } else {
            logger.error("SMSConversation cannot send a message with no text");
        }
    }

    public static void send(@Nullable ThingActions actions, @Nullable String message) {
        if (actions instanceof SMSConversationActions) {
            ((SMSConversationActions) actions).send(message);
        } else {
            throw new IllegalArgumentException("Instance is not an SMSConversationActions class.");
        }
    }
}
