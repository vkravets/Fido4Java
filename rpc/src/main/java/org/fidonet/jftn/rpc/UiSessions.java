package org.fidonet.jftn.rpc;

import org.fidonet.jftn.rpc.auth.RpcAccounts;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded bearer sessions shared by password and passkey authentication. */
public final class UiSessions implements LoginService.Iface {
    private static final SecureRandom random = new SecureRandom();
    private final RpcAccounts accounts;
    private final long lifetime;
    private final LongSupplier clock;
    private final Map<String, Session> sessions = new HashMap<>();

    private static final class Session {
        private final String owner;
        private final long expires;

        private Session(String owner, long expires) {
            this.owner = owner;
            this.expires = expires;
        }
    }

    public UiSessions(RpcAccounts accounts, long seconds) {
        this(accounts, seconds, System::currentTimeMillis);
    }

    public UiSessions(RpcAccounts accounts, long seconds, LongSupplier clock) {
        if (seconds < 60 || seconds > 86400) {
            throw new IllegalArgumentException("RPC session lifetime must be 60..86400 seconds");
        }
        this.accounts = accounts;
        lifetime = seconds * 1000;
        this.clock = clock;
    }

    @Override
    public synchronized LoginResponse login(Credential credentials) {
        try {
            if (credentials == null || !accounts.verifyPassword(credentials.getLogin(),
                                                                credentials.getPassword())) {
                return new LoginResponse(Response.AUTHORIZATION_FAILED, "");
            }
            return issueForUser(credentials.getLogin());
        } catch (IOException e) {
            return new LoginResponse(Response.AUTHORIZATION_FAILED, "");
        }
    }

    /** Internal issuance endpoint; callers must first verify a password or WebAuthn assertion. */
    public synchronized LoginResponse issueForUser(String user) throws IOException {
        expire();
        if (!accounts.enabled(user) || sessions.size() >= 64) {
            return new LoginResponse(Response.AUTHORIZATION_FAILED, "");
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        sessions.put(token, new Session(user, clock.getAsLong() + lifetime));
        return new LoginResponse(Response.OK, token);
    }

    private void expire() {
        long now = clock.getAsLong();
        sessions.values().removeIf(session -> session.expires <= now);
    }

    public synchronized String require(String token) throws UiError {
        expire();
        Session session = sessions.get(token);
        try {
            if (session == null || !accounts.enabled(session.owner)) {
                sessions.remove(token);
                throw new UiError("UNAUTHORIZED", "Session is missing, disabled or expired");
            }
        } catch (IOException e) {
            throw new UiError("UNAUTHORIZED", "Cannot validate the session");
        }
        return session.owner;
    }

    public synchronized void invalidate(String owner) {
        sessions.values().removeIf(session -> session.owner.equals(owner));
    }

    @Override
    public synchronized void logout(String token) {
        sessions.remove(token);
    }

    public synchronized void clear() {
        sessions.clear();
    }
}
