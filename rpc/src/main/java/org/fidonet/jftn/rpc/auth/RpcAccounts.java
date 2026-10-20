package org.fidonet.jftn.rpc.auth;

import org.fidonet.jftn.rpc.PasskeyInfo;
import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;

import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.ByteArray;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/** Account facade; setup and runtime share a managed repository rather than owning connections. */
public final class RpcAccounts implements AutoCloseable {
    private static final SecureRandom random = new SecureRandom();
    private final StorageSession storage;
    private final AccountRepository repository;
    private boolean closed;

    public RpcAccounts(Path path) throws IOException {
        this(StorageServices.file(path));
    }

    public RpcAccounts(StorageSession storage) throws IOException {
        this.storage = storage;
        try {
            repository = storage.require(AccountRepository.class);
        } catch (IOException | RuntimeException failure) {
            try {
                storage.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    AccountRepository repository() {
        if (closed) {
            throw new IllegalStateException("Account storage lease is closed");
        }
        return repository;
    }

    static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    public void bootstrap(String user, String password) throws IOException {
        repository().bootstrap(user, password);
    }

    public void createUser(String user, String password) throws IOException {
        repository().createUser(user, password);
    }

    public boolean hasUsers() throws IOException {
        return repository().hasUsers();
    }

    public boolean enabled(String user) throws IOException {
        return repository().enabled(user);
    }

    public boolean verifyPassword(String user, String password) throws IOException {
        return repository().verifyPassword(user, password);
    }

    public void changePassword(String user, String current, String replacement) throws IOException {
        repository().changePassword(user, current, replacement);
    }

    public Optional<ByteArray> handle(String user) {
        return repository().handle(user);
    }

    public Optional<String> username(ByteArray handle) {
        return repository().username(handle);
    }

    List<RegisteredCredential> credentials(String rp, String owner, String id) {
        return repository().credentials(rp, owner, id);
    }

    void register(String owner, String rp, String label, RegistrationResult credential)
            throws IOException {
        repository().register(owner, rp, label, credential);
    }

    boolean updateCounter(String rp,
                          String owner,
                          ByteArray id,
                          long previous,
                          long current,
                          boolean backedUp)
            throws IOException {
        return repository().updateCounter(rp, owner, id, previous, current, backedUp);
    }

    public List<PasskeyInfo> list(String owner, String rp) throws IOException {
        return repository().list(owner, rp);
    }

    public void remove(String owner, String rp, String id) throws IOException {
        repository().remove(owner, rp, id);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        storage.close();
    }
}
