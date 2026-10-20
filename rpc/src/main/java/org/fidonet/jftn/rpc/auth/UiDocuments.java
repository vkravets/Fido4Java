package org.fidonet.jftn.rpc.auth;

/** Personal document facade, retaining the existing bounded validation contract. */
final class UiDocuments {
    private final RpcAccounts accounts;

    UiDocuments(RpcAccounts accounts) {
        this.accounts = accounts;
    }

    String read(String owner, String key) throws Exception {
        return accounts.repository().profiles().uiDocument(owner, key);
    }

    void save(String owner, String key, String document) throws Exception {
        accounts.repository().profiles().uiDocument(owner, key, document);
    }
}
