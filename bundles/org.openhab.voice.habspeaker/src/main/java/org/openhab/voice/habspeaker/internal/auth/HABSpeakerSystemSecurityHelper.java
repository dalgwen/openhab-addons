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
package org.openhab.voice.habspeaker.internal.auth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.openhab.core.OpenHAB;
import org.openhab.core.auth.Authentication;
import org.openhab.core.auth.AuthenticationException;
import org.openhab.core.config.core.ConfigParser;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HABSpeakerSystemSecurityHelper} class allow access to openHAB api security configuration
 * it contains some code copied from the core AuthFilter class so the ws auth works the same, also some code copied
 * from JwtHelper class to mimic the token validation.
 * Could be removed if this HABSpeaker gets more integrated in the future, it's a temporal solution to keep
 * working without request changes to the core team.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@Component(service = HABSpeakerSystemSecurityHelper.class, configurationPid = "org.openhab.restauth", property = Constants.SERVICE_PID
        + "=org.openhab.voice.habspeaker.restauth")
@NonNullByDefault
public class HABSpeakerSystemSecurityHelper {
    private final Logger logger = LoggerFactory.getLogger(HABSpeakerSystemSecurityHelper.class);
    // Api security
    static final String CONFIG_IMPLICIT_USER_ROLE = "implicitUserRole";
    static final String CONFIG_TRUSTED_NETWORKS = "trustedNetworks";
    private boolean implicitUserRole = true;
    private List<CIDR> trustedNetworks = List.of();

    // JWT token
    private static final String KEY_FILE_PATH = OpenHAB.getUserDataFolder() + File.separator + "secrets"
            + File.separator + "rsa_json_web_key.json";

    private static final String ISSUER_NAME = "openhab";
    private static final String AUDIENCE = "openhab";
    private RsaJsonWebKey jwtWebKey;

    public HABSpeakerSystemSecurityHelper() {
        try {
            jwtWebKey = loadKey();
        } catch (Exception e) {
            logger.error("Error while initializing the JWT helper", e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Activate
    protected void activate(Map<String, Object> config) {
        modified(config);
    }

    @Modified
    protected void modified(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            implicitUserRole = ConfigParser.valueAsOrElse(properties.get(CONFIG_IMPLICIT_USER_ROLE), Boolean.class,
                    true);
            trustedNetworks = parseTrustedNetworks(
                    ConfigParser.valueAsOrElse(properties.get(CONFIG_TRUSTED_NETWORKS), String.class, ""));
        }
    }

    // JWT Token
    private RsaJsonWebKey loadKey() throws FileNotFoundException, JoseException, IOException {
        try (final BufferedReader reader = Files.newBufferedReader(Paths.get(KEY_FILE_PATH))) {
            return (RsaJsonWebKey) JsonWebKey.Factory.newJwk(reader.readLine());
        } catch (IOException | JoseException e) {
            throw e;
        }
    }

    /**
     * Performs verifications on a JWT token, then parses it into a {@link AuthenticationException} instance
     *
     * @param jwt the base64-encoded JWT token from the request
     * @return the {@link Authentication} derived from the information in the token
     * @throws AuthenticationException
     */
    public Authentication verifyAndParseJwtAccessToken(String jwt) throws AuthenticationException {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder().setRequireExpirationTime().setAllowedClockSkewInSeconds(30)
                .setRequireSubject().setExpectedIssuer(ISSUER_NAME).setExpectedAudience(AUDIENCE)
                .setVerificationKey(jwtWebKey.getKey()).setJwsAlgorithmConstraints(
                        AlgorithmConstraints.ConstraintType.WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256)
                .build();

        try {
            JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
            String username = jwtClaims.getSubject();
            List<String> roles = jwtClaims.getStringListClaimValue("role");
            String scope = jwtClaims.getStringClaimValue("scope");
            return new Authentication(username, roles.toArray(new String[roles.size()]), scope);
        } catch (InvalidJwtException | MalformedClaimException e) {
            throw new AuthenticationException("Error while processing JWT token", e);
        }
    }

    // Api security
    public boolean isImplicitUserRole(HttpServletRequest request) {
        if (implicitUserRole) {
            return true;
        }
        try {
            byte[] clientAddress = InetAddress.getByName(getClientIp(request)).getAddress();
            return trustedNetworks.stream().anyMatch(networkCIDR -> networkCIDR.isInRange(clientAddress));
        } catch (IOException e) {
            logger.debug("Error validating trusted networks: {}", e.getMessage());
            return false;
        }
    }

    private List<CIDR> parseTrustedNetworks(String value) {
        var cidrList = new ArrayList<CIDR>();
        for (var cidrString : value.split(",")) {
            try {
                if (!cidrString.isBlank()) {
                    cidrList.add(new CIDR(cidrString.trim()));
                }
            } catch (UnknownHostException e) {
                logger.warn("Error parsing trusted network cidr: {}", cidrString);
            }
        }
        return cidrList;
    }

    private String getClientIp(HttpServletRequest request) throws UnknownHostException {
        String ipForwarded = Objects.requireNonNullElse(request.getHeader("x-forwarded-for"), "");
        String clientIp = ipForwarded.split(",")[0];
        return clientIp.isBlank() ? request.getRemoteAddr() : clientIp;
    }

    private static class CIDR {
        private static final Pattern CIDR_PATTERN = Pattern.compile("(?<networkAddress>.*?)/(?<prefixLength>\\d+)");
        private final byte[] networkBytes;
        private final int prefix;

        public CIDR(String cidr) throws UnknownHostException {
            Matcher m = CIDR_PATTERN.matcher(cidr);
            if (!m.matches()) {
                throw new UnknownHostException();
            }
            this.prefix = Integer.parseInt(m.group("prefixLength"));
            this.networkBytes = InetAddress.getByName(m.group("networkAddress")).getAddress();
        }

        public boolean isInRange(byte[] address) {
            if (networkBytes.length != address.length) {
                return false;
            }
            int p = this.prefix;
            int i = 0;
            while (p > 8) {
                if (networkBytes[i] != address[i]) {
                    return false;
                }
                ++i;
                p -= 8;
            }
            final int m = (65280 >> p) & 255;
            return (networkBytes[i] & m) == (address[i] & m);
        }
    }
}
