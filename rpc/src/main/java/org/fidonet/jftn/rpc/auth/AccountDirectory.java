package org.fidonet.jftn.rpc.auth;

import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.util.List;

/** Domain operations delegate to the active storage adapter without exposing JDBC. */
public final class AccountDirectory {
    private final RpcAccounts accounts;

    public AccountDirectory(RpcAccounts accounts) {
        this.accounts = accounts;
    }

    @FunctionalInterface
    private interface Read<T> {
        T get() throws Exception;
    }

    private static <T> T read(Read<T> operation) throws IOException {
        try {
            return operation.get();
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Unable to access account profiles", failure);
        }
    }

    public AccountRecord profile(String login) throws IOException {
        return read(() -> accounts.repository().profiles().profile(login));
    }

    public List<AccountRecord> users() throws IOException {
        return read(() -> accounts.repository().profiles().users());
    }

    public void displayName(String owner, String value) throws Exception {
        if (value == null || value.length() > 80
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Display name must contain at most 80 printable characters");
        }
        accounts.repository().profiles().displayName(owner, value.strip());
    }

    public List<AddressClaim> claims(String owner) throws IOException {
        return read(() -> accounts.repository().profiles().claims(owner));
    }

    public List<String> allowed(String owner, List<String> stationAddresses) throws IOException {
        return read(() -> accounts.repository().profiles().allowed(owner, stationAddresses));
    }

    public List<String> hostedAddresses() throws IOException {
        return read(() -> accounts.repository().profiles().hostedAddresses());
    }

    public void register(String login, String password, String requestedAka) throws Exception {
        accounts.repository().profiles().register(login, password, requestedAka);
    }

    public void requestAka(String owner, String value) throws Exception {
        accounts.repository().profiles().requestAka(owner, value);
    }

    public void decide(String owner, String value, boolean approve) throws Exception {
        accounts.repository().profiles().decide(owner, value, approve);
    }

    public void preferences(String owner,
                            String theme,
                            String defaultAka,
                            List<String> stationAddresses)
            throws Exception {
        accounts.repository().profiles().preferences(owner, theme, defaultAka, stationAddresses);
    }

    String uiDocument(String owner, String key) throws Exception {
        return accounts.repository().profiles().uiDocument(owner, key);
    }

    void uiDocument(String owner, String key, String document) throws Exception {
        accounts.repository().profiles().uiDocument(owner, key, document);
    }

    public static String address(String value, boolean pointOnly) {
        if (value == null || !value.matches("[0-9]+:[0-9]+/[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Expected a numeric FTN address");
        }
        FTNAddr parsed = new FTNAddr(value);
        for (int part : new int[]{parsed.getZone(), parsed.getNet(), parsed.getNode(),
                parsed.getPnt()}) {
            if (part < 0 || part > 65535) {
                throw new IllegalArgumentException("FTN address component is outside 0..65535");
            }
        }
        if (parsed.getZone() == 0 || pointOnly && parsed.getPnt() == 0) {
            throw new IllegalArgumentException("Registration requires a point address with an explicit zone");
        }
        return parsed.toString();
    }
}
