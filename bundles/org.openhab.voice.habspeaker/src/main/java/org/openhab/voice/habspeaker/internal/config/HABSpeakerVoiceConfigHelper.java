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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerVoiceConfigHelper} class allow access to openHAB voice configuration
 * to get the configured keyword.
 * It's a temporal solution to keep could be removed if the voice manager getKeyword method is added.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = HABSpeakerVoiceConfigHelper.class, configurationPid = "org.openhab.voice", property = Constants.SERVICE_PID
        + "=org.openhab.voice.habspeaker.voice")
@NonNullByDefault
public class HABSpeakerVoiceConfigHelper {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerVoiceConfigHelper.class);
    // the default keyword to use if no other is configured
    private static final String DEFAULT_KEYWORD = "Wakeup";
    private static final String CONFIG_KEYWORD = "keyword";
    private String keyword = DEFAULT_KEYWORD;

    @Activate
    protected void activate(Map<String, Object> config) {
        modified(config);
    }

    @Modified
    protected void modified(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            keyword = properties.containsKey(CONFIG_KEYWORD) ? properties.get(CONFIG_KEYWORD).toString()
                    : DEFAULT_KEYWORD;
            logger.debug("Default keyword: {}", keyword);
        }
    }

    public String getKeyword() {
        return keyword;
    }
}
