namespace cpp org.fidonet.jftn.rpc
namespace d org.fidonet.jftn.rpc
namespace java org.fidonet.jftn.rpc
namespace php org.fidonet.jftn.rpc
namespace perl org.fidonet.jftn.rpc

struct JavaStatistics {
     1: string java_version,
     2: i64 total_memmory,
     3: i64 usage_memmory,
     4: i16 total_threads,
     5: i16 run_threads
}

struct NodeStatistics {
    1: string node_version,
    2: i16 connected_user,
    3: i64 total_areas,
    4: i64 total_messages,
    5: i64 messages_by_day
}

service StatisticService {
    JavaStatistics getJavaStatistics(),
    NodeStatistics getNodeStatistics()
}