# Remaining source coverage gaps

Snapshot from the combined JaCoCo report, 2026-10-14. Counts include every handwritten source file.
These are unexecuted lines/branch outcomes, not a claim that each is a missing feature or impossible to test.
See [functional scenarios and limitations](test-coverage.md) for interpretation and proposed approaches.
Exception constructors and unused helpers are included; generated Thrift sources are excluded.

| Source | Missed lines | Missed branch outcomes | Lines with missed branches |
| --- | ---: | ---: | --- |
| [binkp/binkp-common/binkp/common/io/FilesSender.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/io/FilesSender.java) | 7 | 19 | 85, 96, 99, 107, 117, 158, 159, 163, 173, 177, 228, 238, 242, 257, 268 |
| [fido-fts/types/FTNAddr.java](../fido-fts/src/main/java/org/fidonet/types/FTNAddr.java) | 0 | 16 | 128, 131, 134, 137, 146, 147, 148, 149 |
| [fido-database/db/upgrade/version0/UpgraderV0.java](../fido-database/src/main/java/org/fidonet/db/upgrade/version0/UpgraderV0.java) | 16 | 8 | 65, 73, 77, 81, 85, 144, 148, 154 |
| [fido-database/db/upgrade/Upgrader.java](../fido-database/src/main/java/org/fidonet/db/upgrade/Upgrader.java) | 12 | 7 | 72, 74, 96, 101, 106 |
| [fido-config/config/JFtnConfig.java](../fido-config/src/main/java/org/fidonet/config/JFtnConfig.java) | 9 | 6 | 102, 127, 128, 131 |
| [binkp/binkp-mina2/binkp/mina2/Server.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/Server.java) | 6 | 5 | 88, 100, 104, 132 |
| [binkp/binkp-mina2/binkp/mina2/Client.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/Client.java) | 3 | 5 | 70, 82, 86, 95, 98 |
| [fido-database/db/plugin/DatabasePlugin.java](../fido-database/src/main/java/org/fidonet/db/plugin/DatabasePlugin.java) | 20 | 4 | 79, 83 |
| [binkp/binkp-common/binkp/common/commands/ADRCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/ADRCommand.java) | 7 | 4 | 97, 98 |
| [binkp/binkp-common/binkp/common/commands/NULCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/NULCommand.java) | 1 | 4 | 57, 58, 65, 72 |
| [binkp/binkp-common/binkp/common/commands/OKCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/OKCommand.java) | 0 | 4 | 62, 69, 72 |
| [binkp/binkp-mina2/binkp/mina2/handler/BinkSessionHandler.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/handler/BinkSessionHandler.java) | 13 | 3 | 76, 111, 146 |
| [fido-fts/fts/FtsPkt.java](../fido-fts/src/main/java/org/fidonet/fts/FtsPkt.java) | 12 | 3 | 149, 156, 161 |
| [binkp/binkp-netty/binkp/netty/plugin/handler/BinkSessionHandler.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/handler/BinkSessionHandler.java) | 9 | 3 | 79, 173, 234 |
| [binkp/binkp-netty/binkp/netty/plugin/Client.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/Client.java) | 6 | 3 | 77, 105, 117 |
| [jtoss/jftn/tosser/Tosser.java](../jtoss/src/main/java/org/fidonet/jftn/tosser/Tosser.java) | 5 | 3 | 101, 136, 137 |
| [tools/jftn/tools/SafeSimpleDateFormat.java](../tools/src/main/java/org/fidonet/jftn/tools/SafeSimpleDateFormat.java) | 2 | 3 | 108, 111 |
| [binkp/binkp-common/binkp/common/io/ReceiveTransfer.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/io/ReceiveTransfer.java) | 2 | 3 | 64, 78, 79 |
| [fido-database/db/DatabasePagingIterator.java](../fido-database/src/main/java/org/fidonet/db/DatabasePagingIterator.java) | 9 | 2 | 92, 112 |
| [rpc/jftn/rpc/Server.java](../rpc/src/main/java/org/fidonet/jftn/rpc/Server.java) | 8 | 2 | 137, 140 |
| [plugin-manager/jftn/plugins/PluginManager.java](../plugin-manager/src/main/java/org/fidonet/jftn/plugins/PluginManager.java) | 6 | 2 | 111, 149 |
| [fido-database/db/OrmManager.java](../fido-database/src/main/java/org/fidonet/db/OrmManager.java) | 5 | 2 | 95, 192 |
| [fido-database/db/DatabaseManager.java](../fido-database/src/main/java/org/fidonet/db/DatabaseManager.java) | 5 | 2 | 387, 503 |
| [binkp/binkp-common/binkp/common/io/PartialFileOutputStream.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/io/PartialFileOutputStream.java) | 4 | 2 | 34, 122 |
| [binkp/binkp-netty/binkp/netty/plugin/handler/BinkServerSessionHandler.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/handler/BinkServerSessionHandler.java) | 3 | 2 | 72, 90 |
| [binkp/binkp-common/binkp/common/commands/FILECommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/FILECommand.java) | 2 | 2 | 68, 69 |
| [jambase/echobase/jam/JAMEchoBase.java](../jambase/src/main/java/org/fidonet/echobase/jam/JAMEchoBase.java) | 13 | 1 | 163 |
| [binkp/binkp-netty/binkp/netty/plugin/Runner.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/Runner.java) | 12 | 1 | 126 |
| [binkp/binkp-mina2/binkp/mina2/Runner.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/Runner.java) | 12 | 1 | 126 |
| [fido-fts/fts/FtsPackMsg.java](../fido-fts/src/main/java/org/fidonet/fts/FtsPackMsg.java) | 5 | 1 | 96 |
| [fido-tools/types/Message.java](../fido-tools/src/main/java/org/fidonet/types/Message.java) | 5 | 1 | 193 |
| [script-api/jftn/engine/script/AbstractScriptManager.java](../script-api/src/main/java/org/fidonet/jftn/engine/script/AbstractScriptManager.java) | 3 | 1 | 93 |
| [fido-fts/types/Link.java](../fido-fts/src/main/java/org/fidonet/types/Link.java) | 3 | 1 | 108 |
| [binkp/binkp-netty/binkp/netty/plugin/commons/NettySession.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/commons/NettySession.java) | 3 | 1 | 56 |
| [binkp/binkp-mina2/binkp/mina2/commons/Mina2Session.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/commons/Mina2Session.java) | 3 | 1 | 55 |
| [binkp/binkp-common/binkp/common/config/Password.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/config/Password.java) | 2 | 1 | 122 |
| [fido-fts/fts/AbstractFidoAddressesList.java](../fido-fts/src/main/java/org/fidonet/fts/AbstractFidoAddressesList.java) | 2 | 1 | 99 |
| [jambase/echobase/jam/struct/MessageHeader.java](../jambase/src/main/java/org/fidonet/echobase/jam/struct/MessageHeader.java) | 1 | 1 | 66 |
| [binkp/binkp-mina2/binkp/mina2/codec/BinkDataEncoder.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/codec/BinkDataEncoder.java) | 1 | 1 | 45 |
| [binkp/binkp-netty/binkp/netty/plugin/codec/BinkDataDecoder.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/codec/BinkDataDecoder.java) | 1 | 1 | 54 |
| [fido-database/db/upgrade/version0/Echomail.java](../fido-database/src/main/java/org/fidonet/db/upgrade/version0/Echomail.java) | 1 | 1 | 269 |
| [binkp/binkp-mina2/binkp/mina2/codec/BinkDataDecoder.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/codec/BinkDataDecoder.java) | 0 | 1 | 56 |
| [fido-database/db/objects/Echomail.java](../fido-database/src/main/java/org/fidonet/db/objects/Echomail.java) | 0 | 1 | 241 |
| [binkp/binkp-netty/binkp/netty/plugin/Server.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/Server.java) | 0 | 1 | 95 |
| [binkp/binkp-common/binkp/common/commands/ERRCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/ERRCommand.java) | 0 | 1 | 51 |
| [fido-config/config/BaseConfig.java](../fido-config/src/main/java/org/fidonet/config/BaseConfig.java) | 0 | 1 | 121 |
| [binkp/binkp-mina2/binkp/mina2/codec/TrafficCrypterCodecFilter.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/codec/TrafficCrypterCodecFilter.java) | 0 | 1 | 68 |
| [binkp/binkp-common/binkp/common/io/FileInfo.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/io/FileInfo.java) | 0 | 1 | 156 |
| [binkp/binkp-netty/binkp/netty/plugin/codec/TrafficCrypterCodec.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/codec/TrafficCrypterCodec.java) | 0 | 1 | 62 |
| [fido-database/db/upgrade/tools/UpgraderTool.java](../fido-database/src/main/java/org/fidonet/db/upgrade/tools/UpgraderTool.java) | 0 | 1 | 58 |
| [binkp/binkp-common/binkp/common/commands/SKIPCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/SKIPCommand.java) | 0 | 1 | 61 |
| [binkp/binkp-common/binkp/common/LinksInfo.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/LinksInfo.java) | 0 | 1 | 69 |
| [fido-fts/fts/FtsMsg.java](../fido-fts/src/main/java/org/fidonet/fts/FtsMsg.java) | 31 | 0 |  |
| [jftn-bundle/jftn/JFtnRunner.java](../jftn-bundle/src/main/java/org/fidonet/jftn/JFtnRunner.java) | 24 | 0 |  |
| [fido-database/db/ScriptGenerator.java](../fido-database/src/main/java/org/fidonet/db/ScriptGenerator.java) | 20 | 0 |  |
| [script-api/jftn/engine/script/exception/NotSupportedScriptEngine.java](../script-api/src/main/java/org/fidonet/jftn/engine/script/exception/NotSupportedScriptEngine.java) | 7 | 0 |  |
| [fido-database/db/exceptions/DatabaseException.java](../fido-database/src/main/java/org/fidonet/db/exceptions/DatabaseException.java) | 7 | 0 |  |
| [fido-tools/echobase/EchoMgr.java](../fido-tools/src/main/java/org/fidonet/echobase/EchoMgr.java) | 6 | 0 |  |
| [binkp/binkp-common/binkp/common/SessionContext.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/SessionContext.java) | 6 | 0 |  |
| [binkp/binkp-common/binkp/common/commands/share/UnknownCommandException.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/share/UnknownCommandException.java) | 5 | 0 |  |
| [script-api/jftn/engine/script/exception/EngineNotFoundException.java](../script-api/src/main/java/org/fidonet/jftn/engine/script/exception/EngineNotFoundException.java) | 5 | 0 |  |
| [plugin-manager/jftn/plugins/PluginException.java](../plugin-manager/src/main/java/org/fidonet/jftn/plugins/PluginException.java) | 5 | 0 |  |
| [binkp/binkp-common/binkp/common/io/FileData.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/io/FileData.java) | 5 | 0 |  |
| [jtoss/jftn/tosser/exception/TosserException.java](../jtoss/src/main/java/org/fidonet/jftn/tosser/exception/TosserException.java) | 5 | 0 |  |
| [binkp/binkp-common/binkp/common/codec/DataBulk.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/codec/DataBulk.java) | 5 | 0 |  |
| [binkp/binkp-common/binkp/common/commands/share/CompositeMessage.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/share/CompositeMessage.java) | 4 | 0 |  |
| [binkp/binkp-common/binkp/common/events/PoolEvent.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/events/PoolEvent.java) | 4 | 0 |  |
| [binkp/binkp-common/binkp/common/config/StationConfig.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/config/StationConfig.java) | 4 | 0 |  |
| [binkp/binkp-common/binkp/common/commands/share/MessageCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/share/MessageCommand.java) | 4 | 0 |  |
| [jtoss/jftn/tosser/TossNetmailEventHandler.java](../jtoss/src/main/java/org/fidonet/jftn/tosser/TossNetmailEventHandler.java) | 3 | 0 |  |
| [binkp/binkp-common/binkp/common/crypt/DummyDecrypt.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/crypt/DummyDecrypt.java) | 3 | 0 |  |
| [fido-config/config/ParseConfigException.java](../fido-config/src/main/java/org/fidonet/config/ParseConfigException.java) | 3 | 0 |  |
| [binkp/binkp-common/binkp/common/events/FileSendEventHandler.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/events/FileSendEventHandler.java) | 3 | 0 |  |
| [fido-tools/echobase/exceptions/EchoBaseException.java](../fido-tools/src/main/java/org/fidonet/echobase/exceptions/EchoBaseException.java) | 3 | 0 |  |
| [script-api/jftn/engine/script/JythonScriptManager.java](../script-api/src/main/java/org/fidonet/jftn/engine/script/JythonScriptManager.java) | 3 | 0 |  |
| [binkp/binkp-common/binkp/common/events/PoolEventHandler.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/events/PoolEventHandler.java) | 3 | 0 |  |
| [binkp/binkp-common/binkp/common/commands/LogCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/LogCommand.java) | 3 | 0 |  |
| [script-api/jftn/engine/script/JFtnScriptService.java](../script-api/src/main/java/org/fidonet/jftn/engine/script/JFtnScriptService.java) | 3 | 0 |  |
| [binkp/binkp-common/binkp/common/codec/TrafficCrypter.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/codec/TrafficCrypter.java) | 2 | 0 |  |
| [fido-tools/Body.java](../fido-tools/src/main/java/org/fidonet/Body.java) | 2 | 0 |  |
| [binkp/binkp-common/binkp/common/commands/CramOPTCommand.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/commands/CramOPTCommand.java) | 2 | 0 |  |
| [fido-database/db/upgrade/version0/OrmManagerV0.java](../fido-database/src/main/java/org/fidonet/db/upgrade/version0/OrmManagerV0.java) | 2 | 0 |  |
| [fido-fts/fts/SeenBy.java](../fido-fts/src/main/java/org/fidonet/fts/SeenBy.java) | 2 | 0 |  |
| [binkp/binkp-common/binkp/common/codec/DataReader.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/codec/DataReader.java) | 1 | 0 |  |
| [fido-fts/tools/CharsetTools.java](../fido-fts/src/main/java/org/fidonet/tools/CharsetTools.java) | 1 | 0 |  |
| [fido-fts/types/Attribute.java](../fido-fts/src/main/java/org/fidonet/types/Attribute.java) | 1 | 0 |  |
| [fido-fts/tools/StringTools.java](../fido-fts/src/main/java/org/fidonet/tools/StringTools.java) | 1 | 0 |  |
| [tools/jftn/tools/FileUtils.java](../tools/src/main/java/org/fidonet/jftn/tools/FileUtils.java) | 1 | 0 |  |
| [fido-database/db/objects/Netmail.java](../fido-database/src/main/java/org/fidonet/db/objects/Netmail.java) | 1 | 0 |  |
| [binkp/binkp-netty/binkp/netty/plugin/Handler.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/Handler.java) | 1 | 0 |  |
| [binkp/binkp-netty/binkp/netty/plugin/commons/SessionKeys.java](../binkp/binkp-netty/src/main/java/org/fidonet/binkp/netty/plugin/commons/SessionKeys.java) | 1 | 0 |  |
| [binkp/binkp-common/binkp/common/SessionHelper.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/SessionHelper.java) | 1 | 0 |  |
| [fido-database/db/upgrade/version0/ConfigurationLink.java](../fido-database/src/main/java/org/fidonet/db/upgrade/version0/ConfigurationLink.java) | 1 | 0 |  |
| [binkp/binkp-mina2/binkp/mina2/commons/SessionKeys.java](../binkp/binkp-mina2/src/main/java/org/fidonet/binkp/mina2/commons/SessionKeys.java) | 1 | 0 |  |
| [binkp/binkp-common/binkp/common/protocol/SessionWrapper.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/protocol/SessionWrapper.java) | 1 | 0 |  |
| [binkp/binkp-common/binkp/common/crypt/DummyEncrypt.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/crypt/DummyEncrypt.java) | 1 | 0 |  |
| [fido-tools/misc/MyCRC.java](../fido-tools/src/main/java/org/fidonet/misc/MyCRC.java) | 1 | 0 |  |
| [binkp/binkp-common/binkp/common/codec/DataInfo.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/codec/DataInfo.java) | 1 | 0 |  |
| [fido-tools/misc/Zipper.java](../fido-tools/src/main/java/org/fidonet/misc/Zipper.java) | 1 | 0 |  |
| [binkp/binkp-common/binkp/common/events/BinkpAbstractEvent.java](../binkp/binkp-common/src/main/java/org/fidonet/binkp/common/events/BinkpAbstractEvent.java) | 1 | 0 |  |
| [jambase/echobase/jam/struct/SubField.java](../jambase/src/main/java/org/fidonet/echobase/jam/struct/SubField.java) | 1 | 0 |  |
