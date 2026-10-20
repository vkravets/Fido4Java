package org.fidonet.jftn.rpc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class UiDocumentsTest {
    @TempDir
    Path root;

    @Test
    void documentsAreOwnedByTheSessionAndSurviveRestart() throws Exception {
        Path path = root.resolve("accounts");
        try (RpcAccounts accounts = new RpcAccounts(path)) {
            accounts.createUser("first", "password-first");
            accounts.createUser("second", "password-second");
            UiSessions sessions = new UiSessions(accounts, 3600);
            String first = sessions.login(new Credential("first", "password-first"))
                                   .getSession_id();
            String second = sessions.login(new Credential("second", "password-second"))
                                    .getSession_id();
            ProfileHandler handler = new ProfileHandler(new AccountDirectory(accounts),
                                                        sessions,
                                                        List.of(),
                                                        false);
            handler.saveUiDocument(first,
                                   "workspace",
                                   "{\"version\":1,\"netmail\":{\"preview\":\"below\"}}");
            assertEquals("{}", handler.uiDocument(second, "workspace"));
            assertThrows(UiError.class, () -> handler.uiDocument("invalid", "workspace"));
            assertThrows(UiError.class, () -> handler.saveUiDocument(first, "station", "{}"));
            for (String invalid : List.of("[]",
                                          "{} {}",
                                          "{\"a\":1,\"a\":2}",
                                          "{",
                                          "{\"body\":\"" + "x".repeat(270000) + "\"}")) {
                assertThrows(UiError.class,
                             () -> handler.saveUiDocument(first, "workspace", invalid));
            }
        }
        try (RpcAccounts accounts = new RpcAccounts(path)) {
            assertEquals("{\"version\":1,\"netmail\":{\"preview\":\"below\"}}",
                         new UiDocuments(accounts).read("first", "workspace"));
        }
    }
}
