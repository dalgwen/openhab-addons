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
package org.openhab.transform.multilongpress.profiles;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.profiles.StateProfile;
import org.openhab.core.thing.profiles.TriggerProfile;
import org.openhab.core.transform.TransformationService;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profile to offer the MultiLongPressProfile on an ItemChannelLink
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class MultiLongPressProfile implements TriggerProfile, StateProfile {

    private final Logger logger = LoggerFactory.getLogger(MultiLongPressProfile.class);

    public static final ProfileTypeUID PROFILE_TYPE_UID = new ProfileTypeUID(
            TransformationService.TRANSFORM_PROFILE_SCOPE, "multilongpress-to-string");
    public static final String PARAM_HAS_RELEASEEVENT = "hasReleaseEvent";
    public static final String PARAM_BUTTON1_NAME = "button1";
    public static final String PARAM_BUTTON2_NAME = "button2";
    public static final String PARAM_RELEASED = "releasedEventOrState";
    public static final String PARAM_PRESSED = "pressedEventOrState";
    public static final String PARAM_MAXREPETITION = "maxRepetition";
    public static final String PARAM_DELAYBETWEENEVENT = "delay";

    private final ProfileCallback callback;
    private final ProfileContext context;

    protected @Nullable ScheduledFuture<?> resolveLater;
    protected String lastPressedButton = "";
    protected int numberOfTimePressed = 0;
    protected int numberOfTimeReleased = 0;
    protected boolean continuousPressMode = false;
    public Integer currentRepetition = 0;

    boolean hasReleasedEvent = true;
    String button1Name = "1";
    String button2Name = "2";
    Integer maxRepetition = -1;
    private Integer delay = 500;

    private CommandOrUpdate mode = CommandOrUpdate.COMMAND;

    private final Set<String> pressedEvents = new HashSet<>(Set.of(CommonTriggerEvents.DIR1_PRESSED,
            CommonTriggerEvents.PRESSED, CommonTriggerEvents.SHORT_PRESSED, OnOffType.ON.toString(),
            OpenClosedType.OPEN.toString(), PlayPauseType.PLAY.toString(), UpDownType.UP.toString()));
    private final Set<String> releaseEvents = new HashSet<>(
            Set.of(CommonTriggerEvents.DIR1_RELEASED, CommonTriggerEvents.RELEASED, OnOffType.OFF.toString(),
                    OpenClosedType.CLOSED.toString(), PlayPauseType.PAUSE.toString(), UpDownType.DOWN.toString()));

    MultiLongPressProfile(ProfileCallback callback, ProfileContext context) {
        this.callback = callback;
        this.context = context;

        Configuration configuration = context.getConfiguration();
        logger.debug("Configuration: {}", configuration);

        Object paramValueHasReleaseEvent = configuration.get(PARAM_HAS_RELEASEEVENT);
        if (paramValueHasReleaseEvent != null) {
            if (paramValueHasReleaseEvent instanceof Boolean paramValueHasReleaseEventBoolean) {
                hasReleasedEvent = paramValueHasReleaseEventBoolean;
            } else {
                logger.error("Parameter '{}' is not of type Boolean. Please make sure it is", PARAM_HAS_RELEASEEVENT);
            }
        }

        Object paramValue1 = configuration.get(PARAM_BUTTON1_NAME);
        if (paramValue1 != null) {
            if (paramValue1 instanceof String paramValue1String) {
                button1Name = paramValue1String;
            } else {
                logger.error("Parameter '{}' is not of type String. Please make sure it is", PARAM_BUTTON1_NAME);
            }
        }
        Object paramValue2 = configuration.get(PARAM_BUTTON2_NAME);
        if (paramValue2 != null) {
            if (paramValue2 instanceof String paramValue2String) {
                button2Name = paramValue2String;
            } else {
                logger.error("Parameter '{}' is not of type String. Please make sure it is", PARAM_BUTTON2_NAME);
            }
        }
        Object paramValueMaxRepetition = configuration.get(PARAM_MAXREPETITION);
        if (paramValueMaxRepetition != null) {
            if (paramValueMaxRepetition instanceof BigDecimal paramValueMaxRepetitionBigDecimal) {
                maxRepetition = paramValueMaxRepetitionBigDecimal.intValue();
            } else if (paramValueMaxRepetition instanceof Integer) {
                maxRepetition = (Integer) paramValueMaxRepetition;
            } else {
                logger.error("Parameter '{}' is not of type Integer/BigDecimal. Please make sure it is",
                        PARAM_MAXREPETITION);
            }
        }
        Object paramPressed = configuration.get(PARAM_PRESSED);
        if (paramPressed != null) {
            if (paramPressed instanceof String paramPressedAsString) {
                if (!paramPressedAsString.isBlank()) {
                    pressedEvents.add(paramPressedAsString);
                }
            } else {
                logger.error("Parameter '{}' is not of type String. Please make sure it is", PARAM_PRESSED);
            }
        }
        Object paramReleased = configuration.get(PARAM_RELEASED);
        if (paramReleased != null) {
            if (paramReleased instanceof String paramReleasedAsString) {
                if (!paramReleasedAsString.isBlank()) {
                    releaseEvents.add(paramReleasedAsString);
                }
            } else {
                logger.error("Parameter '{}' is not of type String. Please make sure it is", PARAM_RELEASED);
            }
        }
        Object paramDelay = configuration.get(PARAM_DELAYBETWEENEVENT);
        if (paramDelay != null) {
            if (paramDelay instanceof BigDecimal paramDelayBigDecimal) {
                delay = paramDelayBigDecimal.intValue();
            } else if (paramDelay instanceof Integer) {
                delay = (Integer) paramDelay;
            } else {
                logger.error("Parameter '{}' is not of type Integer/BigDecimal. Please make sure it is",
                        PARAM_DELAYBETWEENEVENT);
            }
        }
    }

    @Override
    public ProfileTypeUID getProfileTypeUID() {
        return PROFILE_TYPE_UID;
    }

    @Override
    public void onTriggerFromHandler(String event) {
        interpretEvent(event);
    }

    private void interpretEvent(String event) {
        currentRepetition = 0;
        if (logger.isDebugEnabled()) {
            logger.debug("Event: {} at {}", event, System.currentTimeMillis());
        }

        if (pressedEvents.contains(event)) {
            buttonPressed(button1Name);
            if (!hasReleasedEvent) { // the device cannot send release event, so we "release" now
                buttonReleased(button1Name);
            }
        }
        if (releaseEvents.contains(event)) {
            buttonReleased(button1Name);
        }

        // special for rocker with two buttons :
        if (CommonTriggerEvents.DIR2_PRESSED.equals(event)) {
            buttonPressed(button2Name);
        } else if (CommonTriggerEvents.DIR2_RELEASED.equals(event)) {
            buttonReleased(button2Name);
        }

        // special press buttons
        else if (CommonTriggerEvents.DOUBLE_PRESSED.equals(event)) {
            buttonPressed(button2Name);
            buttonReleased(button2Name);
            buttonPressed(button2Name);
            buttonReleased(button2Name);
        } else if (CommonTriggerEvents.LONG_PRESSED.equals(event)) {
            send(button1Name + "." + 0);
        }
    }

    private void reset() {
        this.lastPressedButton = "";
        this.numberOfTimePressed = 0;
        this.numberOfTimeReleased = 0;
    }

    private void send(String type) {
        if (this.mode == CommandOrUpdate.COMMAND) {
            callback.sendCommand(new StringType(type));
        } else {
            callback.sendUpdate(new StringType(type));
        }
    }

    private void resolve() {
        if (!this.lastPressedButton.isEmpty()) {
            if (this.numberOfTimeReleased != 0) {
                send(this.lastPressedButton + "." + this.numberOfTimeReleased);
            }
            if (this.numberOfTimeReleased < this.numberOfTimePressed) {
                continuousPressMode = true;
                String localLastPressedBouton = this.lastPressedButton;
                synchronized (this) {
                    ScheduledFuture<?> resolveLaterFinal = this.resolveLater;
                    if (resolveLaterFinal != null) {
                        resolveLaterFinal.cancel(true);
                    }
                    this.resolveLater = context.getExecutorService()
                            .scheduleWithFixedDelay(
                                    () -> recurrentLongPress(localLastPressedBouton,
                                            new StringType(localLastPressedBouton + "." + 0)),
                                    0, delay, TimeUnit.MILLISECONDS);
                }
            }
        }
        reset();
    }

    private void recurrentLongPress(String button, StringType command) {
        if (maxRepetition > 0) {
            if (currentRepetition < maxRepetition) {
                send(button + "." + 0);
                currentRepetition++;
            }
            var scheduledRepetition = this.resolveLater;
            if (currentRepetition >= maxRepetition && scheduledRepetition != null) {
                scheduledRepetition.cancel(false);
            }
        } else {
            send(button + "." + 0);
        }
    }

    private void buttonPressed(String bouton) {
        if (!this.lastPressedButton.equals(bouton)) {
            resolve();
        } else {
            // match releaseNumber to pressedNumber in case we miss a release
            this.numberOfTimeReleased = this.numberOfTimePressed;
        }
        this.numberOfTimePressed++;
        this.lastPressedButton = bouton;
        synchronized (this) {
            ScheduledFuture<?> resolveLaterFinal = this.resolveLater;
            if (resolveLaterFinal != null) {
                resolveLaterFinal.cancel(true);
            }
            this.resolveLater = context.getExecutorService().schedule(this::resolve, delay + 50, TimeUnit.MILLISECONDS);
        }
    }

    private void buttonReleased(String bouton) {
        if (this.lastPressedButton.equals(bouton)) {
            this.numberOfTimeReleased = this.numberOfTimePressed;
        }
        synchronized (this) {
            ScheduledFuture<?> resolveLaterFinal = this.resolveLater;
            if (resolveLaterFinal != null && continuousPressMode) {
                resolveLaterFinal.cancel(true);
                continuousPressMode = false;
            }
        }
    }

    @Override
    public void onStateUpdateFromItem(State state) {
    }

    @Override
    public void onCommandFromItem(Command command) {
    }

    @Override
    public void onCommandFromHandler(Command command) {
        this.mode = CommandOrUpdate.COMMAND;
        interpretEvent(command.toString());
    }

    @Override
    public void onStateUpdateFromHandler(State state) {
        this.mode = CommandOrUpdate.UPDATE;
        interpretEvent(state.toString());
    }

    private enum CommandOrUpdate {
        COMMAND,
        UPDATE;
    }
}
