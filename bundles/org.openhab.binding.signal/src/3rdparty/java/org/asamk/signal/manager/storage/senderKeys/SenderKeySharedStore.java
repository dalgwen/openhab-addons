package org.asamk.signal.manager.storage.senderKeys;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class SenderKeySharedStore {

    private final static Logger logger = LoggerFactory.getLogger(SenderKeySharedStore.class);
    private final static String TABLE_SENDER_KEY_SHARED = "sender_key_shared";

    private final Database database;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("                    CREATE TABLE sender_key_shared (\n"
                    + "                      _id INTEGER PRIMARY KEY,\n" + "                      uuid BLOB NOT NULL,\n"
                    + "                      device_id INTEGER NOT NULL,\n"
                    + "                      distribution_id BLOB NOT NULL,\n"
                    + "                      timestamp INTEGER NOT NULL,\n"
                    + "                      UNIQUE(uuid, device_id, distribution_id)\n"
                    + "                    ) STRICT;\n" + "");
        }
    }

    SenderKeySharedStore(final Database database) {
        this.database = database;
    }

    public Set<SignalProtocolAddress> getSenderKeySharedWith(final DistributionId distributionId) {
        try (final var connection = database.getConnection()) {
            final var sql = String.format("                    SELECT s.uuid, s.device_id\n"
                    + "                    FROM %s AS s\n" + "                    WHERE s.distribution_id = ?",
                    TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, UuidUtil.toByteArray(distributionId.asUuid()));
                return Utils.executeQueryForStream(statement, this::getSenderKeySharedEntryFromResultSet)
                        .map(k -> k.serviceId.toProtocolAddress(k.deviceId)).collect(Collectors.toSet());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from shared sender key store", e);
        }
    }

    public void markSenderKeySharedWith(final DistributionId distributionId,
            final Collection<SignalProtocolAddress> addresses) {
        final var newEntries = addresses.stream()
                .map(a -> new SenderKeySharedEntry(ServiceId.parseOrThrow(a.getName()), a.getDeviceId()))
                .collect(Collectors.toSet());

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            markSenderKeysSharedWith(connection, distributionId, newEntries);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void clearSenderKeySharedWith(final Collection<SignalProtocolAddress> addresses) {
        final var entriesToDelete = addresses.stream().filter(a -> UuidUtil.isUuid(a.getName()))
                .map(a -> new SenderKeySharedEntry(ServiceId.parseOrThrow(a.getName()), a.getDeviceId()))
                .collect(Collectors.toSet());

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var sql = String.format("                    DELETE FROM %s AS s\n"
                    + "                    WHERE uuid = ? AND device_id = ?", TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var entry : entriesToDelete) {
                    statement.setBytes(1, entry.serviceId.toByteArray());
                    statement.setInt(2, entry.deviceId);
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteAll() {
        try (final var connection = database.getConnection()) {
            final var sql = String.format("                    DELETE FROM %s AS s", TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteAllFor(final ServiceId serviceId) {
        try (final var connection = database.getConnection()) {
            final var sql = String.format(
                    "                    DELETE FROM %s AS s\n" + "                    WHERE uuid = ?\n" + "",
                    TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, serviceId.toByteArray());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteSharedWith(final ServiceId serviceId, final int deviceId, final DistributionId distributionId) {
        try (final var connection = database.getConnection()) {
            final var sql = String.format(
                    "                    DELETE FROM %s AS s\n"
                            + "                    WHERE uuid = ? AND device_id = ? AND distribution_id = ?",
                    TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, serviceId.toByteArray());
                statement.setInt(2, deviceId);
                statement.setBytes(3, UuidUtil.toByteArray(distributionId.asUuid()));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteAllFor(final DistributionId distributionId) {
        try (final var connection = database.getConnection()) {
            final var sql = String.format("                    DELETE FROM %s AS s\n"
                    + "                    WHERE distribution_id = ?\n" + "", TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, UuidUtil.toByteArray(distributionId.asUuid()));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    void addLegacySenderKeysShared(final Map<DistributionId, Set<SenderKeySharedEntry>> sharedSenderKeys) {
        logger.debug("Migrating legacy sender keys shared to database");
        long start = System.nanoTime();
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (final var entry : sharedSenderKeys.entrySet()) {
                markSenderKeysSharedWith(connection, entry.getKey(), entry.getValue());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
        logger.debug("Complete sender keys shared migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private void markSenderKeysSharedWith(final Connection connection, final DistributionId distributionId,
            final Set<SenderKeySharedEntry> newEntries) throws SQLException {
        final var sql = String
                .format("                INSERT OR REPLACE INTO %s (uuid, device_id, distribution_id, timestamp)\n"
                        + "                VALUES (?, ?, ?, ?)\n" + "", TABLE_SENDER_KEY_SHARED);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var entry : newEntries) {
                statement.setBytes(1, entry.serviceId.toByteArray());
                statement.setInt(2, entry.deviceId);
                statement.setBytes(3, UuidUtil.toByteArray(distributionId.asUuid()));
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    private SenderKeySharedEntry getSenderKeySharedEntryFromResultSet(ResultSet resultSet) throws SQLException {
        final var serviceId = ServiceId.parseOrThrow(resultSet.getBytes("uuid"));
        final var deviceId = resultSet.getInt("device_id");
        return new SenderKeySharedEntry(serviceId, deviceId);
    }

    static class SenderKeySharedEntry {
        private final ServiceId serviceId;
        private final int deviceId;

        public SenderKeySharedEntry(@JsonProperty("serviceId") ServiceId serviceId, @JsonProperty("deviceId") int deviceId) {
            super();
            this.serviceId = serviceId;
            this.deviceId = deviceId;
        }

        public ServiceId serviceId() {
            return serviceId;
        }

        public int deviceId() {
            return deviceId;
        }

    }
}
