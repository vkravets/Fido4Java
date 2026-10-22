package org.fidonet.jftn.rpc.auth;

/** Immutable account metadata, without passwords, credential material or sessions. */
public final class AccountRecord {
    public final String login;
    public final String role;
    public final boolean enabled;
    public final String theme;
    public final String defaultAka;
    public final String displayName;

    public AccountRecord(String login,
                         String role,
                         boolean enabled,
                         String theme,
                         String defaultAka,
                         String displayName) {
        this.login = login;
        this.role = role;
        this.enabled = enabled;
        this.theme = theme;
        this.defaultAka = defaultAka;
        this.displayName = displayName.isEmpty() ? login : displayName;
    }
}
