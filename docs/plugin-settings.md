# Plugin settings extensions

`SettingsExtension` in `plugin-manager` is the transport-independent settings
contract. A provider owns its schema, localized labels, validation and writes.
The browser receives data only, never executable JavaScript from a plugin JAR.

Mail providers register with `MailScripts.registerSettingsExtension` during
`MailScriptExtension.register`, before external scripts load. IDs must be unique.
The host describes active providers through `UiService.extensions`; mutations
use `UiService.saveExtension`. TCP and HTTP expose the same contract. Both
methods are protected by `AuthenticatedUi` and require an administrator.

Each view has an ID, title, source, translations and sections. Sections describe
rows and fields (`text`, `select`, `password`) and their own writability. This
allows database-managed credentials alongside file-managed technical settings.
Passwords are write-only: providers must omit stored values from descriptors;
empty edits retain an existing secret. The fileecho provider enforces this.

The host bounds mutation fields and dispatches by provider ID. Provider calls
share the mail processing lock, so changes cannot race with inbound scans.
Providers must validate before writes and use a database transaction for
multi-row changes. Unknown providers/sections/fields are rejected. File sources
must reject writes server-side, regardless of browser controls.

`Station > Plugins` renders forms and tables from these descriptors. Changing
fileecho areas, permissions, creation policies and robot credentials takes
effect for subsequent operations without restart. Enabling/disabling or removing
the plugin itself requires restart; no hot class unloading is implemented.

To add another settings provider, implement the interface and register it from
the service owning that provider. The built-in renderer needs no plugin-specific
imports. Rich executable browser extensions, general-purpose plugin hot loading
and a UI editor for `conf.d` files are outside this contract.
