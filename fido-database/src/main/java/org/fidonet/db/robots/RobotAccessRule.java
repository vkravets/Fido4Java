package org.fidonet.db.robots;

import org.fidonet.types.FTNAddr;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Immutable authorization rule; command passwords are stored only as verifiers. */
public final class RobotAccessRule {
    private final RobotService service;
    private final String address;
    private final String credential;
    private final String masks;
    private final String access;
    private final boolean enabled;

    RobotAccessRule(RobotService service,
                    String address,
                    String credential,
                    String masks,
                    String access,
                    boolean enabled) {
        FTNAddr parsed = new FTNAddr(address);
        if (service == null || !parsed.isValid() || masks == null || masks.isBlank()
                || masks.length() > 4096 || masks.chars().anyMatch(c -> c < 32 || c == 127)
                || credential == null || credential.isEmpty()) {
            throw new IllegalArgumentException("Invalid robot peer, credential or area masks");
        }
        for (String mask : masks.split(",", -1)) {
            if (mask.isBlank()) {
                throw new IllegalArgumentException("Robot area masks cannot contain empty entries");
            }
        }
        if (service == RobotService.AreaFix && !List.of("READ", "BOTH").contains(access)) {
            throw new IllegalArgumentException("AreaFix access must be READ or BOTH");
        }
        this.service = service;
        this.address = parsed.toString();
        this.credential = credential;
        this.masks = masks;
        this.access = service == RobotService.AreaFix ? access : "";
        this.enabled = enabled;
    }

    public static RobotAccessRule create(RobotService service,
                                         String address,
                                         String password,
                                         String masks,
                                         String access,
                                         boolean enabled) {
        if (password == null || password.isEmpty() || password.length() > 256
                || password.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Robot password must contain 1..256 printable characters");
        }
        String stored = service == RobotService.Tic ? password : RobotPassword.hash(password);
        return new RobotAccessRule(service, address, stored, masks, access, enabled);
    }

    /** Changes policy without exposing or rehashing the existing credential. */
    public RobotAccessRule withPolicy(String masks, String access, boolean enabled) {
        return new RobotAccessRule(service, address, credential, masks, access, enabled);
    }

    public RobotService getService() {
        return service;
    }
    public String getAddress() {
        return address;
    }
    public String getMasks() {
        return masks;
    }
    public String getAccess() {
        return access;
    }
    public boolean isEnabled() {
        return enabled;
    }
    String credential() {
        return credential;
    }

    public boolean verifyPassword(String password) {
        if (!enabled || password == null || password.length() > 256) {
            return false;
        }
        return service == RobotService.Tic
                ? credential.equalsIgnoreCase(password)
                : RobotPassword.verify(credential, password);
    }

    /** TIC forwarding requires the actual shared wire secret, unlike command authentication. */
    public String getDeliveryPassword() {
        if (service != RobotService.Tic || !enabled) {
            throw new IllegalStateException("Delivery password is available only for enabled TIC rules");
        }
        return credential;
    }

    public boolean allows(String area) {
        if (!enabled) {
            return false;
        }
        for (String mask : masks.split(",")) {
            if (matches(mask.trim(), area)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String mask, String value) {
        StringBuilder expression = new StringBuilder("^");
        for (char character : mask.toLowerCase(Locale.ROOT).toCharArray()) {
            expression.append(character == '*'
                    ? ".*"
                    : character == '?' ? "." : Pattern.quote(String.valueOf(character)));
        }
        return value.toLowerCase(Locale.ROOT).matches(expression + "$");
    }
}
