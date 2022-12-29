package org.asamk.signal.manager.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import org.asamk.signal.manager.storage.groups.GroupStore;
import org.asamk.signal.manager.storage.identities.IdentityKeyStore;
import org.asamk.signal.manager.storage.prekeys.PreKeyStore;
import org.asamk.signal.manager.storage.prekeys.SignedPreKeyStore;
import org.asamk.signal.manager.storage.recipients.RecipientStore;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogStore;
import org.asamk.signal.manager.storage.senderKeys.SenderKeyRecordStore;
import org.asamk.signal.manager.storage.senderKeys.SenderKeySharedStore;
import org.asamk.signal.manager.storage.sessions.SessionStore;
import org.asamk.signal.manager.storage.stickers.StickerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

public class AccountDatabase extends Database {

    private final static Logger logger = LoggerFactory.getLogger(AccountDatabase.class);
    private static final long DATABASE_VERSION = 11;

    private AccountDatabase(final HikariDataSource dataSource) {
        super(logger, DATABASE_VERSION, dataSource);
    }

    public static AccountDatabase init(File databaseFile) throws SQLException {
        return initDatabase(databaseFile, AccountDatabase::new);
    }

    @Override
    protected void createDatabase(final Connection connection) throws SQLException {
        RecipientStore.createSql(connection);
        MessageSendLogStore.createSql(connection);
        StickerStore.createSql(connection);
        PreKeyStore.createSql(connection);
        SignedPreKeyStore.createSql(connection);
        GroupStore.createSql(connection);
        SessionStore.createSql(connection);
        IdentityKeyStore.createSql(connection);
        SenderKeyRecordStore.createSql(connection);
        SenderKeySharedStore.createSql(connection);
    }

    @Override
    protected void upgradeDatabase(final Connection connection, final long oldVersion) throws SQLException {
        if (oldVersion < 2) {
            logger.debug("Updating database: Creating recipient table");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE recipient (\n"
                        + "                          _id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                        + "                          number TEXT UNIQUE,\n"
                        + "                          uuid BLOB UNIQUE,\n"
                        + "                          profile_key BLOB,\n"
                        + "                          profile_key_credential BLOB,\n" + "\n"
                        + "                          given_name TEXT,\n"
                        + "                          family_name TEXT,\n" + "                          color TEXT,\n"
                        + "\n" + "                          expiration_time INTEGER NOT NULL DEFAULT 0,\n"
                        + "                          blocked INTEGER NOT NULL DEFAULT FALSE,\n"
                        + "                          archived INTEGER NOT NULL DEFAULT FALSE,\n"
                        + "                          profile_sharing INTEGER NOT NULL DEFAULT FALSE,\n" + "\n"
                        + "                          profile_last_update_timestamp INTEGER NOT NULL DEFAULT 0,\n"
                        + "                          profile_given_name TEXT,\n"
                        + "                          profile_family_name TEXT,\n"
                        + "                          profile_about TEXT,\n"
                        + "                          profile_about_emoji TEXT,\n"
                        + "                          profile_avatar_url_path TEXT,\n"
                        + "                          profile_mobile_coin_address BLOB,\n"
                        + "                          profile_unidentified_access_mode TEXT,\n"
                        + "                          profile_capabilities TEXT\n"
                        + "                        ) STRICT;");
            }
        }
        if (oldVersion < 3) {
            logger.debug("Updating database: Creating sticker table");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE sticker (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          pack_id BLOB UNIQUE NOT NULL,\n"
                        + "                          pack_key BLOB NOT NULL,\n"
                        + "                          installed INTEGER NOT NULL DEFAULT FALSE\n"
                        + "                        ) STRICT;");
            }
        }
        if (oldVersion < 4) {
            logger.debug("Updating database: Creating pre key tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE signed_pre_key (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          account_id_type INTEGER NOT NULL,\n"
                        + "                          key_id INTEGER NOT NULL,\n"
                        + "                          public_key BLOB NOT NULL,\n"
                        + "                          private_key BLOB NOT NULL,\n"
                        + "                          signature BLOB NOT NULL,\n"
                        + "                          timestamp INTEGER DEFAULT 0,\n"
                        + "                          UNIQUE(account_id_type, key_id)\n"
                        + "                        ) STRICT;\n" + "                        CREATE TABLE pre_key (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          account_id_type INTEGER NOT NULL,\n"
                        + "                          key_id INTEGER NOT NULL,\n"
                        + "                          public_key BLOB NOT NULL,\n"
                        + "                          private_key BLOB NOT NULL,\n"
                        + "                          UNIQUE(account_id_type, key_id)\n"
                        + "                        ) STRICT;\n");
            }
        }
        if (oldVersion < 5) {
            logger.debug("Updating database: Creating group tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE group_v2 (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          group_id BLOB UNIQUE NOT NULL,\n"
                        + "                          master_key BLOB NOT NULL,\n"
                        + "                          group_data BLOB,\n"
                        + "                          distribution_id BLOB UNIQUE NOT NULL,\n"
                        + "                          blocked INTEGER NOT NULL DEFAULT FALSE,\n"
                        + "                          permission_denied INTEGER NOT NULL DEFAULT FALSE\n"
                        + "                        ) STRICT;\n" + "                        CREATE TABLE group_v1 (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          group_id BLOB UNIQUE NOT NULL,\n"
                        + "                          group_id_v2 BLOB UNIQUE,\n"
                        + "                          name TEXT,\n" + "                          color TEXT,\n"
                        + "                          expiration_time INTEGER NOT NULL DEFAULT 0,\n"
                        + "                          blocked INTEGER NOT NULL DEFAULT FALSE,\n"
                        + "                          archived INTEGER NOT NULL DEFAULT FALSE\n"
                        + "                        ) STRICT;\n"
                        + "                        CREATE TABLE group_v1_member (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          group_id INTEGER NOT NULL REFERENCES group_v1 (_id) ON DELETE CASCADE,\n"
                        + "                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n"
                        + "                          UNIQUE(group_id, recipient_id)\n"
                        + "                        ) STRICT;\n");
            }
        }
        if (oldVersion < 6) {
            logger.debug("Updating database: Creating session tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE session (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          account_id_type INTEGER NOT NULL,\n"
                        + "                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n"
                        + "                          device_id INTEGER NOT NULL,\n"
                        + "                          record BLOB NOT NULL,\n"
                        + "                          UNIQUE(account_id_type, recipient_id, device_id)\n"
                        + "                        ) STRICT;\n");
            }
        }
        if (oldVersion < 7) {
            logger.debug("Updating database: Creating identity table");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE identity (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          recipient_id INTEGER UNIQUE NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n"
                        + "                          identity_key BLOB NOT NULL,\n"
                        + "                          added_timestamp INTEGER NOT NULL,\n"
                        + "                          trust_level INTEGER NOT NULL\n"
                        + "                        ) STRICT;\n");
            }
        }
        if (oldVersion < 8) {
            logger.debug("Updating database: Creating sender key tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                        CREATE TABLE sender_key (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n"
                        + "                          device_id INTEGER NOT NULL,\n"
                        + "                          distribution_id BLOB NOT NULL,\n"
                        + "                          record BLOB NOT NULL,\n"
                        + "                          created_timestamp INTEGER NOT NULL,\n"
                        + "                          UNIQUE(recipient_id, device_id, distribution_id)\n"
                        + "                        ) STRICT;\n"
                        + "                        CREATE TABLE sender_key_shared (\n"
                        + "                          _id INTEGER PRIMARY KEY,\n"
                        + "                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,\n"
                        + "                          device_id INTEGER NOT NULL,\n"
                        + "                          distribution_id BLOB NOT NULL,\n"
                        + "                          timestamp INTEGER NOT NULL,\n"
                        + "                          UNIQUE(recipient_id, device_id, distribution_id)\n"
                        + "                        ) STRICT;\n" + "");
            }
        }
        if (oldVersion < 9) {
            logger.debug("Updating database: Adding urgent field");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE message_send_log_content ADD COLUMN urgent INTEGER NOT NULL DEFAULT TRUE;\n");
            }
        }
        if (oldVersion < 10) {
            logger.debug("Updating database: Key tables on serviceId instead of recipientId");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("                                        CREATE TABLE identity2 (\n"
                        + "                                          _id INTEGER PRIMARY KEY,\n"
                        + "                                          uuid BLOB UNIQUE NOT NULL,\n"
                        + "                                          identity_key BLOB NOT NULL,\n"
                        + "                                          added_timestamp INTEGER NOT NULL,\n"
                        + "                                          trust_level INTEGER NOT NULL\n"
                        + "                                        ) STRICT;\n"
                        + "                                        INSERT INTO identity2 (_id, uuid, identity_key, added_timestamp, trust_level)\n"
                        + "                                          SELECT i._id, r.uuid, i.identity_key, i.added_timestamp, i.trust_level\n"
                        + "                                          FROM identity i LEFT JOIN recipient r ON i.recipient_id = r._id\n"
                        + "                                          WHERE uuid IS NOT NULL;\n"
                        + "                                        DROP TABLE identity;\n"
                        + "                                        ALTER TABLE identity2 RENAME TO identity;\n" + "\n"
                        + "                                        DROP INDEX msl_recipient_index;\n"
                        + "                                        ALTER TABLE message_send_log ADD COLUMN uuid BLOB;\n"
                        + "                                        UPDATE message_send_log\n"
                        + "                                          SET uuid = r.uuid\n"
                        + "                                          FROM message_send_log i, (SELECT _id, uuid FROM recipient) AS r\n"
                        + "                                          WHERE i.recipient_id = r._id;\n"
                        + "                                        DELETE FROM message_send_log WHERE uuid IS NULL;\n"
                        + "                                        ALTER TABLE message_send_log DROP COLUMN recipient_id;\n"
                        + "                                        CREATE INDEX msl_recipient_index ON message_send_log (uuid, device_id, content_id);\n"
                        + "\n" + "                                        CREATE TABLE sender_key2 (\n"
                        + "                                          _id INTEGER PRIMARY KEY,\n"
                        + "                                          uuid BLOB NOT NULL,\n"
                        + "                                          device_id INTEGER NOT NULL,\n"
                        + "                                          distribution_id BLOB NOT NULL,\n"
                        + "                                          record BLOB NOT NULL,\n"
                        + "                                          created_timestamp INTEGER NOT NULL,\n"
                        + "                                          UNIQUE(uuid, device_id, distribution_id)\n"
                        + "                                        ) STRICT;\n"
                        + "                                        INSERT INTO sender_key2 (_id, uuid, device_id, distribution_id, record, created_timestamp)\n"
                        + "                                          SELECT s._id, r.uuid, s.device_id, s.distribution_id, s.record, s.created_timestamp\n"
                        + "                                          FROM sender_key s LEFT JOIN recipient r ON s.recipient_id = r._id\n"
                        + "                                          WHERE uuid IS NOT NULL;\n"
                        + "                                        DROP TABLE sender_key;\n"
                        + "                                        ALTER TABLE sender_key2 RENAME TO sender_key;\n"
                        + "\n" + "                                        CREATE TABLE sender_key_shared2 (\n"
                        + "                                          _id INTEGER PRIMARY KEY,\n"
                        + "                                          uuid BLOB NOT NULL,\n"
                        + "                                          device_id INTEGER NOT NULL,\n"
                        + "                                          distribution_id BLOB NOT NULL,\n"
                        + "                                          timestamp INTEGER NOT NULL,\n"
                        + "                                          UNIQUE(uuid, device_id, distribution_id)\n"
                        + "                                        ) STRICT;\n"
                        + "                                        INSERT INTO sender_key_shared2 (_id, uuid, device_id, distribution_id, timestamp)\n"
                        + "                                          SELECT s._id, r.uuid, s.device_id, s.distribution_id, s.timestamp\n"
                        + "                                          FROM sender_key_shared s LEFT JOIN recipient r ON s.recipient_id = r._id\n"
                        + "                                          WHERE uuid IS NOT NULL;\n"
                        + "                                        DROP TABLE sender_key_shared;\n"
                        + "                                        ALTER TABLE sender_key_shared2 RENAME TO sender_key_shared;\n"
                        + "\n" + "                                        CREATE TABLE session2 (\n"
                        + "                                          _id INTEGER PRIMARY KEY,\n"
                        + "                                          account_id_type INTEGER NOT NULL,\n"
                        + "                                          uuid BLOB NOT NULL,\n"
                        + "                                          device_id INTEGER NOT NULL,\n"
                        + "                                          record BLOB NOT NULL,\n"
                        + "                                          UNIQUE(account_id_type, uuid, device_id)\n"
                        + "                                        ) STRICT;\n"
                        + "                                        INSERT INTO session2 (_id, account_id_type, uuid, device_id, record)\n"
                        + "                                          SELECT s._id, s.account_id_type, r.uuid, s.device_id, s.record\n"
                        + "                                          FROM session s LEFT JOIN recipient r ON s.recipient_id = r._id\n"
                        + "                                          WHERE uuid IS NOT NULL;\n"
                        + "                                        DROP TABLE session;\n"
                        + "                                        ALTER TABLE session2 RENAME TO session;\n" + "");
            }
        }
        if (oldVersion < 11) {
            logger.debug("Updating database: Adding pni field");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE recipient ADD COLUMN pni BLOB;");
            }
        }
    }
}
