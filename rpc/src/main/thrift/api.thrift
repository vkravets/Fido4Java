namespace cpp org.fidonet.jftn.rpc
namespace d org.fidonet.jftn.rpc
namespace java org.fidonet.jftn.rpc
namespace php org.fidonet.jftn.rpc
namespace perl org.fidonet.jftn.rpc

include "share.thrift"

struct VersionResponse {
    1: share.Response response_status
    2: string version;
}

service Api {
   share.Response ping(),
   VersionResponse getVersion(),
}
