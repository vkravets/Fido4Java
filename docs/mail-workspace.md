# Mail workspace

Netmail and echomail share one workspace: the application menu, a contextual
message list, and a reading or composition pane. Conference selection stays in
the echo list pane. Collapse the menu to icons using **Collapse menu**; each icon
retains its accessible label and tooltip. Point accounts retain their existing
subscription and AKA restrictions; layout preferences never grant access.

## Layout and navigation

Personal settings → Profile stores an account display name (up to 80 printable
characters). Enter it manually or search the local nodelist/pointlist catalog
using an approved AKA. The orange document-search icon beside the compact name
field opens a panel matching the combined field/button width. Changing its AKA
refreshes the results; choosing a name closes the panel. Escape or an outside
click dismisses it without changing the name. Results must match that exact address; records from
different sources remain separate choices. Selecting a result fills the editable
field, and Save publishes it. Clearing the name restores the login fallback.
Directory refreshes do not overwrite a saved name or grant address ownership.

The header, sidebar and account labels show this name. New netmail, echomail,
replies and forwards use it as the sender name, which also supplies `@cname` to
templates. Existing drafts retain their sender name and text. Normal outgoing
mail encoding and header-size validation still apply. The login remains the
authentication/storage identity and is shown explicitly in Profile.


On desktop, the three panes meet without card gutters. A 44px application header,
216px menu (56px collapsed), outline icons and compact controls leave more space
for mail. Message rows align sender, subject and date; the full date remains in
the date tooltip. Unread markers and the selected row remain visible.

Open **Display options** beside the search field. **Preview position** selects
the reading pane on the right, below the list, or
on a separate screen. Drag the separator or focus it and use Left/Right (right
preview), Up/Down (below preview), Home or End. The list occupies 20-75% of the
available space. Density is Compact or Comfortable; echo lists independently
support **Threads** and **Flat** views. These choices, split ratios and menu collapse are
saved per account. Netmail and echo layouts are independent.

The reader offers **Reply**, **Previous**, **Next** and **Next unread** within the
loaded list. **More** groups quoted/short replies, a new message, forwarding and
deletion where permitted. Short replies preserve the original reply identifier
without inserting the original quote. Expand **Conversation** to navigate the
parent and replies. Administrators have a **Links** shortcut and open polling,
logs, routing and robot management under **Station**; point accounts do not
receive these controls.

On narrow screens the list, reader and composer occupy separate screens. **Back
to messages** returns to the retained list and scroll position. Leaving a changed
unsaved draft asks for confirmation. The mobile header opens the application
menu; node administration remains available only to administrators.
This compact-layout pass targets desktop; a broader mobile redesign is deferred.

**Search messages** normally filters the currently loaded headers and bounded text
excerpts. It is not a full archive or full body search. Load more messages to
extend this local scope. Matching literal text is highlighted in the open reader.
With the experimental text-only `RpcExperimentalMailSearch` flag enabled, this
same control searches the selected archive through RPC for queries of two or
more characters. Its status identifies the scope; Load more continues scanning,
including after an empty results page. Clearing restores the loaded list.
Navigation, list/tree modes and highlighting use the displayed results in both
modes. The disabled flag retains local filtering.
See [server search setup and limits](rpc-ui.md#experimental-server-side-mail-search).
Echo threading uses exact MSGID/REPLY, including explicit missing-parent states;
subjects do not define conversations.

## Plain-text visual editor

Crossposting to multiple echo areas is **not implemented**. The composer and RPC
submit to one area at a time; XC/XP lines do not trigger additional publications.
The [crossposting proposal](crossposting-plan.md) records the standards/source
analysis, selector design and required server-side recovery work.

The composer keeps recipient and subject fields in a compact header. Echomail
shows only the recipient name; netmail has separate name/address fields on one
row, stacking in a narrow pane. Both fields search the local nodelist/pointlist;
choosing a netmail match fills both values, while an echo match changes only the
name. Manual entry remains available. The sender name and permitted AKA appear
in the heading; **Edit sender** opens their fields without losing draft edits.
Hidden invalid sender fields reopen when validation requires attention.
On desktop, the composer opens with the message list hidden (also for a below
preview). The heading's **Show message list / Hide message list** button toggles it.
It retains the draft, undo history, list scroll position and split ratio. Closing
the editor restores the list without changing saved workspace preferences. The
reader has no such button; mobile and separate-screen layouts already hide the list.

The visual editor and reader share a FIDO text parser. They distinguish quote
depths, signatures, tearline, Origin, links and FTN addresses. Style codes remain
plain text: `*bold*`, `/italic/`, `_underline_`, `#reverse#`. Visual mode hides paired
style markers outside the active selection; **Source text** shows the literal
markers. **Preview** displays the outgoing text with the selected editor theme.
Incoming HTML remains literal text, never executable markup. Only HTTP/HTTPS
links become anchors.

Select text and use the formatting toolbar, or Ctrl/Cmd+B, I or U. Multiline
formatting preserves quote prefixes and blank paragraphs. **Quote** prefixes the
selected lines; Undo and Redo preserve editing history across mode switches.
Quote colors cycle through the three configured levels: 1/4/7 use the first,
2/5/8 the second and 3/6/9 the third. Reader and visual editor use the same
classification; the appearance preview includes levels one through six.
The formatting/mode toolbar uses icons with translated tooltips and accessible
labels. Undo (Ctrl/Cmd+Z) reverts the last text edit; Redo
(Ctrl/Cmd+Shift+Z) restores an undone edit. The template selector and icon-only
**Apply template** button sit at the end of the editor toolbar, wrapping as needed.
The message reader's header actions also use icons with translated tooltips and
accessible labels, including the live appearance preview. The More menu retains
text labels for its choices.
Mail appearance settings place controls beside a live preview on desktop. Choosing
a reading/editor theme or changing a custom color immediately updates the real
message reader/composer components in that preview, without changing saved
preferences. The preview has its own editable sample; it cannot send mail or save
drafts. Use **Save preferences** or **Save personal theme** to persist changes.
On narrower screens the preview follows the controls.

The status shows UTF-8 bytes; outgoing text is limited to 60,000 bytes. Line
endings are normalized to LF in the editor; packet encoding remains server-owned.
**My settings → Mail appearance → Default outgoing encoding** selects UTF-8,
CP866, KOI8-R, CP1251 or CP848 for new letters. The preference belongs to the
current account; saved drafts retain their `encoding`. The editor status shows
the selected encoding alongside the separate UTF-8 size limit. UI/RPC submission
writes the matching `CHRS` declaration and PKT bytes for both netmail and echomail.
Unsupported encodings and characters that cannot be represented are rejected
before mail enters the outgoing queue. RPC clients set `MailDraft.encoding`;
omitting it uses UTF-8. Incoming messages use their
`CHRS`/`CHARSET` declaration, with the legacy CP866 fallback described in
[Mail processing](tosser.md). `JamCharset` is a separate JAM decoding fallback,
and the nodelist encoding setting applies only to directory imports.
The backend still validates headers, authors, recipients and routing controls.

Netmail **Add to send queue** stages the letter without creating PKTs; use the
separate **Send queued mail** action to release it. **Post message** uses normal
echomail submission/export. These controls do not bypass transport scheduling.
**Forward** copies an original into a new netmail draft with an empty recipient;
it does not reuse the original MSGID/REPLY as a new letter's identity.

## Independent mail themes

In **Settings > Mail appearance**, choose Reading theme and Editor theme
independently of the application light/dark palette. Built-ins are Paper,
Graphite and Terminal. Save a personal copy to customize the fixed color tokens;
live preview, JSON import/export and deletion operate on personal copies.
Import only stages a preview until **Save personal theme** succeeds.

Operator files are UTF-8 JSON under `themes/mail/`, relative to the main node
configuration directory. The UI never overwrites these files. The public
`GET /ui/mail-themes.json` endpoint exposes validated theme data only: never put
secrets in these files. Files are read when the UI fetches the palette catalog;
reload the browser to pick up operator changes. No server restart is needed.

Example (all ten color keys are required):

```json
{
  "id": "operator-paper",
  "name": "Station paper",
  "colors": {
    "background": "#fffdf7", "text": "#242424",
    "quote1": "#116577", "quote2": "#6e4090", "quote3": "#82551e",
    "ending": "#66615b", "link": "#805600", "selection": "#f4d38c",
    "caret": "#242424", "search": "#f4d38c"
  }
}
```

The loader accepts at most 32 nonrecursive regular `.json` files of at most
32 KiB each. Symlinks, malformed files and unsafe color values are ignored.
Theme IDs use lowercase letters, digits, `_` and `-` (1-48 characters), names
1-80 characters and colors `#RRGGBB`. Operator themes cannot replace built-ins.
Personal settings accept up to 20 custom palettes. If an operator theme disappears,
rendering falls back to Paper; save another available palette in settings.

## Templates

**My settings → Mail templates** manages personal copies of New message, Short reply, Quoted reply
and Forward defaults. Edit the template text, insert a variable using its button,
and inspect the live preview. Unsupported tokens block saving. Built-ins remain
available. A default can apply to a composition kind globally, to one approved
AKA, one conference, or both. Matching precedence is AKA+conference, conference,
AKA, then generic. Conference names match case-insensitively.

The composer applies the chosen default to a fresh draft and offers a manual
**Template > Apply template** action. Replacing existing text requires confirmation.
Changing headers updates the automatically generated body only while that body
is still untouched, preserving focus in the edited header field. Saved drafts
retain their text and original reply context; opening an existing draft does not
reapply a template. Reader original context
is data, not permission to author mail or inject transport kludges.

The built-in defaults follow GoldED+'s internal template: `Hello <first name>!`
(or `Hello everybody!` for All or an empty recipient), a first-name signature,
the original date/author in replies, and the insertion point above quoted text.
Forwards put the original author/address/date and unchanged message first,
then the greeting and insertion point. These are adapted to the persisted JFtn
draft context: the original date already includes time; original recipient and
source-area headings are omitted because those values are not retained.
Personal templates, default rules and already saved draft bodies are unchanged.
Reference: [GoldED+ built-in template](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/golded3/getpls.h).

Supported GoldED+-inspired tokens:

| Token | Value |
| --- | --- |
| `@cname`, `@cfname`, `@caddr` | Current author, first name, AKA |
| `@fname`, `@faddr` | Author aliases |
| `@tname`, `@tfname`, `@taddr` | Recipient, first name, address |
| `@dname`, `@daddr` | Recipient aliases |
| `@oname`, `@ofname`, `@oaddr`, `@odate` | Original author, first name, address, date |
| `@tpseudo` | Recipient first name; `everybody` for All or an empty recipient |
| `@subject`, `@cecho`, `@areaname` | Subject and conference |
| `@cdate`, `@ctime` | Composition date/time |
| `@quote`, `@message` | Quoted or unchanged original text |
| `@position` | Initial cursor position (first occurrence) |
| `@@` | Literal `@` |

Tokens are case-insensitive and expanded once; inserted message data is never
expanded recursively. Line prefixes `@new`, `@reply`, `@quoted`, `@forward`,
`@echo`, `@net` include that line only for the selected template kind/mail type.
A line starting with `;` is a comment. This is a deliberately bounded subset,
not full GoldED template-language compatibility. No includes, arbitrary file
paths, command execution, HTML or Groovy are supported. Templates are limited to
6,000 characters, 20 personal templates and 64 default rules. Expanded output
must fit the normal 60,000-byte limit.

Personal workspace, palette and template documents live with the RPC account
database in `rpc_ui_documents`, isolated by authenticated account. Back up that
database along with normal mail/UI state. Operator palette files belong to the
installation and should be backed up separately.

## Typography

Mail appearance includes independent reader and editor font families, sizes
(10–32 px) and line heights (1.0–2.0). These personal preferences live in the
account's `mail-themes` UI document, alongside palette selections; no installation
configuration or message encoding changes. Automatic metrics retain the existing
layout defaults. Changes preview immediately and persist with Save preferences.
Font choices use installed monospace families with a generic monospace fallback;
the application does not download fonts. For box drawing and block graphics,
start with DejaVu Sans Mono and line height 1.0. The sample includes a frame and
blocks. Actual glyph joins also depend on the installed font and browser rendering.
Changing typography does not replace editor text, cursor selection or undo history.

## Verification

Parser, formatting, template expansion, preference races and ownership have
focused tests. Storybook covers reader positions, narrow screens, palettes,
editor modes and invalid templates. Browser tests exercise formatting round trips,
undo, preview, forwarding, dirty-draft guards and scroll restoration. VRT uses the
pinned Linux Playwright container with both application themes, EN/UK and desktop/
mobile; see [browser testing](web-ui.md) for commands. Full archive search, trash
and arbitrary GoldED template directives remain outside this implementation.
