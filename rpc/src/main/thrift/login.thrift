namespace cpp org.fidonet.jftn.rpc
namespace d org.fidonet.jftn.rpc
namespace java org.fidonet.jftn.rpc
namespace php org.fidonet.jftn.rpc
namespace perl org.fidonet.jftn.rpc

include "share.thrift"

struct LoginResponse {
    1: share.Response respose_status,
    2: string session_id
}

struct Credential {
    // RPC account credentials; independent from FTN/binkp link passwords.
    1: string login,
    2: string password
}

service LoginService {
    // Password login. Optional WebAuthn/passkey ceremonies use the auth service.
    LoginResponse login(1:Credential cred),
    oneway void logout(1: string session_id)
}
