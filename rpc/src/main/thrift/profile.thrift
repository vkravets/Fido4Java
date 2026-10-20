namespace java org.fidonet.jftn.rpc

include "ui.thrift"

struct AkaClaim {
    1: required string address,
    2: required string state
}

struct UserProfile {
    1: required string login,
    2: required string role,
    3: required bool enabled,
    4: required string theme,
    5: required string defaultAka,
    6: required list<string> allowedAkas,
    7: required list<AkaClaim> claims
}

service ProfileService {
    string uiDocument(1:string session, 2:string key) throws (1:ui.UiError error),
    void saveUiDocument(1:string session, 2:string key, 3:string document) throws (1:ui.UiError error),
    bool registrationEnabled(),
    void register(1:string login, 2:string password, 3:string requestedAka) throws (1:ui.UiError error),
    UserProfile me(1:string session) throws (1:ui.UiError error),
    void savePreferences(1:string session, 2:string theme, 3:string defaultAka) throws (1:ui.UiError error),
    void requestAka(1:string session, 2:string address) throws (1:ui.UiError error),
    list<UserProfile> accounts(1:string session) throws (1:ui.UiError error),
    void decideAka(1:string session, 2:string owner, 3:string address, 4:bool approve) throws (1:ui.UiError error)
}
