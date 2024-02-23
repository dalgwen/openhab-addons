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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.openhab.core.thing.CommonTriggerEvents.*;
import static org.openhab.transform.timedevent.profiles.TimedEventConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
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
public class TimedEventMultiAndLongProfileTest {

    @Mock
    @NonNullByDefault({})
    ProfileCallback profileCallBack;

    @Mock
    @NonNullByDefault({})
    ProfileContext profileContext;

    ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(5);

    @NonNullByDefault({})
    TimedEventMultiAndLongProfile timedEventProfile;

    private static final int DELAY = 200;
    // private static final int DELAY = 200000;
    private static final int DELAYSUBTRESHOLD = (int) (DELAY * 0.70);
    // private static final int DELAYSUBTRESHOLD = (int) (DELAY * 0.01);
    private static final int DELAYOVERTRESHOLD = (int) (DELAY * 1.10);
    private static final int DELAYEND = DELAY * 2;

    @BeforeEach
    public void initDefault() {
        init(null);
    }

    @AfterEach
    public void verifyAfterEach() {
        verifyNoMoreInteractions(profileCallBack);
    }

    public void init(@Nullable Map<@Nullable String, @Nullable Object> conf) {
        var localConf = conf;
        if (localConf == null) {
            localConf = prepareDefaultConfiguration();
        }
        reset(profileContext);
        when(profileContext.getExecutorService()).thenReturn(threadPool);
        when(profileContext.getConfiguration()).thenReturn(new Configuration(localConf));
        timedEventProfile = new TimedEventMultiAndLongProfile(profileCallBack, profileContext);
    }

    private Map<@Nullable String, @Nullable Object> prepareDefaultConfiguration() {
        Map<@Nullable String, @Nullable Object> confMap = new HashMap<>();
        confMap.put(PARAM_DELAYBETWEENEVENT, DELAY);
        return confMap;
    }

    @Test
    public void testTriggerOnePress() throws InterruptedException {
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onTriggerFromHandler(DIR1_RELEASED);

        Thread.sleep(DELAYEND);

        verify(profileCallBack).sendCommand(eq(new StringType("DIR1_PRESSED.1")));
    }

    @Test
    public void testTriggerOnePressOnButton2() throws InterruptedException {
        timedEventProfile.onTriggerFromHandler(DIR2_PRESSED);
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onTriggerFromHandler(DIR2_RELEASED);

        Thread.sleep(DELAYEND);

        verify(profileCallBack).sendCommand(eq(new StringType("DIR2_PRESSED.1")));
    }

    @Test
    public void testTriggerMultiPress() throws InterruptedException {
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        timedEventProfile.onTriggerFromHandler(DIR1_RELEASED);
        // note : there is no release to simulate an event miss (we must support this case)
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        timedEventProfile.onTriggerFromHandler(DIR1_RELEASED);
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onTriggerFromHandler(DIR1_RELEASED);

        Thread.sleep(DELAYEND);

        verify(profileCallBack).sendCommand(eq(new StringType("DIR1_PRESSED.3")));
    }

    @Test
    public void testTriggerMultiPressNoreleaseOption() throws InterruptedException {
        Map<@Nullable String, @Nullable Object> confMap = prepareDefaultConfiguration();
        confMap.put(TimedEventConstants.PARAM_HAS_RELEASEEVENT, false);
        init(confMap);

        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(DELAYEND);

        verify(profileCallBack).sendCommand(eq(new StringType("DIR1_PRESSED.3")));
    }

    @Test
    public void testTriggerOneLongPress() throws InterruptedException {
        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(DELAYOVERTRESHOLD * 2);
        timedEventProfile.onTriggerFromHandler(DIR1_RELEASED);
        Thread.sleep(DELAYEND);

        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("DIR1_PRESSED.1.LONG")));
    }

    @Test
    public void testTriggerOneVeryLongPressMaxReached() throws InterruptedException {
        Map<@Nullable String, @Nullable Object> confMap = prepareDefaultConfiguration();
        confMap.put(PARAM_MAXREPETITION, 3);
        init(confMap);

        timedEventProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(DELAYOVERTRESHOLD * 4);

        verify(profileCallBack, times(3)).sendCommand(eq(new StringType("DIR1_PRESSED.1.LONG")));
    }

    @Test
    public void testSwitchTwoPress() throws InterruptedException {
        timedEventProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onCommandFromHandler(OnOffType.OFF);
        timedEventProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onCommandFromHandler(OnOffType.OFF);
        Thread.sleep(DELAYEND);

        verify(profileCallBack).sendCommand(eq(new StringType("ON.2")));
    }

    @Test
    public void testSwitchSimpleLongPress() throws InterruptedException {
        timedEventProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(DELAYOVERTRESHOLD * 2);
        timedEventProfile.onCommandFromHandler(OnOffType.OFF);

        Thread.sleep(DELAYEND);

        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("ON.1.LONG")));
    }

    @Test
    public void testLongPressReallyStop() throws InterruptedException {
        timedEventProfile.onCommandFromHandler(OnOffType.ON);
        Thread.sleep(DELAYOVERTRESHOLD * 2);
        timedEventProfile.onCommandFromHandler(OnOffType.OFF);

        // should not have other event during this time :
        Thread.sleep(DELAYEND);

        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("ON.1.LONG")));
    }

    @Test
    public void testCustomEventThreePress() throws InterruptedException {
        Map<@Nullable String, @Nullable Object> confMap = prepareDefaultConfiguration();
        confMap.put(PARAM_PRESSED, "custompressevent");
        confMap.put(PARAM_RELEASED, "customreleaseevent");
        init(confMap);

        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        Thread.sleep(DELAYEND);

        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("custompressevent.3")));
    }

    @Test
    public void testCustomEventLongPress() throws InterruptedException {
        Map<@Nullable String, @Nullable Object> confMap = prepareDefaultConfiguration();
        confMap.put(TimedEventConstants.PARAM_PRESSED, "custompressevent");
        confMap.put(TimedEventConstants.PARAM_RELEASED, "customreleaseevent");
        init(confMap);

        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("custompressevent"));
        Thread.sleep(DELAYOVERTRESHOLD * 3);
        timedEventProfile.onCommandFromHandler(new FakeCustomCommand("customreleaseevent"));
        Thread.sleep(DELAYEND);

        verify(profileCallBack, times(3)).sendCommand(eq(new StringType("custompressevent.1.LONG")));
    }

    @Test
    public void testCustomEventNoRelease() throws InterruptedException {
        Map<@Nullable String, @Nullable Object> confMap = prepareDefaultConfiguration();
        confMap.put(TimedEventConstants.PARAM_HAS_RELEASEEVENT, false);
        confMap.put(TimedEventConstants.PARAM_PRESSED, "custompressevent");
        confMap.put(TimedEventConstants.PARAM_RELEASED, null);
        init(confMap);

        timedEventProfile.onStateUpdateFromHandler(new FakeCustomStateUpdate("custompressevent"));
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onStateUpdateFromHandler(new FakeCustomStateUpdate("custompressevent"));
        Thread.sleep(DELAYSUBTRESHOLD);
        timedEventProfile.onStateUpdateFromHandler(new FakeCustomStateUpdate("custompressevent"));
        Thread.sleep(DELAYEND);

        verify(profileCallBack, times(1)).sendUpdate(eq(new StringType("custompressevent.3")));
    }

    @Test
    public void testCustomDelay() throws InterruptedException {
        Map<@Nullable String, @Nullable Object> confMap = prepareDefaultConfiguration();
        confMap.put(TimedEventConstants.PARAM_DELAYBETWEENEVENT, 100);
        confMap.put(TimedEventConstants.PARAM_HAS_RELEASEEVENT, false);
        init(confMap);

        timedEventProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(70);
        timedEventProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(130);
        timedEventProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(200);

        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("PRESSED.2")));
        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("PRESSED.1")));
    }

    @Test
    public void testMultiplePressAndLongPress() throws InterruptedException {
        timedEventProfile.onTriggerFromHandler(PRESSED);
        timedEventProfile.onTriggerFromHandler(PRESSED);
        timedEventProfile.onTriggerFromHandler(RELEASED);
        timedEventProfile.onTriggerFromHandler(PRESSED);
        Thread.sleep(DELAYOVERTRESHOLD);
        verify(profileCallBack, times(1)).sendCommand(eq(new StringType("PRESSED.3.LONG")));
        Thread.sleep(DELAYOVERTRESHOLD);
        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("PRESSED.3.LONG")));
        timedEventProfile.onTriggerFromHandler(RELEASED);
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
