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
package org.openhab.voice.habspeaker.internal.config;

import static org.openhab.voice.habspeaker.internal.HABSpeakerConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.config.core.Configuration;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

/**
 * The {@link HABSpeakerConfigProvider} class defines the speaker configuration
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = HABSpeakerConfigProvider.class, configurationPid = SERVICE_PID, property = Constants.SERVICE_PID
        + "=" + SERVICE_PID)
@ConfigurableService(category = SERVICE_CATEGORY, label = SERVICE_NAME, description_uri = SERVICE_CATEGORY + ":"
        + SERVICE_ID)
@NonNullByDefault
public class HABSpeakerConfigProvider {
    private HABSpeakerConfig config = new HABSpeakerConfig();

    public HABSpeakerConfig getConfig() {
        return config;
    }

    @Activate
    public void activate(Map<String, Object> configMap) {
        modified(configMap);
    }

    @Modified
    public void modified(Map<String, Object> configMap) {
        var config = new Configuration(configMap).as(HABSpeakerConfig.class);
        this.config = config;
    }
}
