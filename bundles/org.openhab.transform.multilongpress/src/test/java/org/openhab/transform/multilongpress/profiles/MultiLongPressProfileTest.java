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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.openhab.core.thing.CommonTriggerEvents.*;

import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * Test class for multi long press
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@NonNullByDefault
public class MultiLongPressProfileTest {

    @Mock
    @NonNullByDefault({})
    ProfileCallback profileCallBack;

    @Mock
    @NonNullByDefault({})
    ProfileContext profileContext;

    ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(5);

    @NonNullByDefault({})
    MultiLongPressProfile multiLongPressProfile;

    @BeforeEach
    public void init() {
        when(profileContext.getExecutorService()).thenReturn(threadPool);
        HashMap<@Nullable String, @Nullable Object> confMap = new HashMap<@Nullable String, @Nullable Object>();
        confMap.put(MultiLongPressProfile.PARAM_MAXREPETITION, 3);
        confMap.put(MultiLongPressProfile.PARAM_BUTTON2_NAME, "button2");

        when(profileContext.getConfiguration()).thenReturn(new Configuration(confMap));
        multiLongPressProfile = new MultiLongPressProfile(profileCallBack, profileContext);
    }

    @Test
    public void testTriggerOnePress() throws InterruptedException {
        multiLongPressProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(100);
        multiLongPressProfile.onTriggerFromHandler(DIR1_RELEASED);

        Thread.sleep(700);

        verify(profileCallBack).sendCommand(eq(new StringType("1.1")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testTriggerOnePressOnButton2() throws InterruptedException {
        multiLongPressProfile.onTriggerFromHandler(DIR2_PRESSED);
        Thread.sleep(100);
        multiLongPressProfile.onTriggerFromHandler(DIR2_RELEASED);

        Thread.sleep(700);

        verify(profileCallBack).sendCommand(eq(new StringType("button2.1")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testTriggerMultiPress() throws InterruptedException {
        multiLongPressProfile.onTriggerFromHandler(DIR1_PRESSED);
        // note : there is no release to simulate an event miss (we must support this case)
        multiLongPressProfile.onTriggerFromHandler(DIR1_PRESSED);
        multiLongPressProfile.onTriggerFromHandler(DIR1_RELEASED);
        multiLongPressProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(100);
        multiLongPressProfile.onTriggerFromHandler(DIR1_RELEASED);

        Thread.sleep(700);

        verify(profileCallBack).sendCommand(eq(new StringType("1.3")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testTriggerOneLongPress() throws InterruptedException {
        multiLongPressProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(1500);
        multiLongPressProfile.onTriggerFromHandler(DIR1_RELEASED);
        Thread.sleep(100);

        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("1.0")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testTriggerOneVeryLongPressMaxReached() throws InterruptedException {
        multiLongPressProfile.onTriggerFromHandler(DIR1_PRESSED);

        Thread.sleep(3000);

        verify(profileCallBack, times(3)).sendCommand(eq(new StringType("1.0")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testSwitchTwoPress() throws InterruptedException {
        multiLongPressProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(100);
        multiLongPressProfile.onCommandFromHandler(OnOffType.OFF);
        multiLongPressProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(100);
        multiLongPressProfile.onCommandFromHandler(OnOffType.OFF);
        Thread.sleep(700);

        verify(profileCallBack).sendCommand(eq(new StringType("1.2")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testSwitchLongTimeBeforeOff() throws InterruptedException {
        multiLongPressProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(1100);
        multiLongPressProfile.onCommandFromHandler(OnOffType.OFF);

        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("1.0")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testCustomEventThreePress() throws InterruptedException {
        HashMap<@Nullable String, @Nullable Object> confMap = new HashMap<@Nullable String, @Nullable Object>();
        confMap.put(MultiLongPressProfile.PARAM_PRESSED, "custompressevent");
        confMap.put(MultiLongPressProfile.PARAM_RELEASED, "customreleaseevent");

        when(profileContext.getConfiguration()).thenReturn(new Configuration(confMap));
        multiLongPressProfile = new MultiLongPressProfile(profileCallBack, profileContext);

        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(200);
        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(200);
        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(200);
        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        Thread.sleep(700);

        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("1.3")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testCustomEventLongPress() throws InterruptedException {
        HashMap<@Nullable String, @Nullable Object> confMap = new HashMap<@Nullable String, @Nullable Object>();
        confMap.put(MultiLongPressProfile.PARAM_PRESSED, "custompressevent");
        confMap.put(MultiLongPressProfile.PARAM_RELEASED, "customreleaseevent");

        when(profileContext.getConfiguration()).thenReturn(new Configuration(confMap));
        multiLongPressProfile = new MultiLongPressProfile(profileCallBack, profileContext);

        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(1800);
        multiLongPressProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        Thread.sleep(100);

        verify(profileCallBack, times(3)).sendCommand(eq(new StringType("1.0")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testCustomEventNoRelease() throws InterruptedException {
        HashMap<@Nullable String, @Nullable Object> confMap = new HashMap<@Nullable String, @Nullable Object>();
        confMap.put(MultiLongPressProfile.PARAM_HAS_RELEASEEVENT, false);
        confMap.put(MultiLongPressProfile.PARAM_PRESSED, "custompressevent");
        confMap.put(MultiLongPressProfile.PARAM_RELEASED, null);

        when(profileContext.getConfiguration()).thenReturn(new Configuration(confMap));
        multiLongPressProfile = new MultiLongPressProfile(profileCallBack, profileContext);

        multiLongPressProfile.onStateUpdateFromHandler(new FakeCustomStateUpdate("custompressevent"));
        Thread.sleep(200);
        multiLongPressProfile.onStateUpdateFromHandler(new FakeCustomStateUpdate("custompressevent"));
        Thread.sleep(200);
        multiLongPressProfile.onStateUpdateFromHandler(new FakeCustomStateUpdate("custompressevent"));
        Thread.sleep(700);

        verify(profileCallBack, times(1)).sendUpdate(eq(new StringType("1.3")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testCustomDelay() throws InterruptedException {
        HashMap<@Nullable String, @Nullable Object> confMap = new HashMap<@Nullable String, @Nullable Object>();
        confMap.put(MultiLongPressProfile.PARAM_DELAYBETWEENEVENT, 200);
        confMap.put(MultiLongPressProfile.PARAM_HAS_RELEASEEVENT, false);
        when(profileContext.getConfiguration()).thenReturn(new Configuration(confMap));
        multiLongPressProfile = new MultiLongPressProfile(profileCallBack, profileContext);

        multiLongPressProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(100);
        multiLongPressProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(300);
        multiLongPressProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(700);

        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("1.2")));
        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("1.1")));

        verifyNoMoreInteractions(profileCallBack);
    }

    public static class FakeCustomCommand implements Command {
        private String event;

        public FakeCustomCommand(String event) {
            this.event = event;
        }

        @Override
        public String format(String pattern) {
            return "";
        }

        @Override
        public String toFullString() {
            return event;
        }

        @Override
        public String toString() {
            return event;
        }
    }

    public static class FakeCustomStateUpdate implements State {
        private String event;

        public FakeCustomStateUpdate(String event) {
            this.event = event;
        }

        @Override
        public String format(String pattern) {
            return "";
        }

        @Override
        public String toFullString() {
            return event;
        }

        @Override
        public String toString() {
            return event;
        }
    }
}
