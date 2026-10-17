# Node and point directory plugin

The `nodelist` module imports node/point lists into a separate H2 catalog.
The full application includes it for setup/settings lookup and scripted feeds.
Scheduled `DirectorySource` refresh is disabled by default. Its
`NodeDirectoryEnabled` switch does not disable explicit imports or the UI's
lookup/download operation. Direct inbound import has a separate opt-in switch,
`DirectoryInboundEnabled`.
Its two tables belong to the plugin; the mail database schema is unchanged.
Imported records never create trusted `Link` entries, passwords, subscriptions,
outbound packets or routing rules.

## Configuration and import

Edit `conf.d/30-nodelist.conf` (paths are relative to the working directory):

```ini
NodeDirectoryEnabled=true
NodeDirectoryPath=./database/nodelist/catalog
DirectoryRefreshSeconds=300
DirectorySource=nodes|NODELIST|./lists/NODELIST.288|0|0|US-ASCII|true
DirectorySource=points|BOSS|./lists/POINTS.288
```

`DirectorySource` is repeatable:
`id|format|filename[|defaultZone|defaultNet|charset|requireChecksum]`.
IDs are case-sensitive and unique. Optional defaults are `0|0|US-ASCII|false`.
Use zone `1` and net `500` for a fragment with no enclosing Zone/Host records.
Other examples:

```ini
DirectorySource=segment|NODELIST|./lists/net500.txt|1|500
DirectorySource=fake|FAKENET|./lists/points24.288|1
DirectorySource=users|FIDOUSER|./lists/fidouser.lst|1
DirectorySource=textusers|FIDOUSER_TEXT|./lists/users.txt|1
```

Plain files and ZIP archives are detected by content, including names such as
`NODELIST.Z88`. ZIPs must contain exactly one regular file; directories without
payload are allowed. Members are streamed, never extracted. Each compressed/raw
file and each expanded member is limited to 128 MiB; lines to 64 KiB.
Unpack ARC/ARJ/LZH/RAR files separately. Filenames are explicit; the plugin does
not guess the newest day number or download files. The optional bundled
[Groovy directory feed](mail-scripts.md#automatic-nodepoint-directory-import)
imports TIC deliveries or explicitly configured completed inbound files and applies NODEDIFF/pointlist A/C/D diffs while retaining
base editions. For manually managed DirectorySource files, publish replacements
using an atomic rename.

For automatic import on binkp receipt, configure `DirectoryInboundEnabled` and
repeatable `DirectoryInboundFeed` rules in `conf.d/43-directory-feed.conf`; see
[direct inbound import](mail-scripts.md#direct-import-from-binkp-inbound).
It uses the existing Groovy script for sender/name policy and this plugin for
parsing, CRC validation, ZIP streaming and atomic source replacement. No new
trusted mail links are created from the resulting directory records.

Automatic import occurs when the mail service starts. Invalid initial sources
fail startup. Later checks run before mail export, at most once per configured
interval (and no more often than mail scans). Update errors are logged and
ordinary mail processing continues with the previous valid source snapshot.
Removing the module or disabling it prevents automatic work and preserves data.

Offline import uses the same service without starting a mailer:

```powershell
.\gradlew.bat :nodelist:importDirectory "-PdirectoryConfig=directory.conf"
```

The offline configuration needs only the directory settings; the enable switch
controls automatic operation only. A failed source produces a nonzero exit.
This command uses `BaseConfig`, so it reads only the named file, not adjacent
`conf.d` fragments. Put `DirectorySource`, `NodeDirectoryPath` and any refresh
settings directly in `directory.conf`; pointing it at a main file whose directory
settings exist only in `conf.d` will not import those sources.

## Formats and validation

The parser follows the FTSC documents, available from these mirrors when the
FTSC server is unavailable:

- [FTS-5000.005](https://defsol.com/files/ftsc/fts-5000.005): hierarchy, fields,
  header CRC and file framing. CRLF, LF and CR are accepted; CRC uses original
  encoded line bytes with canonical CRLF, excluding the header and terminal
  DOS EOF. Omitted EOF is accepted; data after it is rejected. CRC-16/XMODEM
  uses polynomial `0x1021` and initial zero, matching
  [Husky nltools](https://github.com/huskyproject/nltools/blob/master/src/crc16.c).
  Present CRCs are always checked; `requireChecksum=true` also rejects absence.
- [FTS-5002.002](https://defsol.com/files/ftsc/fts-5002.002): `BOSS` and deprecated
  `POSS`; `POINT`/`NODELIST` for combined hierarchical lists; `FAKENET` converts
  Host system-name or `UBOSS:` mappings to real 4D point addresses. It requires
  a default zone when mappings omit it. Boss metadata is not imported as a
  replacement for the boss node. `FIDOUSER` checks consistent record widths,
  surname ordering and address placement at column 46 or later.
  `FIDOUSER_TEXT` explicitly accepts the documented variable-width lookalike
  form with two spaces or a tab before the address.
- [FTS-5001.006](https://defsol.com/files/ftsc/fts-5001.006): all flags, including
  unknown and user flags, retain their order. `getBinkpEndpoints()` interprets
  IBN, INA, shorthand ports, repeated addresses and bracketed IPv6. It can use
  addresses from other IP protocol flags or the system/legacy IPv4 phone field.
  IBN support must be explicitly present. This method returns advertised
  endpoints; it does not dial, perform DNS lookup, authorize a session or decide
  whether a Hold/Down/Pvt node should receive mail.

Unknown node keywords are retained. Address components accept unsigned 16-bit
values, including the commonly needed extension above the original 32767 bound;
CSV record numbers must be nonzero. Missing segment/boss context fails with a
source ID and line diagnostic. Names decode underscores for display; system
names and flags remain unchanged. Alternative charsets, such as CP866, must be
configured explicitly. Empty lists are rejected; use explicit source removal to
delete a source completely. No implicit boss-data inheritance is fabricated for
FidoUser records that contain names and addresses only.

## Storage and application API

Each source replacement is one SQL transaction, with batches of 500 records.
Malformed input, duplicate addresses within a source, bad CRCs, archive errors
and detected changes during import roll back the entire replacement. Different
sources are independent; valid ones can update even if another source fails.
SHA-256 plus parser settings avoid importing unchanged files and survive
restart. A changed file is hashed again during import to detect races.

`DirectoryService.open(IConfig)` owns the catalog and exposes `refresh()` and
`getCatalog()`. Close the service after use. A catalog can also be opened for
queries directly with `new DirectoryCatalog(path)`:

```java
catalog.lookup("nodes", new FTNAddr("1:500/101"));
catalog.lookup(new FTNAddr("1:500/101.1"));
catalog.search("points", "Jane Doe", 0, 50);
catalog.removeSource("obsolete-source");
```

Lookup has a SQL address index. Search is a literal case-insensitive match on
sysop/system name, with required paging (maximum 500 results). Cross-source
lookup returns all matches; no silent precedence is chosen. Records include
source, line, status/keyword, address, names, phone, speed, region, current hub
and original flags. Removing a source from configuration retains its stored
snapshot until `removeSource` is called. Keep unrelated FTN domains under
separate source IDs.

The `settings.lookup` RPC already provides exact-address lookup and optional HTTP
full-list download during setup or for an administrator. It is not a general
paged catalog/search API. If several sources match, the settings handler uses the
first returned record; the catalog API itself returns all matches. Setup lookup
can fall back to a point's boss, without claiming the boss's sysop is the point.
Automatic nodelist-based mail routing remains future work.

The mail-extension SPI owns automatic file refresh; the parser and catalog are
also usable directly without a running mail service. See
[UI lookup](web-ui.md#nodelist-lookup) for its separate download settings.
