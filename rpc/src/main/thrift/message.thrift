namespace cpp org.fidonet.jftn.rpc
namespace d org.fidonet.jftn.rpc
namespace java org.fidonet.jftn.rpc
namespace php org.fidonet.jftn.rpc
namespace perl org.fidonet.jftn.rpc

struct Message {
  1: string subject,
  2: string body,
}

service MessageService {
    list<Message> getMessages(1: string area)
}
