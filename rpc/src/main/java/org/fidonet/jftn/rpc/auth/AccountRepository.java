package org.fidonet.jftn.rpc.auth;

import org.fidonet.jftn.rpc.PasskeyInfo;

import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.ByteArray;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Account operations shared by all transports, without SQL or connection access. */
public interface AccountRepository extends AutoCloseable {
    void bootstrap(String user, String password) throws IOException;

    void createUser(String user, String password) throws IOException;

    boolean hasUsers() throws IOException;

    boolean enabled(String user) throws IOException;

    boolean verifyPassword(String user, String password) throws IOException;

    void changePassword(String user, String current, String replacement) throws IOException;

    Optional<ByteArray> handle(String user);

    Optional<String> username(ByteArray handle);

    List<RegisteredCredential> credentials(String rp, String owner, String credentialId);

    void register(String owner, String rp, String label, RegistrationResult credential)
            throws IOException;

    boolean updateCounter(String rp,
                          String owner,
                          ByteArray id,
                          long previous,
                          long current,
                          boolean backedUp)
            throws IOException;

    List<PasskeyInfo> list(String owner, String rp) throws IOException;

    void remove(String owner, String rp, String id) throws IOException;

    AccountProfiles profiles();

    @Override
    void close() throws IOException;
}
