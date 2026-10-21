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
    void mailTemplatesBelongToEachAuthenticatedPointAndSurviveRestart() throws Exception {
        Path path = root.resolve("personal-templates");
        String firstDocument = templateDocument("First point");
        String secondDocument = templateDocument("Second point");
        try (RpcAccounts accounts = new RpcAccounts(path)) {
            accounts.createUser("admin", "password-admin");
            AccountDirectory directory = new AccountDirectory(accounts);
            directory.register("first", "password-first", "1:500/100.1");
            directory.register("second", "password-second", "1:500/100.2");
            directory.decide("first", "1:500/100.1", true);
            directory.decide("second", "1:500/100.2", true);
            UiSessions sessions = new UiSessions(accounts, 3600);
            ProfileHandler handler = new ProfileHandler(directory, sessions, List.of(), false);
            String first = login(sessions, "first", "password-first");
            String second = login(sessions, "second", "password-second");
            String admin = login(sessions, "admin", "password-admin");
            assertEquals("POINT", handler.me(first).getRole());
            handler.saveUiDocument(first, "mail-templates", firstDocument);
            assertEquals("{}", handler.uiDocument(second, "mail-templates"));
            assertEquals("{}", handler.uiDocument(admin, "mail-templates"));
            handler.saveUiDocument(second, "mail-templates", secondDocument);
            assertEquals(firstDocument, handler.uiDocument(first, "mail-templates"));
            assertEquals(secondDocument, handler.uiDocument(second, "mail-templates"));
            assertThrows(UiError.class,
                         () -> handler.saveUiDocument("invalid", "mail-templates", "{}"));
        }
        try (RpcAccounts accounts = new RpcAccounts(path)) {
            UiDocuments documents = new UiDocuments(accounts);
            assertEquals(firstDocument, documents.read("first", "mail-templates"));
            assertEquals(secondDocument, documents.read("second", "mail-templates"));
        }
    }

    private static String login(UiSessions sessions, String user, String password)
            throws Exception {
        return sessions.login(new Credential(user, password)).getSession_id();
    }

    private static String templateDocument(String name) {
        return "{\"version\":1,\"custom\":[{\"id\":\"personal-reply\",\"name\":\""
               + name
               + "\",\"kind\":\"reply\",\"body\":\"Hello @oname.\\n@position\\n@cname\"}],"
               + "\"rules\":[{\"kind\":\"reply\",\"aka\":\"\",\"area\":\"\","
               + "\"template\":\"personal-reply\"}]}";
    }

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
            handler.saveUiDocument(first, "keyboard", "{\"profile\":\"golded\"}");
            assertEquals("{}", handler.uiDocument(second, "keyboard"));
            assertEquals("{\"profile\":\"golded\"}", handler.uiDocument(first, "keyboard"));
            assertThrows(UiError.class, () -> handler.saveUiDocument("invalid", "keyboard", "{}"));
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
            assertEquals("{\"profile\":\"golded\"}",
                         new UiDocuments(accounts).read("first", "keyboard"));
        }
    }
}
