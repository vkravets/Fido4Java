namespace java org.fidonet.jftn.rpc

include "login.thrift"
include "ui.thrift"

struct AuthMethods {
    1: required bool password,
    2: required bool passkey
}

struct PasskeyOptions {
    1: required string requestId,
    2: required string optionsJson
}

struct PasskeyInfo {
    1: required string credentialId,
    2: required string label,
    3: required i64 created
}

service AuthService {
    AuthMethods methods(),
    PasskeyOptions beginPasskeyLogin() throws (1:ui.UiError error),
    login.LoginResponse finishPasskeyLogin(1:string requestId, 2:string responseJson) throws (1:ui.UiError error),
    PasskeyOptions beginPasskeyRegistration(1:string session, 2:string password, 3:string label) throws (1:ui.UiError error),
    void finishPasskeyRegistration(1:string session, 2:string requestId, 3:string responseJson) throws (1:ui.UiError error),
    list<PasskeyInfo> passkeys(1:string session) throws (1:ui.UiError error),
    void removePasskey(1:string session, 2:string password, 3:string credentialId) throws (1:ui.UiError error),
    void changePassword(1:string session, 2:string currentPassword, 3:string newPassword) throws (1:ui.UiError error)
}
