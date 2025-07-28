package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UsernameLinkUrl;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.cds.CdsiV2Service;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidArgumentException;
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidTokenException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.asamk.signal.manager.config.ServiceConfig.MAXIMUM_ONE_OFF_REQUEST_SIZE;
import static org.asamk.signal.manager.util.Utils.handleResponseException;

public class RecipientHelper {

    private static final Logger logger = LoggerFactory.getLogger(RecipientHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;

    public RecipientHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
    }

    public SignalServiceAddress resolveSignalServiceAddress(RecipientId recipientId) {
        final var address = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId);
        if (address.number().isEmpty() || address.serviceId().isPresent()) {
            return address.toSignalServiceAddress();
        }

        // Address in recipient store doesn't have a uuid, this shouldn't happen
        // Try to retrieve the uuid from the server
        final var number = address.number().get();
        final ServiceId serviceId;
        try {
            serviceId = getRegisteredUserByNumber(number);
        } catch (UnregisteredRecipientException e) {
            logger.warn("Failed to get uuid for e164 number: {}", number);
            // Return SignalServiceAddress with unknown UUID
            return address.toSignalServiceAddress();
        } catch (IOException e) {
            logger.warn("Failed to get uuid for e164 number: {}", number, e);
            // Return SignalServiceAddress with unknown UUID
            return address.toSignalServiceAddress();
        }
        return account.getRecipientAddressResolver()
                .resolveRecipientAddress(account.getRecipientResolver().resolveRecipient(serviceId))
                .toSignalServiceAddress();
    }

    public Set<RecipientId> resolveRecipients(Collection<RecipientIdentifier.Single> recipients) throws UnregisteredRecipientException, IOException {
        final var recipientIds = new HashSet<RecipientId>(recipients.size());
        for (var number : recipients) {
            final var recipientId = resolveRecipient(number);
            recipientIds.add(recipientId);
        }
        return recipientIds;
    }

    public RecipientId resolveRecipient(final RecipientIdentifier.Single recipient) throws UnregisteredRecipientException {
        if (recipient instanceof RecipientIdentifier.Uuid(UUID uuid)) {
            return account.getRecipientResolver().resolveRecipient(ACI.from(uuid));
        } else if (recipient instanceof RecipientIdentifier.Pni(UUID pni)) {
            return account.getRecipientResolver().resolveRecipient(PNI.from(pni));
        } else if (recipient instanceof RecipientIdentifier.Number(String number)) {
            return account.getRecipientStore().resolveRecipientByNumber(number, () -> {
                try {
                    return getRegisteredUserByNumber(number);
                } catch (Exception e) {
                    return null;
                }
            });
        } else if (recipient instanceof RecipientIdentifier.Username(String username)) {
            try {
                return resolveRecipientByUsernameOrLink(username, false);
            } catch (Exception e) {
                return null;
            }
        }
        throw new AssertionError("Unexpected RecipientIdentifier: " + recipient);
    }

    public RecipientId resolveRecipientByUsernameOrLink(
            String username,
            boolean forceRefresh
    ) throws UnregisteredRecipientException, IOException {
        final Username finalUsername;
        try {
            finalUsername = getUsernameFromUsernameOrLink(username);
        } catch (IOException | BaseUsernameException e) {
            throw new RuntimeException(e);
        }
        if (forceRefresh) {
            try {
                final var aci = handleResponseException(dependencies.getUsernameApi().getAciByUsername(finalUsername));
                return account.getRecipientStore().resolveRecipientTrusted(aci, finalUsername.getUsername());
            } catch (NonSuccessfulResponseCodeException e) {
                if (e.code == 404) {
                    throw new UnregisteredRecipientException(new org.asamk.signal.manager.api.RecipientAddress(null,
                            null,
                            null,
                            username));
                }
                logger.debug("Failed to get uuid for username: {}", username, e);
                throw e;
            }
        }
        return account.getRecipientStore().resolveRecipientByUsername(finalUsername.getUsername(), () -> {
            try {
                return handleResponseException(dependencies.getUsernameApi().getAciByUsername(finalUsername));
            } catch (Exception e) {
                return null;
            }
        });
    }

    private Username getUsernameFromUsernameOrLink(String username) throws BaseUsernameException, IOException {
        try {
            final var usernameLinkUrl = UsernameLinkUrl.fromUri(username);
            final var components = usernameLinkUrl.getComponents();
            final var encryptedUsername = handleResponseException(dependencies.getUsernameApi()
                    .getEncryptedUsernameFromLinkServerId(components.getServerId()));
            final var link = new Username.UsernameLink(components.getEntropy(), encryptedUsername);

            return Username.fromLink(link);
        } catch (UsernameLinkUrl.InvalidUsernameLinkException e) {
            return new Username(username);
        }
    }

    public Optional<RecipientId> resolveRecipientOptional(final RecipientIdentifier.Single recipient) {
        try {
            return Optional.of(resolveRecipient(recipient));
        } catch (UnregisteredRecipientException e) {
            if (recipient instanceof RecipientIdentifier.Number(String number)) {
                return account.getRecipientStore().resolveRecipientByNumberOptional(number);
            } else {
                return Optional.empty();
            }
        }
    }

    public void refreshUsers() throws IOException {
        getRegisteredUsers(account.getRecipientStore().getAllNumbers(), false);
    }

    public RecipientId refreshRegisteredUser(RecipientId recipientId) throws IOException, UnregisteredRecipientException {
        final var address = resolveSignalServiceAddress(recipientId);
        if (address.getNumber().isEmpty()) {
            return recipientId;
        }
        final var number = address.getNumber().get();
        final var serviceId = getRegisteredUserByNumber(number);
        return account.getRecipientTrustedResolver()
                .resolveRecipientTrusted(new SignalServiceAddress(serviceId, number));
    }

    public Map<String, RegisteredUser> getRegisteredUsers(
            final Set<String> numbers
    ) throws IOException {
        if (numbers.size() > MAXIMUM_ONE_OFF_REQUEST_SIZE) {
            final var allNumbers = new HashSet<>(account.getRecipientStore().getAllNumbers()) {{
                addAll(numbers);
            }};
            return getRegisteredUsers(allNumbers, false);
        } else {
            return getRegisteredUsers(numbers, true);
        }
    }

    private Map<String, RegisteredUser> getRegisteredUsers(
            final Set<String> numbers,
            final boolean isPartialRefresh
    ) throws IOException {
        Map<String, RegisteredUser> registeredUsers = getRegisteredUsersV2(numbers, isPartialRefresh);

        // Store numbers as recipients, so we have the number/uuid association
        registeredUsers.forEach((number, u) -> account.getRecipientTrustedResolver()
                .resolveRecipientTrusted(u.aci, u.pni, Optional.of(number)));

        final var unregisteredUsers = new HashSet<>(numbers);
        unregisteredUsers.removeAll(registeredUsers.keySet());
        account.getRecipientStore().markUndiscoverablePossiblyUnregistered(unregisteredUsers);
        account.getRecipientStore().markDiscoverable(registeredUsers.keySet());

        return registeredUsers;
    }

    private ServiceId getRegisteredUserByNumber(final String number) throws IOException, UnregisteredRecipientException {
        final Map<String, RegisteredUser> aciMap;
        try {
            aciMap = getRegisteredUsers(Set.of(number), true);
        } catch (NumberFormatException e) {
            throw new UnregisteredRecipientException(new org.asamk.signal.manager.api.RecipientAddress(number));
        }
        final var user = aciMap.get(number);
        if (user == null) {
            throw new UnregisteredRecipientException(new org.asamk.signal.manager.api.RecipientAddress(number));
        }
        return user.getServiceId();
    }

    private Map<String, RegisteredUser> getRegisteredUsersV2(
            final Set<String> numbers,
            boolean isPartialRefresh
    ) throws IOException {
        final var previousNumbers = isPartialRefresh ? Set.<String>of() : account.getCdsiStore().getAllNumbers();
        final var newNumbers = new HashSet<>(numbers) {{
            removeAll(previousNumbers);
        }};
        if (newNumbers.isEmpty() && previousNumbers.isEmpty()) {
            logger.debug("No new numbers to query.");
            return Map.of();
        }
        logger.trace("Querying CDSI for {} new numbers ({} previous), isPartialRefresh={}",
                newNumbers.size(),
                previousNumbers.size(),
                isPartialRefresh);
        final var token = previousNumbers.isEmpty()
                ? Optional.<byte[]>empty()
                : Optional.ofNullable(account.getCdsiToken());

        final CdsiV2Service.Response response;
        try {
            response = handleResponseException(dependencies.getCdsApi()
                    .getRegisteredUsers(token.isEmpty() ? Set.of() : previousNumbers,
                            newNumbers,
                            account.getRecipientStore().getServiceIdToProfileKeyMap(),
                            token,
                            null,
                            dependencies.getLibSignalNetwork(),
                            newToken -> {
                                if (isPartialRefresh) {
                                    account.getCdsiStore().updateAfterPartialCdsQuery(newNumbers);
                                    // Not storing newToken for partial refresh
                                } else {
                                    final var fullNumbers = new HashSet<>(previousNumbers) {{
                                        addAll(newNumbers);
                                    }};
                                    final var seenNumbers = new HashSet<>(numbers) {{
                                        addAll(newNumbers);
                                    }};
                                    account.getCdsiStore().updateAfterFullCdsQuery(fullNumbers, seenNumbers);
                                    account.setCdsiToken(newToken);
                                    account.setLastRecipientsRefresh(System.currentTimeMillis());
                                }
                            }));
        } catch (CdsiInvalidTokenException | CdsiInvalidArgumentException e) {
            account.setCdsiToken(null);
            account.getCdsiStore().clearAll();
            throw e;
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }
        logger.debug("CDSI request successful, quota used by this request: {}", response.getQuotaUsedDebugOnly());

        final var registeredUsers = new HashMap<String, RegisteredUser>();
        response.getResults()
                .forEach((key, value) -> registeredUsers.put(key,
                        new RegisteredUser(value.getAci(), Optional.of(value.getPni()))));
        return registeredUsers;
    }

    public record RegisteredUser(Optional<ACI> aci, Optional<PNI> pni) {

        public RegisteredUser {
            aci = aci.isPresent() && aci.get().isUnknown() ? Optional.empty() : aci;
            pni = pni.isPresent() && pni.get().isUnknown() ? Optional.empty() : pni;
            if (aci.isEmpty() && pni.isEmpty()) {
                throw new AssertionError("Must have either a ACI or PNI!");
            }
        }

        public ServiceId getServiceId() {
            return aci.map(a -> (ServiceId) a).or(this::pni).orElse(null);
        }
    }
}
