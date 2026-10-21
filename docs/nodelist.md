# Node and point directory plugin

Accepted fileecho archives can also be scanned on startup and subsequent mail
scans. Configure a `DirectoryFeed` for the actual area/name/peer and attach a
`DirectoryFeedFolder=feed-id|folder` rule. This includes old receipt-prefixed
payload names, reads only regular files directly in the folder, and skips
`dupes` subdirectories. Publication dates order full editions; older editions
cannot replace a newer retained base. Reuse the same feed for future TIC events.
For example (adjust area names, masks and trusted peer to your installation):

```ini
DirectorySource=nodes|NODELIST|./lists/nodes.lst|0|0|CP866|true
DirectoryFeed=nodes|nodelist|NODELIST.*|NODEDIFF.*|1:500/101
DirectorySource=points|BOSS|./lists/points.lst|0|0|CP866|true
DirectoryFeed=points|pnt46|PNT46REG.*||1:500/101
DirectoryFeedFolder=nodes|./fileecho/nodelist
DirectoryFeedFolder=points|./fileecho/pnt46
```

The retained base paths must be outside the scanned source folders. Keep CRC and
publication-date validation enabled unless an explicit [filename-scoped CRC exception](#scoped-header-crc-exceptions)
is needed for a known broken publication. Local folders are an explicit trusted source;
the importer does not infer trusted links from their contents. This scan can catch
up already completed TIC receipts without replaying the TIC deliveries themselves.
Point and node sources share the same catalog and UI directory.

The `nodelist` module imports node/point lists into a separate H2 catalog.
The full application includes it for setup/settings lookup and scripted feeds.
The node owns one `DirectoryService` and one source registry.
`NodeDirectoryEnabled=false` disables all automatic local and scripted imports;
explicit offline/UI operations and reads of the retained catalog remain available.
Local refresh is separately opt-in through `DirectoryRefreshEnabled` and
`DirectoryLocalSource` IDs. Direct inbound import has a separate opt-in switch,
`DirectoryInboundEnabled`.
Its two tables belong to the plugin; the mail database schema is unchanged.
Imported records never create trusted `Link` entries, passwords, subscriptions,
outbound packets or routing rules.

## Configuration and import

The web UI's **Node directory** is available to both administrators and points.
It searches all imported node/point sources by partial FTN address, sysop, station,
location, phone, Internet hostname/IP, flags and source. Duplicate addresses remain
separate records labelled with their source. Results are paged; no source gains
implicit priority or trust. Empty searches browse the catalog.

Free-form FTN address fields offer matching records after two characters, including
registration before sign-in and first setup. Public read-only search was enabled
deliberately to support registration suggestions. This exposes imported directory
metadata without login; it does not expose account/link passwords, perform DNS
lookups, download lists or grant permissions. Administrative APIs remain protected.
Fixed selectors for trusted/configured peers still allow only those peers.

The API is `SettingsService.searchDirectory(query, offset, limit, includePoints)`,
with a maximum 160-character literal, case-insensitive query and pages of 1..100 records. The
browser retains one 25-record page; suggestions request at most eight records.
Queries beginning with an FTN zone/net prefix search addresses directly using
the catalog's address index; other terms search across the text fields.
SQL queries have a five-second execution timeout. Name underscores and spaces are
equivalent, `%` and `!` are literal, and legacy `000-a-b-c-d` IP notation can be
found by dotted IP. Entries show source/type, sysop/station/location, phone, flags
and validated binkp endpoints. This reads `NodeDirectoryPath` independently of the
scheduled-import enable switch; an empty catalog returns no results.

Edit `conf.d/30-nodelist.conf` (paths are relative to the working directory):

```ini
NodeDirectoryEnabled=true
NodeDirectoryPath=./database/nodelist/catalog
DirectoryRefreshEnabled=true
DirectoryRefreshSeconds=300
DirectoryLocalSource=nodes
DirectoryLocalSource=points
DirectorySource=nodes|NODELIST|./lists/NODELIST.288|0|0|US-ASCII|true
DirectorySource=points|BOSS|./lists/POINTS.288
```

`DirectorySource` is repeatable:
`id|format|filename[|defaultZone|defaultNet|charset|requireChecksum]`.
IDs and normalized base paths are unique across the registry. IDs are case-sensitive.
A source cannot be both local-refresh-managed and delivery-managed. Optional defaults are `0|0|US-ASCII|false`.
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

Only explicitly selected `DirectoryLocalSource` files are imported on startup
when `DirectoryRefreshEnabled=true`. Invalid initial local files fail startup.
Delivery-managed sources may have missing retained bases until their first full list arrives. Later checks run before mail export, at most once per configured
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
  Present CRCs are checked by default; `requireChecksum=true` also rejects absence.
  A configured filename-scoped exception below bypasses only these header checks.
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

`DirectoryService.open(IConfig)` owns the catalog and source registry. It exposes
`DirectoryImportService` to automation; scripts never open or close a catalog.
It also exposes `refresh()` and
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

Setup/administrative HTTP nodelist downloads use the canonical `nodes` source ID,
shared with the default automatic feed. After a successful validated download and
catalog import, the earlier `setup-nodelist` source is removed. Failed downloads
retain both previous sources; unrelated directory sources are never removed.
Existing alias records disappear on the next successful UI download, not merely
by opening the read-only directory browser.

The mail-extension SPI owns automatic file refresh; the parser and catalog are
also usable directly without a running mail service. See
[UI lookup](web-ui.md#nodelist-lookup) for its separate download settings.

## Configuration consolidation

`30-nodelist.conf` owns enablement, catalog path and every `DirectorySource`
definition (format, base path, charset, address context and checksum requirement).
`43-directory-feed.conf` only selects delivery events using those source IDs.
Use `DirectoryFeed=id|area|full-mask|diff-mask|peer` for TIC and
`DirectoryInboundFeed=id|full-mask|diff-mask|peer` for direct inbound. Folder
catch-up still uses `DirectoryFeedFolder=id|folder`. The full package enables the
provider by default but declares no trusted sources or deliveries.

Existing configurations must move the format/base/charset/CRC parts out of old
feed declarations into `DirectorySource`, then shorten feeds to ID references.
Old inline feed formats are rejected with a configuration error. Startup never
overwrites existing operator files. Sources disabled through the old
`NodeDirectoryEnabled=false` must be explicitly enabled to allow scripted imports.
Periodic local imports additionally need `DirectoryRefreshEnabled=true` and
one or more `DirectoryLocalSource=id` entries. CLI `importDirectory` explicitly
imports all declared sources regardless of automatic refresh selection.

## New Point Keeper R46 checksum investigation (2026-10-21)

The apparent bad CRC in the inspected `PNT46REG.*` publications is explained by
a different checksum algorithm and different line-ending input. All **75** local
received publications and the previously downloaded public 2024 sample match
**CRC-16/ARC over the original LF body bytes**, excluding the first header line.
They do not match JFtn's canonical-CRLF CRC-16/XMODEM calculation.

| Calculation (decimal) | PNT46REG.289, 2026-10-16 | PNT46REG.320, 2024-11-15 |
| --- | ---: | ---: |
| Header value | 45257 | 15865 |
| ARC, original LF body | **45257** | **15865** |
| ARC, canonical CRLF body | 30642 | 12928 |
| XMODEM, original LF body | 46346 | 42971 |
| XMODEM, canonical CRLF body | 40818 | 54054 |

Both headers identify `New Point Keeper v.0.0.0.7`. ARC uses polynomial `0x8005`
(reflected implementation `0xA001`), initial value zero, reflected input/output,
and no final XOR. XMODEM uses polynomial `0x1021`, initial value zero,
unreflected input/output and no final XOR. For ASCII `123456789`, their check
values are `0xBB3D` and `0x31C3`, respectively. See the
[CRC catalogue](https://reveng.sourceforge.io/crc-catalogue/16.htm#crc.cat.crc-16-arc)
and [Husky's CRC implementation](https://github.com/huskyproject/nltools/blob/b1c1c0df031ad4957c1f157ff8b1aef794918eed/src/crc16.c).
Changing only LF to CRLF cannot fix this discrepancy: the polynomial differs too.

Evidence and limits:

- The 75 local publications match their received archives byte-for-byte and
  have matching TIC CRC32/size fields. The mismatch is therefore explained by
  the observed publisher checksum convention, rather than a change during local
  fileecho processing.
- A separate bitwise C implementation, compiled and run in a network-disabled
  Linux container with `podman run` and read-only inputs, matched **76/76** headers
  and passed the `123456789` check vector. The Python diagnostic independently
  reproduced all 76 matches; canonical XMODEM matched **0/76**.
- Earlier unmodified historical Husky `nlcrc` accepted a valid control and the
  normal nodelist, but rejected these pointlists. For `.289`, it reported 46346
  on the received LF file and 40818 on a CRLF copy, agreeing with the table.
- The public sample came from
  [this mirror](https://netbsd.paket.ua/pub/FIDO/R46_PNT/PNT46REG.320).
  Its earlier download bypassed an expired TLS certificate; it corroborates the
  algorithm on those saved bytes, not an authenticated original from the publisher.
- The generator's source was not located. This identifies the behavior of the
  inspected output, not a specific faulty source line or every release of NPK.

Input fingerprints:

```text
PNT46REG.289 (2469 bytes)
SHA256 69ea927b0f07750c773f313ed4cf29e73c9bef734f6b368abdf569aa9ce9e946
PNT46REG.320, public 2024 sample (2470 bytes)
SHA256 b03dcf903fba33167d3b5df78624d316e2652e5c7861719136c118be93844bda
```

Reproduce the four calculations with Python's standard library:

```shell
python tools/directory_crc.py "path/to/PNT46REG.*"
```

The diagnostic prints JSON with SHA-256, line-ending counts, header value,
calculated CRCs and matching variants. It never rewrites files. Exit zero means
the report was generated, **not** that a publication is authorized or importable.
It does not validate directory structure or replace the runtime parser.

Strict XMODEM validation remains the default. The explicit compatibility rule
below now verifies this publisher's checksum instead of skipping it. No automatic
global generator detection is enabled.

## Scoped CRC compatibility

Configure an explicit source and original filename mask in `conf.d/30-nodelist.conf`:

```ini
DirectoryCRCCompatibility=points|PNT46REG.*|new-point-keeper
```

Rules are repeatable, validated before opening storage, and use the same safe
case-insensitive filename glob syntax as `DirectoryIgnoreCRC`. Unknown source IDs,
unknown modes, bare `*`, paths and duplicate source/mask pairs are rejected.

For matching **full publications**, canonical-CRLF XMODEM is checked first. Only
if it fails may ARC over the original body bytes be used, and only when the second
line exactly equals `;S Created by New Point Keeper v.0.0.0.7.`. A missing checksum,
an altered marker, or a mismatch of both CRCs is rejected. The marker alone is
never sufficient. ARC excludes the first line and DOS EOF, preserving actual body
line endings and the presence/absence of a final newline. Successful fallback
validation is logged at INFO. Stored originals are never normalized or rewritten.

Compatibility takes precedence over a matching old ignore rule so that the
verified mode cannot accidentally fall back to skipping CRC. Remove the old
`DirectoryIgnoreCRC` entry for the same source/mask when migrating. Structural,
address, duplicate-record, archive and TIC checks remain in effect. Diff imports
always clear both compatibility and ignore modes: their base and output must
pass standard XMODEM. Direct `DirectoryUpdate` callers have the same restriction.

The mask uses the original outer delivery filename for archives and the source
basename for explicit local imports. A rule for `PNT46REG.*` does not match
`points.lst`; a deliberate local import needs its own narrow rule. Changing rules
changes source/receipt identities and removing a rule causes strict revalidation
on the next import. Existing catalog data is retained if revalidation fails.

Administrators can edit these rules through **Station → Plugins → Node directory
→ CRC checks**, using the existing extension-settings RPC as well. Select a
source, enter a filename mask and choose **New Point Keeper (XMODEM / ARC)**.
Saving atomically updates the two CRC directive sets in `conf.d/30-nodelist.conf`,
preserves its other settings/comments, removes an exact matching legacy ignore
entry, and applies the new policy to the active directory service. Selecting
**Standard XMODEM** removes that pair's compatibility/ignore entries. It is refused
if another remaining rule overlaps that source/mask, avoiding a silent fallback
to a broader exception. Other sources and masks are preserved.

Legacy ignore entries are shown separately, read-only. If CRC rules are declared
in the main file/another fragment, or `30-nodelist.conf` was edited externally
since startup, the UI becomes read-only rather than overwriting those declarations.
Edit the relevant files and restart in that case. The settings provider exists
only while the directory service is active, independently of external scripts;
the runner consumes a generic provider interface and imports no directory class.
Existing import jobs and retained catalogs are not automatically rerun or removed.

## Scoped header CRC exceptions

Automatic header CRC checking remains the default. For a known publisher with
incorrect full-list CRCs, explicitly configure a source ID and filename glob in
`conf.d/30-nodelist.conf`:

```ini
DirectorySource=points|BOSS|./lists/points.lst|0|0|CP866|true
DirectoryIgnoreCRC=points|PNT46REG.*
```

`DirectoryIgnoreCRC` is repeatable. It has no global boolean form. Masks match
the original incoming filename case-insensitively, with `*` and `?`; transport
storage names and temporary parsing filenames are not used. Each rule is limited
to its declared source. A rule must contain a literal letter/digit; bare `*`, path
masks, unknown source IDs and duplicate rules are rejected. Supported mask
characters are ASCII letters/digits, `_`, `.`, `+`, `-`, `*`, and `?` (1..128 total).

For matching **full publications only**, missing or mismatched header CRCs are
ignored even when the source normally requires a checksum. Record syntax,
encoding, address limits, hierarchy, duplicate addresses, archive validation,
size bounds and publication-date rollback checks remain enforced. TIC password,
TIC CRC32 and delivery peer validation are unchanged. The original header is not
rewritten. Each selected exception is logged at WARN with source, original filename
and mask. Keep masks narrow and use trusted delivery rules/folders.

The same rule works for TIC callbacks, completed inbound files and accepted-folder
catch-up. For ZIPs it matches the outer delivery name, not archive member names.
Diffs never use this exception: both the retained base and patched result must
pass their normal CRC checks. An accepted full base with a wrong CRC therefore
cannot be used for diff updates; deliver another full list instead.

For explicit local refresh/offline imports, the configured source file's basename
is matched. A delivery exception for `PNT46REG.*` does not automatically exempt
the retained `points.lst` when imported as a local file. Rule changes invalidate
script receipt/backoff identities so previously deferred deliveries can be retried
after restart. Already imported catalog snapshots remain until replaced or
explicitly removed; deleting the exception does not delete records or rewrite bases.
