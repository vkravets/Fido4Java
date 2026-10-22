package org.fidonet.jftn.rpc.auth;

import org.fidonet.jftn.rpc.AuthMethods;
import org.fidonet.jftn.rpc.AuthService;
import org.fidonet.jftn.rpc.LoginResponse;
import org.fidonet.jftn.rpc.PasskeyInfo;
import org.fidonet.jftn.rpc.PasskeyOptions;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.COSEAlgorithmIdentifier;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** WebAuthn ceremonies remain server-owned, short-lived, single-use and session-bound. */
public final class AccountAuthentication implements AuthService.Iface, AutoCloseable {
    private final RpcAccounts accounts;
    private final UiSessions sessions;
    private final RelyingParty relyingParty;
    private final String rpId;
    private final LongSupplier clock;
    private final Map<String, Pending> pending = new HashMap<>();

    private static final class Pending {
        private final Object options;
        private final String session;
        private final String label;
        private final long expires;

        private Pending(Object options, String session, String label, long expires) {
            this.options = options;
            this.session = session;
            this.label = label;
            this.expires = expires;
        }
    }

    /** Empty RP ID disables passkeys while retaining database password authentication. */
    public AccountAuthentication(RpcAccounts accounts,
                                 UiSessions sessions,
                                 String rpId,
                                 Set<String> origins) {
        this(accounts, sessions, rpId, origins, System::currentTimeMillis);
    }

    public AccountAuthentication(RpcAccounts accounts,
                                 UiSessions sessions,
                                 String rpId,
                                 Set<String> origins,
                                 LongSupplier clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.rpId = rpId;
        this.clock = clock;
        if (rpId.isEmpty()) {
            relyingParty = null;
            return;
        }
        validateOrigins(rpId, origins);
        relyingParty = RelyingParty.builder()
                                   .identity(RelyingPartyIdentity.builder()
                                                                 .id(rpId)
                                                                 .name("JFtn node")
                                                                 .build())
                                   .credentialRepository(new PasskeyRepository(accounts, rpId))
                                   .origins(Set.copyOf(origins))
                                   .allowOriginPort(false)
                                   .allowOriginSubdomain(false)
                                   .attestationConveyancePreference(AttestationConveyancePreference.NONE)
                                   .preferredPubkeyParams(List.of(PublicKeyCredentialParameters.builder()
                                                                                               .alg(COSEAlgorithmIdentifier.ES256)
                                                                                               .build(),
                                                                  PublicKeyCredentialParameters.builder()
                                                                                               .alg(COSEAlgorithmIdentifier.RS256)
                                                                                               .build()))
                                   .validateSignatureCounter(true)
                                   .build();
    }

    private static void validateOrigins(String rpId, Set<String> origins) {
        if (!rpId.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?") || origins.isEmpty()) {
            throw new IllegalArgumentException("Passkeys require an RP hostname and explicit origins");
        }
        for (String origin : origins) {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null || !(host.equals(rpId) || host.endsWith("." + rpId))
                    || !(uri.getScheme().equals("https")
                            || uri.getScheme().equals("http") && host.equals("localhost"))
                    || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("Passkey origin must be an HTTPS origin under the RP ID, or http://localhost");
            }
        }
    }

    private void requirePasskeys() throws UiError {
        if (relyingParty == null) {
            throw new UiError("UNSUPPORTED", "Passkeys are not configured");
        }
    }

    @Override
    public AuthMethods methods() {
        return new AuthMethods(true, relyingParty != null);
    }

    private String add(Object options, String session, String label) throws UiError {
        pending.values().removeIf(item -> item.expires <= clock.getAsLong());
        if (pending.size() >= 128) {
            throw new UiError("BUSY", "Too many authentication requests");
        }
        String id = Base64.getUrlEncoder()
                          .withoutPadding()
                          .encodeToString(RpcAccounts.randomBytes(32));
        pending.put(id, new Pending(options, session, label, clock.getAsLong() + 120000));
        return id;
    }

    private Pending consume(String id, String session, Class<?> type, String json) throws UiError {
        Pending request = pending.remove(id);
        if (request == null || request.expires <= clock.getAsLong()
                || !type.isInstance(request.options) || !request.session.equals(session)
                || json == null || json.length() > 131072) {
            throw new UiError("AUTHORIZATION_FAILED", "Invalid or expired authentication request");
        }
        return request;
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run() throws Exception;
    }

    private <T> T verify(Operation<T> operation) throws UiError {
        try {
            return operation.run();
        } catch (UiError e) {
            throw e;
        } catch (Exception e) {
            // Never return raw credential JSON, passwords or library diagnostics to the client.
            throw new UiError("AUTHORIZATION_FAILED", "Authentication request was rejected");
        }
    }

    @Override
    public synchronized PasskeyOptions beginPasskeyLogin() throws UiError {
        requirePasskeys();
        return verify(() -> {
            AssertionRequest options = relyingParty.startAssertion(StartAssertionOptions.builder()
                                                                                        .userVerification(UserVerificationRequirement.REQUIRED)
                                                                                        .timeout(120000)
                                                                                        .build());
            return new PasskeyOptions(add(options, "", ""), options.toCredentialsGetJson());
        });
    }

    @Override
    public synchronized LoginResponse finishPasskeyLogin(String requestId, String responseJson)
            throws UiError {
        requirePasskeys();
        Pending request = consume(requestId, "", AssertionRequest.class, responseJson);
        return verify(() -> {
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                                                                                        .request((AssertionRequest) request.options)
                                                                                        .response(PublicKeyCredential.parseAssertionResponseJson(responseJson))
                                                                                        .build());
            if (!result.isSuccess() || !result.isUserVerified() || !result.isSignatureCounterValid()
                    || !accounts.updateCounter(rpId,
                                               result.getUsername(),
                                               result.getCredentialId(),
                                               result.getCredential().getSignatureCount(),
                                               result.getSignatureCount(),
                                               result.isBackedUp())) {
                throw new UiError("AUTHORIZATION_FAILED", "Passkey assertion was rejected");
            }
            return sessions.issueForUser(result.getUsername());
        });
    }

    private void password(String user, String password) throws UiError {
        if (!verify(() -> accounts.verifyPassword(user, password))) {
            throw new UiError("AUTHORIZATION_FAILED", "Password confirmation failed");
        }
    }

    @Override
    public synchronized PasskeyOptions beginPasskeyRegistration(String session,
                                                                String password,
                                                                String label)
            throws UiError {
        requirePasskeys();
        String owner = sessions.require(session);
        password(owner, password);
        if (label == null || label.isBlank() || label.length() > 100
                || label.matches("(?s).*[\\x00-\\x1f].*")) {
            throw new UiError("INVALID_ARGUMENT",
                              "Passkey label must contain 1..100 printable characters");
        }
        if (verify(() -> accounts.list(owner, rpId).size()) >= 10) {
            throw new UiError("INVALID_ARGUMENT", "At most 10 passkeys per account are supported");
        }
        return verify(() -> {
            PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                                                                                                                .user(UserIdentity.builder()
                                                                                                                                  .name(owner)
                                                                                                                                  .displayName(accounts.repository()
                                                                                                                                                       .profiles()
                                                                                                                                                       .profile(owner).displayName)
                                                                                                                                  .id(accounts.handle(owner)
                                                                                                                                              .orElseThrow())
                                                                                                                                  .build())
                                                                                                                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                                                                                                                                                      .residentKey(ResidentKeyRequirement.REQUIRED)
                                                                                                                                                                      .userVerification(UserVerificationRequirement.REQUIRED)
                                                                                                                                                                      .build())
                                                                                                                .timeout(120000)
                                                                                                                .build());
            return new PasskeyOptions(add(options, session, label),
                                      options.toCredentialsCreateJson());
        });
    }

    @Override
    public synchronized void finishPasskeyRegistration(String session,
                                                       String requestId,
                                                       String responseJson)
            throws UiError {
        requirePasskeys();
        String owner = sessions.require(session);
        Pending request = consume(requestId,
                                  session,
                                  PublicKeyCredentialCreationOptions.class,
                                  responseJson);
        verify(() -> {
            RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                                                                                                 .request((PublicKeyCredentialCreationOptions) request.options)
                                                                                                 .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                                                                                                 .build());
            if (!result.isUserVerified()) {
                throw new UiError("AUTHORIZATION_FAILED", "Passkey user verification is required");
            }
            accounts.register(owner, rpId, request.label, result);
            return null;
        });
    }

    @Override
    public synchronized List<PasskeyInfo> passkeys(String session) throws UiError {
        requirePasskeys();
        String owner = sessions.require(session);
        return verify(() -> accounts.list(owner, rpId));
    }

    @Override
    public synchronized void removePasskey(String session, String password, String credentialId)
            throws UiError {
        requirePasskeys();
        String owner = sessions.require(session);
        password(owner, password);
        verify(() -> {
            accounts.remove(owner, rpId, credentialId);
            return null;
        });
    }

    @Override
    public synchronized void changePassword(String session,
                                            String currentPassword,
                                            String newPassword)
            throws UiError {
        String owner = sessions.require(session);
        password(owner, currentPassword);
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 1024) {
            throw new UiError("INVALID_ARGUMENT", "New password must contain 12..1024 characters");
        }
        verify(() -> {
            synchronized (sessions) {
                accounts.changePassword(owner, currentPassword, newPassword);
                sessions.invalidate(owner);
            }
            pending.values()
                   .removeIf(request -> request.options instanceof PublicKeyCredentialCreationOptions
                           && ((PublicKeyCredentialCreationOptions) request.options).getUser()
                                                                                    .getName()
                                                                                    .equals(owner));
            return null;
        });
    }

    @Override
    public synchronized void close() {
        pending.clear();
    }
}
