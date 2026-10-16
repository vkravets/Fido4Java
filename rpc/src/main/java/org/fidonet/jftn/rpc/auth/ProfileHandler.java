package org.fidonet.jftn.rpc.auth;

import org.fidonet.jftn.rpc.AkaClaim;
import org.fidonet.jftn.rpc.ProfileService;
import org.fidonet.jftn.rpc.UiError;
import org.fidonet.jftn.rpc.UiSessions;
import org.fidonet.jftn.rpc.UserProfile;

import java.util.ArrayList;
import java.util.List;

/** Personal settings and moderated signup, separate from node-wide mail operations. */
public final class ProfileHandler implements ProfileService.Iface {
    private final AccountDirectory directory;
    private final UiSessions sessions;
    private final List<String> stationAddresses;
    private final boolean registration;

    public ProfileHandler(AccountDirectory directory,
                          UiSessions sessions,
                          List<String> stationAddresses,
                          boolean registration) {
        this.directory = directory;
        this.sessions = sessions;
        this.stationAddresses = List.copyOf(stationAddresses);
        this.registration = registration;
    }

    @Override
    public boolean registrationEnabled() {
        return registration;
    }

    @Override
    public void register(String login, String password, String requestedAka) throws UiError {
        if (!registration) {
            throw new UiError("FORBIDDEN", "Public registration is disabled");
        }
        call(() -> {
            directory.register(login, password, requestedAka);
            return null;
        });
    }

    @Override
    public UserProfile me(String session) throws UiError {
        String owner = sessions.require(session);
        return call(() -> profile(directory.profile(owner)));
    }

    private UserProfile profile(AccountRecord account) throws Exception {
        List<AkaClaim> claims = new ArrayList<>();
        for (AddressClaim claim : directory.claims(account.login)) {
            claims.add(new AkaClaim(claim.address, claim.state));
        }
        return new UserProfile(account.login,
                               account.role,
                               account.enabled,
                               account.theme,
                               account.defaultAka,
                               directory.allowed(account.login, stationAddresses),
                               claims);
    }

    @Override
    public void savePreferences(String session, String theme, String defaultAka) throws UiError {
        String owner = sessions.require(session);
        call(() -> {
            directory.preferences(owner, theme, defaultAka, stationAddresses);
            return null;
        });
    }

    @Override
    public void requestAka(String session, String address) throws UiError {
        String owner = sessions.require(session);
        call(() -> {
            directory.requestAka(owner, address);
            return null;
        });
    }

    @Override
    public List<UserProfile> accounts(String session) throws UiError {
        sessions.requireAdminUser(sessions.require(session));
        return call(() -> {
            List<UserProfile> result = new ArrayList<>();
            for (AccountRecord account : directory.users()) {
                result.add(profile(account));
            }
            return result;
        });
    }

    @Override
    public void decideAka(String session, String owner, String address, boolean approve)
            throws UiError {
        sessions.requireAdminUser(sessions.require(session));
        call(() -> {
            directory.decide(owner, address, approve);
            return null;
        });
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run() throws Exception;
    }

    private static <T> T call(Operation<T> operation) throws UiError {
        try {
            return operation.run();
        } catch (IllegalArgumentException failure) {
            throw new UiError("INVALID_ARGUMENT", failure.getMessage());
        } catch (Exception failure) {
            throw new UiError("OPERATION_FAILED",
                              "Account operation failed; check the request and try again");
        }
    }
}
