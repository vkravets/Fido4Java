package org.fidonet.jftn.rpc.auth;

import java.util.List;

/** Registration, approved identities and preferences; implementations preserve authorization rules. */
public interface AccountProfiles {
    AccountRecord profile(String login) throws Exception;

    List<AccountRecord> users() throws Exception;

    void displayName(String owner, String displayName) throws Exception;

    void register(String login, String password, String requestedAka) throws Exception;

    List<AddressClaim> claims(String owner) throws Exception;

    void requestAka(String owner, String value) throws Exception;

    void decide(String owner, String value, boolean approve) throws Exception;

    void preferences(String owner, String theme, String defaultAka, List<String> stationAddresses)
            throws Exception;

    List<String> allowed(String owner, List<String> stationAddresses) throws Exception;

    List<String> hostedAddresses() throws Exception;

    String uiDocument(String owner, String key) throws Exception;

    void uiDocument(String owner, String key, String document) throws Exception;
}
