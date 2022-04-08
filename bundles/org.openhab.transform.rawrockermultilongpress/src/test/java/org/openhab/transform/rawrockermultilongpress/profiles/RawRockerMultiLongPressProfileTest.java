/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;

/**
 * Test class for multi long press
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@NonNullByDefault
public class RawRockerMultiLongPressProfileTest {

    @Mock
    @NonNullByDefault({})
    ProfileCallback profileCallBack;

    @Mock
    @NonNullByDefault({})
    ProfileContext profileContext;

    ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(5);

    @NonNullByDefault({})
    RawRockerMultiLongPressProfile rawRockerProfile;

    @BeforeEach
    public void init() {
        when(profileContext.getExecutorService()).thenReturn(threadPool);
        HashMap<@Nullable String, @Nullable Object> confMap = new HashMap<@Nullable String, @Nullable Object>();
        confMap.put(RawRockerMultiLongPressProfile.PARAM_MAXREPETITION, 3);
        confMap.put(RawRockerMultiLongPressProfile.PARAM_BUTTON2, "button2");
        when(profileContext.getConfiguration()).thenReturn(new Configuration(confMap));
        rawRockerProfile = new RawRockerMultiLongPressProfile(profileCallBack, profileContext);
    }

    @Test
    public void testOnePress() throws InterruptedException {

        rawRockerProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(100);
        rawRockerProfile.onTriggerFromHandler(DIR1_RELEASED);

        Thread.sleep(2000);

        verify(profileCallBack).sendCommand(eq(new StringType("1.1")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testOnePressOnButton2() throws InterruptedException {

        rawRockerProfile.onTriggerFromHandler(DIR2_PRESSED);
        Thread.sleep(100);
        rawRockerProfile.onTriggerFromHandler(DIR2_RELEASED);

        Thread.sleep(2000);

        verify(profileCallBack).sendCommand(eq(new StringType("button2.1")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testMultiPress() throws InterruptedException {

        rawRockerProfile.onTriggerFromHandler(DIR1_PRESSED);
        rawRockerProfile.onTriggerFromHandler(DIR1_PRESSED);
        rawRockerProfile.onTriggerFromHandler(DIR1_PRESSED);

        Thread.sleep(100);
        rawRockerProfile.onTriggerFromHandler(DIR1_RELEASED);

        Thread.sleep(2000);

        verify(profileCallBack).sendCommand(eq(new StringType("1.3")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testOneLongPress() throws InterruptedException {

        rawRockerProfile.onTriggerFromHandler(DIR1_PRESSED);
        Thread.sleep(1500);
        rawRockerProfile.onTriggerFromHandler(DIR1_RELEASED);
        Thread.sleep(1000);

        verify(profileCallBack, times(2)).sendCommand(eq(new StringType("1.0")));

        verifyNoMoreInteractions(profileCallBack);
    }

    @Test
    public void testOneVeryLongPress() throws InterruptedException {

        rawRockerProfile.onTriggerFromHandler(DIR1_PRESSED);

        Thread.sleep(5000);

        verify(profileCallBack, times(3)).sendCommand(eq(new StringType("1.0")));

        verifyNoMoreInteractions(profileCallBack);
    }
}
