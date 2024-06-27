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
package org.openhab.automation.java223.internal.codegeneration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link SourceHelperCopier} is responsible for copying the helper library for rule development
 *
 * @author Gwendal Roulleau - Refactor using freemarker
 */
@NonNullByDefault
public class SourceHelperCopier {

    private static List<FileToCopy> FILES_TO_COPY = List.of( //
            new FileToCopy("rules", "RuleAnnotationParser"), //
            new FileToCopy("rules", "RuleParserException"), //
            new FileToCopy("rules", "Java223Rule"), //
            new FileToCopy("", "Java223Script"), //
            new FileToCopy("rules.annotations", "ChannelEventTrigger"), //
            new FileToCopy("rules.annotations", "ChannelEventTriggers"), //
            new FileToCopy("rules.annotations", "CronTrigger"), //
            new FileToCopy("rules.annotations", "CronTriggers"), //
            new FileToCopy("rules.annotations", "DateTimeTrigger"), //
            new FileToCopy("rules.annotations", "DateTimeTriggers"), //
            new FileToCopy("rules.annotations", "DayOfWeekCondition"), //
            new FileToCopy("rules.annotations", "EphemerisDaysetCondition"), //
            new FileToCopy("rules.annotations", "EphemerisHolidayCondition"), //
            new FileToCopy("rules.annotations", "EphemerisNotHolidayCondition"), //
            new FileToCopy("rules.annotations", "EphemerisWeekdayCondition"), //
            new FileToCopy("rules.annotations", "EphemerisWeekendCondition"), //
            new FileToCopy("rules.annotations", "GenericAutomationTrigger"), //
            new FileToCopy("rules.annotations", "GenericAutomationTriggers"), //
            new FileToCopy("rules.annotations", "GenericCompareCondition"), //
            new FileToCopy("rules.annotations", "GenericCompareConditions"), //
            new FileToCopy("rules.annotations", "GenericEventCondition"), //
            new FileToCopy("rules.annotations", "GenericEventTrigger"), //
            new FileToCopy("rules.annotations", "GenericEventTriggers"), //
            new FileToCopy("rules.annotations", "GroupCommandTrigger"), //
            new FileToCopy("rules.annotations", "GroupCommandTriggers"), //
            new FileToCopy("rules.annotations", "GroupStateChangeTrigger"), //
            new FileToCopy("rules.annotations", "GroupStateChangeTriggers"), //
            new FileToCopy("rules.annotations", "GroupStateUpdateTrigger"), //
            new FileToCopy("rules.annotations", "GroupStateUpdateTriggers"), //
            new FileToCopy("rules.annotations", "ItemCommandTrigger"), //
            new FileToCopy("rules.annotations", "ItemCommandTriggers"), //
            new FileToCopy("rules.annotations", "ItemStateCondition"), //
            new FileToCopy("rules.annotations", "ItemStateConditions"), //
            new FileToCopy("rules.annotations", "ItemStateChangeTrigger"), //
            new FileToCopy("rules.annotations", "ItemStateChangeTriggers"), //
            new FileToCopy("rules.annotations", "ItemStateUpdateTrigger"), //
            new FileToCopy("rules.annotations", "ItemStateUpdateTriggers"), //
            new FileToCopy("rules.annotations", "Rule"), //
            new FileToCopy("rules.annotations", "SystemStartlevelTrigger"), //
            new FileToCopy("rules.annotations", "ThingStatusChangeTrigger"), //
            new FileToCopy("rules.annotations", "ThingStatusChangeTriggers"), //
            new FileToCopy("rules.annotations", "ThingStatusUpdateTrigger"), //
            new FileToCopy("rules.annotations", "ThingStatusUpdateTriggers"), //
            new FileToCopy("rules.annotations", "TimeOfDayCondition"), //
            new FileToCopy("rules.annotations", "TimeOfDayTrigger"), //
            new FileToCopy("rules.annotations", "TimeOfDayTriggers"), //
            new FileToCopy("rules.eventinfo", "ChannelEvent"), //
            new FileToCopy("rules.eventinfo", "EventInfo"), //
            new FileToCopy("rules.eventinfo", "ItemCommand"), //
            new FileToCopy("rules.eventinfo", "ItemStateChange"), //
            new FileToCopy("rules.eventinfo", "ItemStateUpdate"), //
            new FileToCopy("rules.eventinfo", "ThingStatusChange"), //
            new FileToCopy("rules.eventinfo", "ThingStatusUpdate"));

    public static synchronized void copyFiles(ClassWriter classWriter) throws IOException {
        for (FileToCopy fileToCopy : FILES_TO_COPY) {

            String resourcePath = "/" + ClassWriter.HELPER_PACKAGE + "/" //
                    + (fileToCopy.packageName.isEmpty() ? "" : (fileToCopy.packageName.replaceAll("\\.", "/") + "/")) //
                    + fileToCopy.fileName + ".java";
            InputStream inputStream = ClassWriter.class.getResourceAsStream(resourcePath);

            String destPackageName = ClassWriter.HELPER_PACKAGE
                    + (fileToCopy.packageName.isEmpty() ? "" : ("." + fileToCopy.packageName));

            StringBuilder resultStringBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    resultStringBuilder.append(line).append("\n");
                }
            }

            classWriter.replaceHelperFileIfNotEqual(destPackageName, fileToCopy.fileName,
                    resultStringBuilder.toString());
        }
    }

    public static record FileToCopy(String packageName, String fileName) {
    }
}
