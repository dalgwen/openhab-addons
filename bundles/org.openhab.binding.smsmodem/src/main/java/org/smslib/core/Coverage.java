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

package org.smslib.core;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class Coverage {
    String msisdn;

    boolean coverage;

    double creditsUsed;

    public Coverage(String msisdn) {
        this.msisdn = msisdn;
        this.coverage = false;
        this.creditsUsed = 0;
    }

    public String getMsisdn() {
        return this.msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public boolean getCoverage() {
        return this.coverage;
    }

    public void setCoverage(boolean coverage) {
        this.coverage = coverage;
    }

    public double getCreditsUsed() {
        return this.creditsUsed;
    }

    public void setCreditsUsed(double creditsUsed) {
        this.creditsUsed = creditsUsed;
    }

    @Override
    public String toString() {
        StringBuffer b = new StringBuffer(1024);
        b.append(String.format("MSISDN: %s%n", getMsisdn()));
        b.append(String.format("Is destination covered?: %b%n", getCoverage()));
        b.append(String.format("Credits used: %f%n", getCreditsUsed()));
        return b.toString();
    }
}
