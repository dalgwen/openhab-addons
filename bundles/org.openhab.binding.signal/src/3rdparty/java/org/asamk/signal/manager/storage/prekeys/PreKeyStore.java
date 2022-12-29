package org.asamk.signal.manager.storage.prekeys;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceIdType;

public class PreKeyStore implements org.signal.libsignal.protocol.state.PreKeyStore {

    private static final String TABLE_PRE_KEY = "pre_key";
    private final static Logger logger = LoggerFactory.getLogger(PreKeyStore.class);

    private final Database database;
    private final int accountIdType;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "                    CREATE TABLE pre_key (\n" + "                      _id INTEGER PRIMARY KEY,\n"
                            + "                      account_id_type INTEGER NOT NULL,\n"
                            + "                      key_id INTEGER NOT NULL,\n"
                            + "                      public_key BLOB NOT NULL,\n"
                            + "                      private_key BLOB NOT NULL,\n"
                            + "                      UNIQUE(account_id_type, key_id)\n"
                            + "                    ) STRICT;\n" + "");
        }
    }

    public PreKeyStore(final Database database, final ServiceIdType serviceIdType) {
        this.database = database;
        this.accountIdType = Utils.getAccountIdType(serviceIdType);
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        final var preKey = getPreKey(preKeyId);
        if (preKey == null) {
            throw new InvalidKeyIdException("No such signed pre key record: " + preKeyId);
        }
        return preKey;
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        final var sql = String
                .format("                INSERT INTO %s (account_id_type, key_id, public_key, private_key)\n"
                        + "                VALUES (?, ?, ?, ?)\n", TABLE_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, preKeyId);
                final var keyPair = record.getKeyPair();
                statement.setBytes(3, keyPair.getPublicKey().serialize());
                statement.setBytes(4, keyPair.getPrivateKey().serialize());
                statement.executeUpdate();
            } catch (InvalidKeyException ignored) {
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update pre_key store", e);
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return getPreKey(preKeyId) != null;
    }

    @Override
    public void removePreKey(int preKeyId) {
        final var sql = String.format("                DELETE FROM %s AS p\n"
                + "                WHERE p.account_id_type = ? AND p.key_id = ?\n", TABLE_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, preKeyId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update pre_key store", e);
        }
    }

    public void removeAllPreKeys() {
        final var sql = String.format(
                "                DELETE FROM %s AS p\n" + "                WHERE p.account_id_type = ?\n",
                TABLE_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update pre_key store", e);
        }
    }

    void addLegacyPreKeys(final Collection<PreKeyRecord> preKeys) {
        logger.debug("Migrating legacy preKeys to database");
        long start = System.nanoTime();
        final var sql = String
                .format("                INSERT INTO %s (account_id_type, key_id, public_key, private_key)\n"
                        + "                VALUES (?, ?, ?, ?)\n", TABLE_PRE_KEY);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var deleteSql = String.format("DELETE FROM %s AS p WHERE p.account_id_type = ?", TABLE_PRE_KEY);
            try (final var statement = connection.prepareStatement(deleteSql)) {
                statement.setInt(1, accountIdType);
                statement.executeUpdate();
            }
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var record : preKeys) {
                    statement.setInt(1, accountIdType);
                    statement.setInt(2, record.getId());
                    final var keyPair = record.getKeyPair();
                    statement.setBytes(3, keyPair.getPublicKey().serialize());
                    statement.setBytes(4, keyPair.getPrivateKey().serialize());
                    statement.executeUpdate();
                }
            } catch (InvalidKeyException ignored) {
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update preKey store", e);
        }
        logger.debug("Complete preKeys migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private PreKeyRecord getPreKey(int preKeyId) {
        final var sql = String.format("                SELECT p.key_id, p.public_key, p.private_key\n"
                + "                FROM %s p\n" + "                WHERE p.account_id_type = ? AND p.key_id = ?\n",
                TABLE_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, preKeyId);
                return Utils.executeQueryForOptional(statement, this::getPreKeyRecordFromResultSet).orElse(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from pre_key store", e);
        }
    }

    private PreKeyRecord getPreKeyRecordFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var keyId = resultSet.getInt("key_id");
            final var publicKey = Curve.decodePoint(resultSet.getBytes("public_key"), 0);
            final var privateKey = Curve.decodePrivatePoint(resultSet.getBytes("private_key"));
            return new PreKeyRecord(keyId, new ECKeyPair(publicKey, privateKey));
        } catch (InvalidKeyException e) {
            return null;
        }
    }
}
