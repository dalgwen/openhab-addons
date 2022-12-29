package org.asamk.signal.manager.storage.accounts;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountsStorage {

    private final List<Account> accounts;
    private final Integer version;

    public AccountsStorage(@JsonProperty("accounts") List<Account> accounts, @JsonProperty("version") Integer version) {
        super();
        this.accounts = accounts;
        this.version = version;
    }

    public static class Account {
        private final String path;
        private final String environment;
        private final String number;
        private final String uuid;

        public Account(@JsonProperty("path") String path, @JsonProperty("environment") String environment,
                @JsonProperty("number") String number, @JsonProperty("uuid") String uuid) {
            super();
            this.path = path;
            this.environment = environment;
            this.number = number;
            this.uuid = uuid;
        }

        public String path() {
            return path;
        }

        public String environment() {
            return environment;
        }

        public String number() {
            return number;
        }

        public String uuid() {
            return uuid;
        }

    }

    public List<Account> accounts() {
        return accounts;
    }

    public Integer version() {
        return version;
    }
}
