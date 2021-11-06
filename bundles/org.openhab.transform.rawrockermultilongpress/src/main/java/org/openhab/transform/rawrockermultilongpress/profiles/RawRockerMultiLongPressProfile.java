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
package org.openhab.transform.rawrockermultilongpress.profiles;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.profiles.TriggerProfile;
import org.openhab.core.types.State;

/**
 * Profile to offer the RockerMultiLongPressProfile on a ItemChannelLink
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class RawRockerMultiLongPressProfile implements TriggerProfile {

    public static final ProfileTypeUID PROFILE_TYPE_UID = new ProfileTypeUID("ROCKER", "rawrocker-to-string");

    private final ProfileCallback callback;
    private final ProfileContext context;

    protected @Nullable ScheduledFuture<?> resolveLater;
    protected String lastPressedButton = "";
    protected int pressedNumber = 0;
    protected int releasedNumber = 0;
    protected boolean continuousPressMode = false;

    RawRockerMultiLongPressProfile(ProfileCallback callback, ProfileContext context) {
        this.callback = callback;
        this.context = context;
    }

    @Override
    public ProfileTypeUID getProfileTypeUID() {
        return PROFILE_TYPE_UID;
    }

    @Override
    public void onTriggerFromHandler(String event) {
        if (CommonTriggerEvents.DIR1_PRESSED.equals(event)) {
            buttonPressed("1");
        } else if (CommonTriggerEvents.DIR1_RELEASED.equals(event)) {
            buttonReleased("1");
        } else if (CommonTriggerEvents.DIR2_PRESSED.equals(event)) {
            buttonPressed("2");
        } else if (CommonTriggerEvents.DIR2_RELEASED.equals(event)) {
            buttonReleased("2");
        } else if (CommonTriggerEvents.PRESSED.equals(event)) {
            buttonPressed("1");
        } else if (CommonTriggerEvents.RELEASED.equals(event)) {
            buttonReleased("1");
        }
    }

    public void reset() {
        this.lastPressedButton = "";
        this.pressedNumber = 0;
        this.releasedNumber = 0;
    }

    public void resolve() {
        if (!this.lastPressedButton.isEmpty()) {
            if (this.releasedNumber != 0) {
                callback.sendCommand(new StringType(this.lastPressedButton + "." + this.releasedNumber));
            }
            if (this.releasedNumber < this.pressedNumber) {
                continuousPressMode = true;
                String _lastPressedBouton = this.lastPressedButton;
                synchronized (this) {
                    if (this.resolveLater != null) {
                        this.resolveLater.cancel(true);
                    }
                    this.resolveLater = context.getExecutorService().scheduleAtFixedRate(
                            () -> callback.sendCommand(new StringType(_lastPressedBouton + "." + 0)), 0, 500,
                            TimeUnit.MILLISECONDS);
                }
            }
        }
        reset();
    }

    public void buttonPressed(String bouton) {

        if (!this.lastPressedButton.equals(bouton)) {
            resolve();
        }
        this.pressedNumber++;
        this.lastPressedButton = bouton;
        synchronized (this) {
            if (this.resolveLater != null) {
                this.resolveLater.cancel(true);
            }
            this.resolveLater = context.getExecutorService().schedule(this::resolve, 550, TimeUnit.MILLISECONDS);
        }
    }

    public void buttonReleased(String bouton) {

        if (this.lastPressedButton.equals(bouton)) {
            this.releasedNumber++;
        }
        synchronized (this) {
            if (this.resolveLater != null && continuousPressMode) {
                this.resolveLater.cancel(true);
                continuousPressMode = false;
            }
        }
    }

    @Override
    public void onStateUpdateFromItem(State state) {
    }

}
