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
package org.openhab.binding.voltronic.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Base} is responsible for handling commands, which are
 * sent to one of the channels, irrespective of the data transfer technology.
 *
 * @author Дилян Палаузов
 */
@NonNullByDefault
abstract class Base extends BaseThingHandler implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(Base.class);
    private TranslationProvider translationProvider;
    private final Bundle bundle = FrameworkUtil.getBundle(Base.class);
    private boolean qpigsHasInputPower;
    protected boolean isOnline;

    Base(Thing thing, TranslationProvider translationProvider) {
        super(thing);
        this.translationProvider = translationProvider;
    }

    /**
     * There are many technologies how to communicate with Voltronic inverters, this interface is the common
     * denominator. Returning <code>null</code> means the device is disconnected and the Thing should go offline.
     *
     * @param c The command to send to the inverter, which already includes CRC and terminating <code>\r</code>.
     * @return What the inverter answered, excluding the final <code>\r</code>; <code>null</code> on timeout when no
     *         data was received; empty string when data was received but within a timeout no <code>\r</code> arrived.
     */
    @Nullable
    protected abstract String command(byte[] c);

    private String translate(String t) {
        String ret = translationProvider.getText(bundle, t, null, null);
        return ret == null ? "" : ret; // cannot happen, but this @NonNullByDefault is pernetrant
    }

    String sendCommand(String cmd) {
        final byte[] cmd_ = cmd.getBytes();
        final byte[] crc = CRCUtil.getCRCByte(cmd);
        final byte[] instruction = new byte[cmd_.length + crc.length];
        System.arraycopy(cmd_, 0, instruction, 0, cmd_.length);
        System.arraycopy(crc, 0, instruction, cmd_.length, crc.length);
        try {
            String received = command(instruction);
            if (received == null || received.isEmpty() || !CRCUtil.checkCRC(received)
                    || "NAK".equals(received.substring(1, received.length() - 2))) {
                if (logger.isDebugEnabled())
                    if (received == null)
                        logger.debug("sendCommand {} received null, retrying", cmd);
                    else if (received.isEmpty())
                        logger.debug("sendCommand {} received empty string, retrying", cmd);
                    else if (!CRCUtil.checkCRC(received))
                        logger.debug("sendCommand {} CRC does not match, retrying", cmd);
                    else if ("NAK".equals(received.substring(1, received.length() - 2)))
                        logger.debug("sendCommand {} received NAK, retrying", cmd);
                if ((received = command(instruction)) == null || received.isEmpty() || !CRCUtil.checkCRC(received)
                        || "NAK".equals(received.substring(1, received.length() - 2))) {
                    if (logger.isDebugEnabled())
                        if (received == null)
                            logger.debug("sendCommand {} received null, try one more time", cmd);
                        else if (received.isEmpty())
                            logger.debug("sendCommand {} received empty string, try one more time", cmd);
                        else if (!CRCUtil.checkCRC(received))
                            logger.debug("sendCommand {} CRC does not match, try one more time", cmd);
                        else if ("NAK".equals(received.substring(1, received.length() - 2)))
                            logger.debug("sendCommand {} received NAK, try one more time", cmd);
                    received = command(instruction);
                }
            }
            if (received == null || !CRCUtil.checkCRC(received)) {
                if (isOnline)
                    goOffline();
                throw new TimeoutException();
            }
            if (!isOnline)
                scheduler.execute(this);
            return received.substring(1, received.length() - 2);
        } catch (NullPointerException e) {
            if (isOnline)
                goOffline();
            throw new TimeoutException();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, org.openhab.core.types.Command command) {
        if (!isOnline) {
            updateState(channelUID, UnDefType.UNDEF);
            return;
        }
        try {
            if (command instanceof RefreshType) {
                switch (channelUID.getGroupId()) {
                    case "qmod":
                        handleQMOD();
                        return;
                    case "qpigs":
                        handleQPIGS();
                        return;
                    case "qpiws":
                        handleQPIWS();
                        return;
                    case "qflag":
                        handleQFLAG();
                        return;
                    case "qpiri":
                        handleQPIRI();
                        return;
                    default:
                        logger.error("REFRESH requested for channel {}", channelUID.getId());
                        return;
                }
            }

            switch (channelUID.getGroupId()) {
                case "qflag":
                    if (command instanceof OnOffType c) {
                        final Consumer<String> f = (s) -> {
                            sendCommand(c == OnOffType.ON ? "PE" + s : "PD" + s);
                            handleQPIRIandQFLAG();
                        };
                        switch (channelUID.getIdWithoutGroup()) {
                            case "mute":
                                sendCommand(c == OnOffType.ON ? "PDa" : "PEa");
                                handleQPIRIandQFLAG();
                                return;
                            case "overloadBypass":
                                f.accept("b");
                                return;
                            case "powerSaving":
                                f.accept("j");
                                return;
                            case "lcdReturn":
                                f.accept("k");
                                return;
                            case "overloadRestart":
                                f.accept("u");
                                return;
                            case "overtemperatureRestart":
                                f.accept("v");
                                return;
                            case "backlight":
                                f.accept("x");
                                return;
                            case "alarm":
                                f.accept("y");
                                return;
                            case "faultCode":
                                f.accept("z");
                                return;
                            default:
                                logger.error("Unknown channel", channelUID.getId());
                                return;

                        }
                    }
                    logger.error("Channel {} receives only ON/OFF commands", channelUID.getId());
                    return;
                case "qpiri":
                    if (command instanceof Number n) {
                        switch (channelUID.getIdWithoutGroup()) {
                            case "chargerSourcePriority":
                                sendCommand("PCP0" + n.toString());
                                handleQPIRIandQFLAG();
                                return;
                            case "outputSourcePriority":
                                sendCommand("POP0" + n.toString());
                                handleQPIRIandQFLAG();
                                return;
                            default:
                                logger.error("Channel {} does not handle commands", channelUID.getId());
                                return;
                        }
                    }
                    logger.error("Command {} to channel {} not a Number", command, channelUID.getId());
                    return;
                default:
                    logger.error("Command requested for unsupported channel {}", channelUID.getId());
            }
        } catch (TimeoutException e) {
        }
    }

    protected void setProperties() {
        Boolean sccFirmwareUpdated = null;
        {
            String r = sendCommand("QPIGS");
            qpigsHasInputPower = r.length() > 102;
            if (!r.isEmpty() && !"NAK".equals(r)) {
                try {
                    @SuppressWarnings("resource")
                    Scanner s = new Scanner(r);
                    s.useDelimiter(" ");
                    for (int i = 0; i < 16; i++)
                        s.next();
                    r = s.next(); // device status
                    sccFirmwareUpdated = r.charAt(2) == '1';
                } catch (NoSuchElementException e) {
                    logger.error("Run reply was[{}]", r, e);
                }
                if (!qpigsHasInputPower) {
                    // TODO in the channel-type for inputPower replace Measurement with Calculation
                }
            }
        }

        Map<String, String> properties = new HashMap<>();
        final BiConsumer<String, String> f = (p, c) -> {
            final String r = sendCommand(c);
            if (!r.isEmpty() && !"NAK".equals(r))
                properties.put(translate(p), r);
        };

        final String sn = sendCommand("QID");
        if (!sn.isEmpty() && !"NAK".equals(sn))
            properties.put(translate("property.serial-number"), sn.replaceAll("....", "$0 "));

        final String sid = sendCommand("QSID");
        if (!sid.isEmpty() && !"NAK".equals(sid)) {
            final int length = Integer.valueOf(sid.substring(0, 2));
            final String dsn = sid.substring(2, 2 + length);
            if (dsn.equals(sn)) {
                final String remaining = sid.substring(length + 2);
                if (!remaining.matches("0*"))
                    properties.put(translate("property.serial-number"),
                            dsn.replaceAll("....", "$0 ") + '•' + remaining);
            } else
                properties.put(translate("property.device-serial-number"), dsn.replaceAll("....", "$0 "));
        }
        f.accept("property.main-cpu-firmware", "QVFW");
        f.accept("property.model-name", "QMN");
        f.accept("property.another-cpu-firmware", "QVFW2");
        f.accept("property.another-cpu-firmware-remote", "QVFW3");
        f.accept("property.device-protocol", "QPI");
        f.accept("property.bluetooth-version", "VERFW");
        final Function<Boolean, String> translateYesNo = b -> {
            @Nullable
            Class<?> c = null;
            try {
                c = Class.forName("org.openhab.core.automation.RuleManager");
            } catch (ClassNotFoundException e) {
            }
            if (b) {
                @Nullable
                String ret = translationProvider.getText(FrameworkUtil.getBundle(c),
                        "module-type.core.RunRuleAction.config.considerConditions.option.true", null, null);
                return ret == null ? "Yes" : ret;
            }
            String ret = translationProvider.getText(FrameworkUtil.getBundle(c),
                    "module-type.core.RunRuleAction.config.considerConditions.option.false", null, null);
            return ret == null ? "No" : ret;
        };

        if (sccFirmwareUpdated != null)
            properties.put(translate("property.scc-firmware-updated"), translateYesNo.apply(sccFirmwareUpdated));
        String res = sendCommand("QGMN");
        if (!res.isEmpty() && !"NAK".equals(res))
            // https://github.com/ardupic/voltronic-inverter-communication-protocols/blob/main/Axpert MKS II%26MKS
            // III%26MKS IV RS232 Protocol 20201109.pdf page 13
            properties.put(translate("property.general-model-name"), switch (res) {
                case "001" -> "VP-5000";
                case "002" -> "VM-5000";
                case "003" -> "VP-3000";
                case "004" -> "VM-3000";
                case "005" -> "MKS+-2000-48-LV-LY";
                case "006" -> "MLV 3KVA | Axpert MLV 3K-24";
                case "007" -> "PLV 3KVA | Axpert PLV 3K-24";
                case "008" -> "MKS HV 24V 3KVA | Axpert MKS 3KP";
                case "009" -> "KS HV 24V 3KVA | Axpert KS 3KP";
                case "010" -> "MKS HV 24V 5KVA | Axpert MKS 5KP";
                case "011" -> "KS HV 24V 5KVA | Axpert KS 5KP";
                case "012" -> "MKS HV 48V 4K/5KVA/64V | Axpert MKS 4K/5K 64VDC";
                case "013" -> "KS HV 48V 4/5KVA/64V | Axpert KS 4K/5K 64VDS";
                case "014" -> "Axpert MKS 4/5KVA | Axpert MKS 4K/5K";
                case "015" -> "Axpert KS 4/5KVA | Axpert KS 4K/5K";
                case "016" -> "ALFA M-5000";
                case "017" -> "ALFA P-5000";
                case "018" -> "Axpert Plus Duo/Tri 5KVA";
                case "019" -> "EPS 5KVA | Axpert EPS 5KW";
                case "020" -> "EPS M5K | Axpert EPS M-5KW";
                case "021" -> "EPS 3/3 5KW | Axpert EPS 33-5KW";
                case "022" -> "Axpert MKS II 5KW";
                case "023" -> "Axpert KING 5KW";
                case "024" -> "Axpert KING 3KW";
                case "025" -> "Axpert MKS II 5KW | APT MKS II 5KW (Feed-in grid function)";
                case "026" -> "Axpert MLV 5KW | Axpert MLV 5KW-48V";
                case "027" -> "Axpert VMIII";
                case "028" -> "Axpert VMIII | APT VMIII 3.2KW (Feed-in grid function)";
                case "029" -> "Axpert VMII";
                case "030" -> "Axpert VMII | Fusion VMII (Feed-in grid function)";
                case "031" -> "Axpert MKS II 5KW | Phocos MKS II 5KW (Discharge current time function)";
                case "032" -> "Axpert MKS | Axpert MKS Zero LV 0.7KW";
                case "033" -> "Axpert MKS | Axpert MKS Zero LV 1.4KW";
                case "034" -> "Axpert MKS | Axpert MKS Zero LV 2.6KW";
                case "035" -> "Axpert King 5KW | Axpert King 5KW (Query PV generated and output load energy)";
                case "036" -> "Axpert King 3KW | Axpert King 3KW (Query PV generated and output load energy)";
                case "037" -> "Axpert VMIII | Axpert VMIII (Query PV generated and output load energy)";
                case "038" ->
                    "Axpert MKS II 5KW | Phocos MKS II 5KW LV (Discharge current time function)(Query PV generated and output load energy)";
                case "039" -> "Axpert MKS II 5KW LV | Phocos MKS II 5KW LV (Discharge current time function)";
                case "040" -> "Axpert SE 3.5K";
                case "041" -> "Axpert SE 5.5K";
                case "042" -> "Axpert MKS III 5KW";
                case "043" -> "MAX3.6K";
                case "044" -> "MAX7.2K";
                case "045" -> "MAX5K LV";
                default -> res;
            });

        res = sendCommand("QWFS");
        if (!res.isEmpty() && !"NAK".equals(res))
            properties.put(translate("properties.wifi-module"), switch (res) {
                case "0" -> translateYesNo.apply(false);
                case "1" -> translateYesNo.apply(true);
                default -> "Unknown";
            });

        res = sendCommand("QBOOT");
        if (!res.isEmpty() && !"NAK".equals(res))
            properties.put(translate("property.dsp-has-bootstrap"), switch (res) {
                case "0" -> translateYesNo.apply(false);
                case "1" -> translateYesNo.apply(true);
                default -> "Unknown";
            });

        // as BaseThingHandler.java, but also remove properties, if they are not present
        boolean propertiesUpdated = false;
        for (Entry<String, String> property : properties.entrySet()) {
            String existingPropertyValue = thing.getProperties().get(property.getKey());
            if (existingPropertyValue == null || !existingPropertyValue.equals(property.getValue()))
                propertiesUpdated = true;
        }
        for (String key : thing.getProperties().keySet())
            if (!properties.containsKey(key))
                propertiesUpdated = true;

        if (propertiesUpdated) {
            thing.setProperties(properties);
            synchronized (this) {
                if (getCallback() != null) {
                    getCallback().thingUpdated(thing);
                } else {
                    logger.warn(
                            "Handler {} tried updating its thing's properties although the handler was already disposed.",
                            getClass().getSimpleName());
                }
            }
        }

        updateStatus(ThingStatus.ONLINE);
        handleQPIRIandQFLAG();
    }

    protected void unsetChannelsAndProperties() {
        thing.setProperties(Map.of());
        synchronized (this) {
            if (getCallback() != null) {
                getCallback().thingUpdated(thing);
            }
        }
        thing.getChannels().forEach(c -> updateState(c.getUID(), UnDefType.UNDEF));
    }

    protected void pollingCode() {
        try {
            handleQMOD();
            handleQPIGS();
            handleQPIWS();
        } catch (TimeoutException e) {
        }
    }

    private void handleQPIWS() {
        String r = sendCommand("QPIWS");
        if (r.isEmpty() || "NAK".equals(r)) {
            return;
        }
        if (r.indexOf("1") == -1) {
            updateState("qpiws#warnings", new StringType(""));
            updateState("qpiws#faults", new StringType(""));
            return;
        }
        ArrayList<String> warnings = new ArrayList<>();
        ArrayList<String> faults = new ArrayList<>();
        Consumer<Integer> addWarning = position -> {
            if (r.charAt(position) == '1')
                warnings.add(translate("channel-type.voltronic.warnings.state.option." + Integer.toString(position)));
        };
        Consumer<Integer> addFault = position -> {
            if (r.charAt(position) == '1')
                faults.add(translate("channel-type.voltronic.warnings.state.option." + Integer.toString(position)));
        };
        Consumer<Integer> soSo = r.charAt(1) == '1' ? addFault : addWarning;
        try {
            for (int i : List.of(0, 5, 12, 13, 14, 15, 17))
                addWarning.accept(i);
            for (int i : List.of(1, 2, 3, 4, 6, 7, 8, 18, 19, 20, 21, 22, 23, 24, 30))
                addFault.accept(i);
            for (int i : List.of(9, 10, 11, 16, 25, 26, 27, 28, 29))
                soSo.accept(i);
            if (r.length() > 30 && r.indexOf("1", 31) != -1)
                warnings.add(31, translate("qpiws.too-long"));
            updateState("qpiws#warnings", new StringType(String.join(",", warnings)));
            updateState("qpiws#faults", new StringType(String.join(",", faults)));
        } catch (IndexOutOfBoundsException e) {
        }
    }

    private void handleQPIGS() {
        String r = sendCommand("QPIGS");
        if (r.isEmpty() || "NAK".equals(r))
            return;
        try {
            @SuppressWarnings("resource")
            Scanner s = new Scanner(r);
            s.useDelimiter(" ");
            updateState("qpigs#gridVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpigs#gridFrequency", new QuantityType<>(s.nextFloat(), Units.HERTZ));
            updateState("qpigs#outputVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpigs#outputFrequency", new QuantityType<>(s.nextFloat(), Units.HERTZ));
            updateState("qpigs#outputApparentPower", new QuantityType<>(s.nextInt(), Units.VOLT_AMPERE));
            updateState("qpigs#outputActivePower", new QuantityType<>(s.nextInt(), Units.WATT));
            updateState("qpigs#outputLoad", new DecimalType(s.nextInt()));
            updateState("qpigs#busVoltage", new QuantityType<>(s.nextInt(), Units.VOLT));
            updateState("qpigs#batteryVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            int batteryCharge = s.nextInt();
            updateState("qpigs#batteryLevel", new DecimalType(s.nextInt()));
            updateState("qpigs#heatSinkTemperature", new QuantityType<>(s.nextInt(), SIUnits.CELSIUS));
            int inputCurrent = s.nextInt();
            updateState("qpigs#inputCurrent", new QuantityType<>(inputCurrent, Units.AMPERE));
            updateState("qpigs#inputVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            float sccVoltage = s.nextFloat();
            updateState("qpigs#sccVoltage", new QuantityType<>(sccVoltage, Units.VOLT));
            updateState("qpigs#batteryChargingCurrent", new QuantityType<>(batteryCharge - s.nextInt(), Units.AMPERE));
            String status = s.next(); // device status
            updateState("qpigs#chargingMode", new StringType(switch (status.substring(5)) {
                case "000" -> "0";
                case "101" -> "5";
                case "110" -> "6";
                case "111" -> "7";
                default -> "8";
            }));
            updateState("qpigs#loadStatus", status.charAt(3) == '0' ? OnOffType.OFF : OnOffType.ON);
            updateState("qpigs#batteryVoltageToSteady", status.charAt(4) == '0' ? OnOffType.OFF : OnOffType.ON);
            if (qpigsHasInputPower) {
                s.next();
                s.next();
                updateState("qpigs#inputPower", new QuantityType<>(s.nextInt(), Units.WATT));
            } else {
                // According to
                // https://github.com/manio/skymax-demo/blob/126b5e30a3423358c49494f6d6854adbe215d02a/main.cpp#L240-L244
                // the QPIGS reply ends with device status and the PV Input Power is calculated by
                updateState("qpigs#inputPower", new QuantityType<>((int) (inputCurrent * sccVoltage), Units.WATT));
            }
        } catch (NoSuchElementException e) {
            logger.error("handleQPIGS reply was[{}]", r, e);
        }
    }

    private void handleQMOD() {
        final String r = sendCommand("QMOD");
        updateState("qmod#mode", !r.isEmpty() && !"NAK".equals(r) ? new StringType(r) : UnDefType.UNDEF);
    }

    private void handleQFLAG() {
        final String r = sendCommand("QFLAG");
        updateState("qflag#mute", // the buzzer/mute is the opposite of all others
                Pattern.matches("E.*a.*D.*", r) ? OnOffType.OFF
                        : Pattern.matches(".*D.*a.*", r) ? OnOffType.ON : UnDefType.UNDEF);
        for (var e : Map.of("qflag#overloadBypass", "b", "qflag#powerSaving", "j", "qflag#lcdReturn", "k",
                "qflag#overloadRestart", "u", "qflag#overtemperatureRestart", "v", "qflag#backlight", "x",
                "qflag#alarm", "y", "qflag#faultCode", "z").entrySet())
            updateState(e.getKey(), Pattern.matches("E.*" + e.getValue() + ".*D.*", r) ? OnOffType.ON
                    : Pattern.matches(".*D.*" + e.getValue() + ".*", r) ? OnOffType.OFF : UnDefType.UNDEF);
    }

    private void handleQPIRI() throws TimeoutException {
        String r = sendCommand("QPIRI");
        if (r.isEmpty() || "NAK".equals(r))
            return;
        try {
            @SuppressWarnings("resource")
            Scanner s = new Scanner(r);
            s.useDelimiter(" ");
            updateState("qpiri#gridRatingVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#gridRatingCurrent", new QuantityType<>(s.nextFloat(), Units.AMPERE));
            updateState("qpiri#outputRatingVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#outputRatingFrequency", new QuantityType<>(s.nextFloat(), Units.HERTZ));
            updateState("qpiri#outputRatingCurrent", new QuantityType<>(s.nextFloat(), Units.AMPERE));
            updateState("qpiri#outputRatingApparentPower", new QuantityType<>(s.nextInt(), Units.VOLT_AMPERE));
            updateState("qpiri#outputRatingActivePower", new QuantityType<>(s.nextInt(), Units.WATT));
            updateState("qpiri#batteryRatingVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#batteryRechargeVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#batteryUnderVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#batteryBulkVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#batteryFloatVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            updateState("qpiri#batteryType", new StringType(s.next()));
            updateState("qpiri#maxACChargingCurrent", new QuantityType<>(s.nextInt(), Units.AMPERE));
            updateState("qpiri#maxChargingCurrent", new QuantityType<>(s.nextInt(), Units.AMPERE));
            final String inputVoltageRange = s.next();
            updateState("qpiri#inputVoltageRange", "0".equals(inputVoltageRange) ? OnOffType.OFF
                    : "1".equals(inputVoltageRange) ? OnOffType.ON : UnDefType.UNDEF);
            updateState("qpiri#outputSourcePriority", new DecimalType(s.nextInt()));
            updateState("qpiri#chargerSourcePriority", new DecimalType(s.nextInt()));
            updateState("qpiri#parallelMaxNum", new DecimalType(s.nextInt()));
            updateState("qpiri#machineType", new StringType(s.next()));
            final String topology = s.next();
            updateState("qpiri#topology",
                    "0".equals(topology) ? OnOffType.OFF : "1".equals(topology) ? OnOffType.ON : UnDefType.UNDEF);
            final String outputMode = s.next();
            updateState("qpiri#outputMode",
                    new StringType(outputMode.length() == 2 ? outputMode.substring(1) : outputMode));
            updateState("qpiri#batteryRedischargeVoltage", new QuantityType<>(s.nextFloat(), Units.VOLT));
            final String pvConditionForParallel = s.next();
            updateState("qpiri#pvConditionForParallel", "0".equals(pvConditionForParallel) ? OnOffType.OFF
                    : "1".equals(pvConditionForParallel) ? OnOffType.ON : UnDefType.UNDEF);
            final String pvPowerBalance = s.next();
            updateState("qpiri#pvPowerBalance", "0".equals(pvPowerBalance) ? OnOffType.OFF
                    : "1".equals(pvPowerBalance) ? OnOffType.ON : UnDefType.UNDEF);
        } catch (NoSuchElementException e) {
            logger.error("handleQPIRI reply was[{}]", r, e);
        }
    }

    protected void goOffline() {
        if (isOnline) {
            if (thing.getStatus() != ThingStatus.UNINITIALIZED)
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE);
            unsetChannelsAndProperties();
        } else if (thing.getStatus() == ThingStatus.INITIALIZING)
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NOT_YET_READY);
        isOnline = false;
    }

    private void handleQPIRIandQFLAG() {
        handleQPIRI();
        handleQFLAG();
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(VoltronicActions.class);
    }
}
