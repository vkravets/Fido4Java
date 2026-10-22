package org.fidonet.jftn.rpc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.Response;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class ProfileNameTest {
    @TempDir
    Path root;

    @Test
    void authenticatedNamesAreIsolatedAndSurviveRestartWithoutChangingLogin() throws Exception {
        try (RpcAccounts accounts = new RpcAccounts(root.resolve("accounts"))) {
            accounts.createUser("first", "first-password");
            accounts.createUser("second", "second-password");
            AccountDirectory directory = new AccountDirectory(accounts);
            UiSessions sessions = new UiSessions(accounts, 3600);
            ProfileHandler handler = new ProfileHandler(directory, sessions, List.of(), false);
            String token = sessions.login(new Credential("first", "first-password"))
                                   .getSession_id();
            assertEquals("first", handler.me(token).getDisplayName());
            handler.saveDisplayName(token, "  Ігор Example  ");
            assertEquals("Ігор Example", handler.me(token).getDisplayName());
            assertEquals("first", handler.me(token).getLogin());
            assertEquals("second", directory.profile("second").displayName);
            assertEquals(Response.AUTHORIZATION_FAILED,
                         sessions.login(new Credential("Ігор Example", "first-password"))
                                 .getRespose_status());
            assertThrows(UiError.class, () -> handler.saveDisplayName("invalid", "Changed"));
            for (String invalid : List.of("Line\nBreak",
                                          "Nul\0Name",
                                          "Tab\tName",
                                          "x".repeat(81))) {
                UiError error = assertThrows(UiError.class,
                                             () -> handler.saveDisplayName(token, invalid));
                assertEquals("INVALID_ARGUMENT", error.getCode());
            }
            assertEquals("Ігор Example", handler.me(token).getDisplayName());
        }
        try (RpcAccounts accounts = new RpcAccounts(root.resolve("accounts"))) {
            AccountDirectory directory = new AccountDirectory(accounts);
            assertEquals("Ігор Example", directory.profile("first").displayName);
            assertEquals("second", directory.profile("second").displayName);
            directory.displayName("first", "  ");
            assertEquals("first", directory.profile("first").displayName);
        }
    }
}
