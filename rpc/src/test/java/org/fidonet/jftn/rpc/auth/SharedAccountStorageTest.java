package org.fidonet.jftn.rpc.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class SharedAccountStorageTest {
    @TempDir
    Path root;

    @Test
    void closingSetupClientPreservesRuntimeAccountAccessAndStoredPassword() throws Exception {
        Path location = root.resolve("accounts");
        RpcAccounts setup = new RpcAccounts(location);
        try (RpcAccounts runtime = new RpcAccounts(location)) {
            setup.bootstrap("admin", "correct-test-password");
            setup.close();
            assertTrue(runtime.verifyPassword("admin", "correct-test-password"));
            runtime.changePassword("admin", "correct-test-password", "replacement-test-password");
        } finally {
            setup.close();
        }
        try (RpcAccounts reopened = new RpcAccounts(location)) {
            assertTrue(reopened.verifyPassword("admin", "replacement-test-password"));
        }
    }
}
