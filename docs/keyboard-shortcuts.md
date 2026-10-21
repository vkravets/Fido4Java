# Browser keyboard shortcuts

Open **My settings → Keyboard shortcuts** to choose **JFtn default** or
**GoldED+ (web)**. Click an assignment and press a combination to replace it;
Clear disables that action, and Restore returns it to the selected preset.
Changing presets resets pending assignments. The save icon at the upper right
applies changes; leaving unsaved edits asks for confirmation. The checkbox
disables application shortcuts. Native editor navigation and undo/redo remain.
Bold, italic and underline include Meta-key aliases to preserve the existing
editor behavior on keyboards using Command.

| Action | JFtn | GoldED+ web |
| --- | --- | --- |
| New message | C | E, Insert, Alt+E |
| Quoted reply | R | Q, F4, Alt+Q |
| Short reply | Shift+R | R, F3, Alt+R |
| Previous / next message | K / J, Left / Right | Left / Right |
| Next unread | U | Alt+Right, Alt+U |
| Search loaded headers | / | F, Alt+F |
| Back to messages | Escape | L, F9, Alt+L, Escape |
| Forward | Shift+F | M, Alt+M |
| Delete netmail | Delete | D, Delete, Alt+D |
| Submit composer | Ctrl+Enter | F2, Alt+S |
| Save draft | Ctrl+S | Ctrl+S |

GoldED mappings were checked against its
[keyboard configuration](https://github.com/golded-plus/golded-plus/blob/master/cfgs/config/goldkeys.cfg)
on 2026-10-21. This is a subset adapted to existing JFtn actions, not an emulation
of every GoldED command. Submitting netmail adds it to the send queue; submitting
echomail posts it through the existing composer action. Forward opens JFtn's
forward composer rather than GoldED's copy/move menu. No new mail operations or
authorization bypasses are introduced.

Alt+1 through Alt+8 open Netmail, Echomail, Drafts, Directory, My settings,
Station, Links and Polling. Alt+Shift+M toggles the menu, Alt+Shift+R refreshes
an available page action, and Ctrl+Alt+Enter duplicates **Send queued mail**.
Button tooltips and `aria-keyshortcuts` show currently saved assignments.

Letters are ignored in fields and editable text. Composer and editor bindings
have separate contexts; browser defaults are prevented only when an available
action handles the combination. Hidden pages, disabled controls, modal dialogs,
IME composition, AltGr and appearance previews do not receive commands.
Existing arrow navigation in trees, tabs and splitters takes precedence.
Physical letter positions make bindings independent of the keyboard layout.
Authorized reader commands in the More menu also work while that menu is closed.
Message navigation shortcuts focus the persistent preview without scrolling;
the list keeps its single selected-message highlight. During a pending read,
reader actions remain visually stable but unavailable to mouse and shortcuts.
At navigation boundaries or during a pending request, assigned arrow shortcuts
are consumed rather than falling through to list activation or browser scrolling.

Browsers and operating systems do not deliver every shortcut to a web page.
Reserved combinations such as Ctrl+L, Ctrl+R, Ctrl+W, Alt+F4, F5 and F12 cannot
be assigned. GoldED's editor Ctrl+Z save alias is omitted to keep undo;
Ctrl+L/Ctrl+R formatting uses JFtn's alternatives. Remaining combinations still
depend on browser, desktop environment and extensions. Functional checks cover
Chromium; other browsers and operating systems are not verified.

Preferences are account-owned SQL UI data exposed by
`ProfileService.uiDocument(session, "keyboard")` and `saveUiDocument`:

```json
{"version":1,"enabled":true,"profile":"golded","overrides":{"mail.reply":["Alt+Shift+Q"],"mail.delete":[]}}
```

Keys use canonical names (`Ctrl`, `Alt`, `Shift`, `Meta`, letters, digits,
`ArrowRight`, `Enter`, `Escape`, function keys and supported punctuation).
An action accepts up to four alternatives. Overlapping contexts reject duplicate
assignments; reading and composing may independently use Escape. Failed writes
retain the active configuration, and account changes discard pending loads.
