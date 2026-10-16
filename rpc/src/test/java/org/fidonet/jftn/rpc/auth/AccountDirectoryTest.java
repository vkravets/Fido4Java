package org.fidonet.jftn.rpc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fidonet.jftn.rpc.Credential;
import org.fidonet.jftn.rpc.Response;
import org.fidonet.jftn.rpc.UiSessions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class AccountDirectoryTest {
    @TempDir
    Path root;
    private RpcAccounts accounts;
    private AccountDirectory directory;

    @BeforeEach
    void open() throws Exception {
        accounts = new RpcAccounts(root.resolve("accounts"));
        accounts.bootstrap("sysop", "admin-password");
        directory = new AccountDirectory(accounts);
    }

    @AfterEach
    void close() throws Exception {
        accounts.close();
    }

    @Test
    void registrationStaysDisabledUntilAnAddressIsApproved() throws Exception {
        directory.register("point", "point-password", "1:500/100.1");
        UiSessions sessions = new UiSessions(accounts, 3600);
        assertEquals(Response.AUTHORIZATION_FAILED,
                     sessions.login(new Credential("point", "point-password")).getRespose_status());
        assertEquals("POINT", directory.profile("point").role);
        assertEquals("PENDING", directory.claims("point").get(0).state);
        directory.decide("point", "1:500/100.1", true);
        assertEquals(Response.OK,
                     sessions.login(new Credential("point", "point-password")).getRespose_status());
        assertEquals("1:500/100.1", directory.profile("point").defaultAka);
        assertEquals(List.of("1:500/100.1"), directory.allowed("point", List.of("1:500/100")));
    }

    @Test
    void failedRegistrationAndApprovalDoNotPartiallyUpdateTheDatabase() throws Exception {
        assertThrows(IllegalArgumentException.class,
                     () -> directory.register("invalid", "point-password", "1:500/100"));
        assertEquals(1, directory.users().size());
        directory.register("first", "point-password", "1:500/100.1");
        directory.register("second", "point-password", "1:500/100.1");
        directory.decide("first", "1:500/100.1", true);
        assertThrows(IllegalArgumentException.class,
                     () -> directory.decide("second", "1:500/100.1", true));
        assertFalse(directory.profile("second").enabled);
        assertEquals("PENDING", directory.claims("second").get(0).state);
        assertThrows(IllegalArgumentException.class,
                     () -> directory.decide("second", "1:500/100.9", true));
        directory.requestAka("second", "1:500/100.9");
        directory.decide("second", "1:500/100.9", true);
        assertTrue(directory.profile("second").enabled);
    }

    @Test
    void preferencesOnlySelectApprovedIdentitiesAndPersistAcrossRestart() throws Exception {
        directory.register("point", "point-password", "1:500/100.1");
        directory.decide("point", "1:500/100.1", true);
        directory.requestAka("point", "1:500/100.2");
        assertThrows(IllegalArgumentException.class,
                     () -> directory.preferences("point", "dark", "1:500/100.2", List.of()));
        directory.preferences("point", "dark", "1:500/100.1", List.of());
        accounts.close();
        accounts = new RpcAccounts(root.resolve("accounts"));
        directory = new AccountDirectory(accounts);
        assertEquals("dark", directory.profile("point").theme);
        assertEquals("1:500/100.1", directory.profile("point").defaultAka);
        assertEquals("ADMIN", directory.profile("sysop").role);
    }

    @Test
    void rejectedRequestsCannotActivateAnAccount() throws Exception {
        directory.register("point", "point-password", "1:500/100.1");
        directory.decide("point", "1:500/100.1", false);
        assertFalse(accounts.enabled("point"));
        assertEquals("REJECTED", directory.claims("point").get(0).state);
    }
}
