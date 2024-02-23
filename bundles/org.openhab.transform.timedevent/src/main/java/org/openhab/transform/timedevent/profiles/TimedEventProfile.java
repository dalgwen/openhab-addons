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
package org.openhab.transform.timedevent.profiles;

import static org.openhab.transform.timedevent.profiles.TimedEventConstants.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.profiles.StateProfile;
import org.openhab.core.thing.profiles.TriggerProfile;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.transform.timedevent.profiles.data.ComputedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common data and code for multi and long press profiles
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public abstract class TimedEventProfile implements StateProfile, TriggerProfile {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    // services
    protected final ProfileCallback callback;
    protected final Configuration configuration;
    @NonNullByDefault({})
    protected ProfileTypeUID profileTypeUID;
    private final ScheduledExecutorService executorService;

    // states
    private @Nullable ScheduledFuture<?> toDoNext;
    private Optional<Type> lastPressedButton = Optional.empty();
    private Optional<Type> lastReleasedButton = Optional.empty();
    private int numberOfTimePressed = 0;
    private int numberOfTimeReleased = 0;
    private boolean buttonIsLongPressedRightNow = false;
    private Integer currentRepetition = 0;
    protected CommandOrUpdate mode = CommandOrUpdate.COMMAND;

    // configuration parameters
    protected boolean hasReleasedEvent;
    private final Integer maxRepetition;
    private final Integer delay;
    protected final Set<String> pressedEvents;
    protected final Set<String> releaseEvents;

    @SuppressWarnings("unchecked")
    public TimedEventProfile(ProfileCallback callback, ProfileContext context) {
        this.callback = callback;
        this.configuration = context.getConfiguration();
        this.executorService = context.getExecutorService();
        logger.debug("Configuration: {}", configuration);

        hasReleasedEvent = getParameterAs(PARAM_HAS_RELEASEEVENT, Boolean.class).orElse(true);
        pressedEvents = getParameterAs(PARAM_PRESSED, Set.class).orElse(DEFAULT_PRESSED_EVENTS);
        releaseEvents = getParameterAs(PARAM_RELEASED, Set.class).orElse(DEFAULT_RELEASE_EVENTS);
        delay = getParameterAs(PARAM_DELAYBETWEENEVENT, Integer.class).orElse(DEFAULT_DELAYBETWEENEVENT);
        maxRepetition = getParameterAs(PARAM_MAXREPETITION, Integer.class).orElse(DEFAULT_MAX_REPETITION);
    }

    public void setProfileTypeUID(ProfileTypeUID profileTypeUID) {
        this.profileTypeUID = profileTypeUID;
    }

    @Override
    public ProfileTypeUID getProfileTypeUID() {
        if (profileTypeUID == null) {
            throw new IllegalStateException("Implementation error. This field should have been initialized");
        }
        return profileTypeUID;
    }

    @SuppressWarnings("unchecked")
    protected <T> Optional<T> getParameterAs(String parameterName, Class<T> as) {
        Object paramValue = configuration.get(parameterName);
        if (paramValue != null) {
            if (as.isAssignableFrom(paramValue.getClass())) {
                // special case : empty string should be empty optional
                if (paramValue instanceof String paramValueString && paramValueString.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(as.cast(paramValue));
            } else if (paramValue instanceof BigDecimal paramValueBigDecimal && as.isAssignableFrom(Integer.class)) {
                // special conversion BigDecimal to Integer
                return Optional.of(as.cast(paramValueBigDecimal.intValue()));
            } else if (paramValue instanceof String paramValueString && as.isAssignableFrom(Set.class)) {
                // special conversion String to Set
                return (Optional<T>) Optional
                        .of(Stream.of(paramValueString.split(",", -1)).collect(Collectors.toSet()));
            } else {
                logger.error("Parameter '{}' is not of type {}, Please make sure it is", parameterName,
                        as.getSimpleName());
            }
        }
        return Optional.empty();
    }

    /**
     * Interpret an event as a button press.
     *
     * @param event
     */
    protected void interpretEvent(Type type) {

        String event = type.toString();
        currentRepetition = 0;
        if (logger.isDebugEnabled()) {
            String now = Long.valueOf(System.currentTimeMillis()).toString().substring(8);
            logger.debug("Event at {} : {}", now, event);
        }

        if (pressedEvents.contains(event)) {
            buttonPressed(type);
            if (!hasReleasedEvent) {
                // the device cannot send release event, so we "release" now
                buttonReleased(null);
            }
        } else if (releaseEvents.contains(event)) {
            buttonReleased(type);
        }

        // special press buttons
        else if (CommonTriggerEvents.DOUBLE_PRESSED.equals(event)) {
            buttonPressed(new StringType(CommonTriggerEvents.PRESSED));
            buttonReleased(null);
            buttonPressed(new StringType(CommonTriggerEvents.PRESSED));
            buttonReleased(null);
        } else if (CommonTriggerEvents.LONG_PRESSED.equals(event)) {
            logAndSend(new ComputedEvent(new StringType(CommonTriggerEvents.PRESSED), numberOfTimePressed,
                    ++currentRepetition));
        }
    }

    private void reset() {
        this.lastPressedButton = Optional.empty();
        this.lastReleasedButton = Optional.empty();
        this.numberOfTimePressed = 0;
        this.numberOfTimeReleased = 0;
    }

    private void resolve() {
        if (!this.lastPressedButton.isEmpty()) {
            if (this.numberOfTimeReleased == this.numberOfTimePressed) {
                logAndSend(new ComputedEvent(this.lastPressedButton.get(), this.numberOfTimePressed, 0));
                // we will also send release event if applicable, after a small delay
                if (lastReleasedButton.isPresent()) {
                    logAndSend(new ComputedEvent(this.lastReleasedButton.get(), this.numberOfTimePressed, 0));
                }
            }
            if (this.numberOfTimeReleased < this.numberOfTimePressed) {
                buttonIsLongPressedRightNow = true;
                Type localLastPressedBouton = this.lastPressedButton.get();
                synchronized (this) {
                    ScheduledFuture<?> toDoNextFinal = this.toDoNext;
                    if (toDoNextFinal != null) {
                        toDoNextFinal.cancel(true);
                    }
                    int localNumberOfTimePressed = numberOfTimePressed;
                    this.toDoNext = executorService.scheduleWithFixedDelay(
                            () -> recurrentLongPress(localLastPressedBouton, localNumberOfTimePressed), 0, delay,
                            TimeUnit.MILLISECONDS);
                }
            }
        }
        reset();
    }

    private void recurrentLongPress(Type button, int prefixNumberOfTimePressed) {
        var scheduledRepetition = this.toDoNext;
        if (currentRepetition >= maxRepetition) {
            if (scheduledRepetition != null) {
                scheduledRepetition.cancel(false);
            }
        } else {
            logAndSend(new ComputedEvent(button, prefixNumberOfTimePressed, ++currentRepetition));
        }
    }

    private void buttonPressed(Type button) {
        var localLastPressedButton = lastPressedButton;
        if (localLastPressedButton.isPresent() && !localLastPressedButton.get().toString().equals(button.toString())) {
            // we press a button different from the last time, so resolve the last press now.
            resolve();
        } else {
            // before increment, match releaseNumber to pressedNumber in case we missed a release event
            this.numberOfTimeReleased = this.numberOfTimePressed;
        }
        this.numberOfTimePressed++;
        this.lastPressedButton = Optional.of(button);
        synchronized (this) {
            ScheduledFuture<?> toDoNextFinal = this.toDoNext;
            if (toDoNextFinal != null) {
                toDoNextFinal.cancel(true);
            }
            this.toDoNext = executorService.schedule(this::resolve, delay + 1, TimeUnit.MILLISECONDS);
        }
    }

    private void buttonReleased(@Nullable Type button) {
        // before assignation, match press number to releaseNumber in case we miss a press
        if (this.numberOfTimePressed == this.numberOfTimeReleased) {
            this.numberOfTimePressed++;
        }
        this.numberOfTimeReleased = this.numberOfTimePressed;
        this.lastReleasedButton = Optional.ofNullable(button);
        synchronized (this) {
            ScheduledFuture<?> toDoNextLocal = this.toDoNext;
            // the button is released after a long press
            if (toDoNextLocal != null && buttonIsLongPressedRightNow) {
                toDoNextLocal.cancel(true);
                buttonIsLongPressedRightNow = false;
                if (button != null) {
                    logAndSend(new ComputedEvent(button, numberOfTimePressed, currentRepetition));
                    reset();
                }
            }
        }
    }

    protected enum CommandOrUpdate {
        COMMAND,
        UPDATE;
    }

    private void logAndSend(ComputedEvent buttonEvent) {
        if (logger.isDebugEnabled()) {
            String now = Long.valueOf(System.currentTimeMillis()).toString().substring(8);
            logger.debug("Computed at {} : {}", now, buttonEvent);
        }
        event(buttonEvent);
    }

    @Override
    public void onCommandFromHandler(Command command) {
        this.mode = CommandOrUpdate.COMMAND;
        interpretEvent(command);
    }

    @Override
    public void onStateUpdateFromHandler(State state) {
        this.mode = CommandOrUpdate.UPDATE;
        interpretEvent(state);
    }

    @Override
    public void onStateUpdateFromItem(State state) {
    }

    @Override
    public void onCommandFromItem(Command command) {
    }

    @Override
    public void onTriggerFromHandler(String event) {
        interpretEvent(new StringType(event));
    }

    protected abstract void event(ComputedEvent event);
}
