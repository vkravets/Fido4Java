Manual settings only

Create *.conf files here for settings that are not exposed by the UI.
Files are loaded beside jftn.conf, sorted by filename. Later files override
earlier files and file-only values in jftn.conf. Relative option paths retain
their usual meaning: relative to the node's working directory.

The UI never writes these files. UI-managed options (Link, System, SysOp,
Location, NodeListUrl, NodeListCharset, RpcHttpOrigin, UiMailStorage and
PreserveReceivedFiles) are rejected here to avoid conflicting settings.

Example 50-local.conf:
ReceivedArchivePath=./mail/received

Restart the node after editing configuration. Keep private configuration outside
Git. When upgrading, preserve your jftn.conf and conf.d directory.

Plugin templates are supplied as *.conf.example. Startup creates missing
*.conf files once, never overwriting existing operator settings.
See docs/plugin-configuration.md for the file/key map. Keep all MailScript
entries together in 40-mail-scripts.conf to preserve handler registration order.
The database and scheduler plugins currently have no configurable options.

Default mail scripts load from ./scripts/mail-scripts/ as external files.
AreaFix uses database subscriptions. Configure peer/area/feed rules explicitly;
no directory catalog is opened when DirectoryFeed is absent.

RobotAccessSource=database is the default for the full package with UI/RPC.
Select RobotAccessSource=files explicitly for file-only administration; it works
with RpcEnabled=false. Existing active .conf files are not overwritten.
Use --check-robot-access to validate file rules without starting the node.
Use --import-robot-access for an explicit one-time import into an empty database
rule table, then select RobotAccessSource=database. See docs/robot-access.md.
