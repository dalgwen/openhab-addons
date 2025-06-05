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
package org.openhab.binding.wyoming.internal.protocol.message.data;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class RunPipelineData extends WyomingData {
    private String start_stage;
    private String end_stage;

    public RunPipelineData(String start_stage, String end_stage) {
        this.start_stage = start_stage;
        this.end_stage = end_stage;
    }

    public String getStart_stage() {
        return start_stage;
    }

    public void setStart_stage(String start_stage) {
        this.start_stage = start_stage;
    }

    public String getEnd_stage() {
        return end_stage;
    }

    public void setEnd_stage(String end_stage) {
        this.end_stage = end_stage;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        RunPipelineData that = (RunPipelineData) o;
        return Objects.equals(start_stage, that.start_stage) && Objects.equals(end_stage, that.end_stage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start_stage, end_stage);
    }
}
