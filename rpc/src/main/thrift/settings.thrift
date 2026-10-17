namespace java org.fidonet.jftn.rpc

include "ui.thrift"

// Password fields are write-only: empty retains the stored value; '-' clears it.
struct MailLinkConfiguration {
    1: required string address,
    2: required list<string> localAddresses,
    3: required string host,
    4: required i32 port,
    5: required string sessionPassword,
    6: required string packetPassword,
    // INHERIT, SEPARATE or UNPROTECTED
    7: required string packetPasswordMode
}

struct NodeConfiguration {
    1: required string address,
    2: required list<string> aliases,
    3: required string system,
    4: required string sysop,
    5: required string location,
    6: required string uplink,
    7: required string host,
    8: required i32 port,
    9: required string linkPassword,
    10: required string storage,
    11: required string nodeListUrl,
    12: required string login,
    13: required string password,
    14: required string charset,
    15: required string browserOrigin,
    16: optional bool preserveReceivedFiles = true,
    // First-run only. When absent, legacy single-link fields remain supported.
    17: optional list<MailLinkConfiguration> initialLinks
}

struct SetupStatus {
    1: required bool required,
    2: required bool restartRequired
}

struct TosserConfiguration {
    1: required i32 intervalSeconds,
    2: required bool deleteProcessedFiles,
    3: required bool enforceEchoAccess,
    4: required bool autoCreateEcho
}

struct NodeLookup {
    1: required bool found,
    2: required bool boss,
    3: required string system,
    4: required string sysop,
    5: required string location,
    6: required string host,
    7: required i32 port,
    8: required string source
}

service SettingsService {
    bool reload(1:string authorization) throws (1:ui.UiError error),
    SetupStatus status(),
    NodeConfiguration current(1:string authorization) throws (1:ui.UiError error),
    NodeLookup lookup(1:string authorization, 2:string address, 3:string url, 4:string charset) throws (1:ui.UiError error),
    bool save(1:string authorization, 2:NodeConfiguration configuration) throws (1:ui.UiError error),
    list<MailLinkConfiguration> links(1:string authorization) throws (1:ui.UiError error),
    bool saveLinks(1:string authorization, 2:list<MailLinkConfiguration> links) throws (1:ui.UiError error),
    TosserConfiguration tosser(1:string authorization) throws (1:ui.UiError error),
    bool saveTosser(1:string authorization, 2:TosserConfiguration configuration) throws (1:ui.UiError error)
}
