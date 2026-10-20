# Storage providers and typed repositories

The storage infrastructure is shared by SQL-backed node services. Functional
plugins request domain repositories; provider adapters own connections, SQL,
ORM mappings, indexes and module schema initialization. H2 is the only currently
implemented SQL provider. An in-memory test provider verifies SPI dispatch; it
is not evidence of PostgreSQL/MySQL compatibility.

## Ownership and contracts

- `storage-api`: `StorageProvider`, `StorageSession`, `RepositoryAdapter` and
  typed repository lookup. No JDBC or ORM dependency.
- `storage-jdbc`: infrastructure SPI available to JDBC adapters only.
- `storage-h2`: URL construction, shutdown ownership, module schema metadata and
  a bounded pool (eight connections per location, ten-second borrow timeout).
- `fido-database`: the H2/ORMLite mail implementation and database plugin.
  `MailRepository` and editable robot contracts are in `fido-tools`.
- `directory-storage-h2`: directory SQL behind `DirectoryRepository`.
- `account-storage-h2`: account/passkey/profile and reader-state SQL behind
  `AccountRepository`, `AccountProfiles` and `UserMailRepository`.

The database plugin publishes `MailStorage`. RPC checks the `MailRepository`
capability instead of casting to the concrete `MailDatabaseFacade`. Domain
services do not obtain a connection or execute named arbitrary SQL queries.
A mail repository can provide additional typed repositories through
`RepositoryAccess.repository(Class<T>)`.

A backend location has one process-local owner. Multiple clients share its
repository instances and pool through reference-counted leases. Closing a setup
or directory client does not close another client's repository. The last lease
closes repositories in reverse order and then closes the backend. The node stops
workers and RPC transports before releasing storage. Borrowed extension registries
close only their own repositories, never their parent's backend.

A shared source does not mean a single JDBC connection or a transaction spanning
unrelated operations. Directory import, UI requests and mail work use independent
connections. A repository owns each transaction and rollback boundary. Sharing a
DataSource with ORMLite does not implicitly join a JDBC transaction to an ORM one.

## Existing data and configuration

Defaults preserve the existing files: mail under `database/jftn10`, directory at
`NodeDirectoryPath`, accounts at `RpcAuthPath` (or the existing Tmp fallback), and
reader state under `Tmp/ui-state`. Password hashes, passkey IDs/counters, address
grants, retained directory editions, drafts and submission reservations are not
rewritten or reset. SQL files are not automatically merged.

The optional `conf.d/05-storage.conf` contains provider selection. All declarations
for the repeated key must be in one fragment:

```ini
# StorageEndpoint=name|provider|provider-specific-location
StorageEndpoint=mail|h2|./database/jftn10
StorageEndpoint=directory|h2|./database/nodelist/catalog
# Specify accounts/reader only after checking the paths of the existing installation.
```

Names used by the application are `mail`, `directory`, `accounts` and `reader`.
Without a matching declaration, existing per-component path settings still apply.
A provider interprets and normalizes its location; business plugins do not parse
JDBC URLs. Duplicate/malformed declarations, unavailable providers and missing or
ambiguous repository adapters fail explicitly. No fallback to H2 occurs after
selecting another provider. These settings require restart and remain file-only.
The UI preserves the fragment, including during first-run setup.

Changing a location selects a different store; it does not copy data. Stop the
node, back up all related state and use a separately reviewed migration procedure
before changing locations or combining physical database files. Do not point all
endpoints at an empty file and assume old accounts/mail were transferred.

## Adding a provider or operation

1. Put a typed operation/query/result in the domain repository contract. Keep
   pagination, ordering, case sensitivity, null handling and errors explicit.
2. Implement it in backend adapters. Use a new small capability interface when
   an optional feature should not expand the mandatory contract for every plugin.
3. Register `StorageProvider` and `RepositoryAdapter` implementations under
   `META-INF/services`. A provider must supply every enabled required capability.
4. Keep backend SQL/resources in the adapter JAR. Scripts may invoke the domain API;
   Groovy is not responsible for credentials, connection lifecycle or arbitrary SQL.
5. Run shared behavioral tests, rollback/restart tests and authorization tests
   against each actual supported backend. A leftover adapter whose optional plugin
   contract is absent does not break unrelated repositories. Missing required
   implementations fail before the dependent service starts; never silently emulate a different backend's behavior.

`RepositoryAdapter.schemaVersion()` is persisted per contract in `storage_modules`.
Version 1 adopts the current schema as baseline without historical conversion.
A different recorded version is rejected before opening the repository. Future
schema changes need an explicit migration design; neither a new adapter nor a
new plugin version silently upgrades/downgrades tables. H2 DDL and file publication
are not advertised as one crash-atomic transaction.

## Current remaining boundary

Fileecho already shares the main mail database through `extensionDao` and
`extensionTransaction`; it does not open an independent database. Its DAO queries
and ORM-annotated records still live in the fileecho plugin. Moving these to a
typed `FileEchoStorage` backend adapter remains a separate pending change. Until
that is implemented, a different SQL backend must use file-managed fileecho or
provide the existing implementation's requirements. Do not claim full backend
portability for this legacy extension path.

Standalone ORM constructors remain available to isolated tests and offline tools.
Their scoped extension bridge uses that ORM store's location. Normal node startup
uses provider-owned connections and persistent schema metadata.

Legacy/Transit do not open a SQL provider or substitute a dummy database. Neutral
contracts can be present in these bundles; H2/ORM/SQL adapters remain excluded.

## Verification

Load the development environment, then run relevant checks:

```powershell
.\gradlew.bat :storage-api:test :storage-h2:test :fido-config:test
.\gradlew.bat :fido-database:test :nodelist:test :rpc:test :rpc-node:test :web-ui:test
.\gradlew.bat :jftn-bundle:test --tests '*ProfileDistributionTest' --tests '*ServicePluginDistributionTest'
.\gradlew.bat spotlessCheck verifyJavaFormatting
```

Formatting tasks can be run separately from frontend-producing tasks. Tests use
isolated state; none of these commands updates an installed station's private data.
UI/VRT suites are not required for this backend-only change.
