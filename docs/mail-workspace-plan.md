# Mail workspace implementation

Accepted on 2026-10-20. This is an implementation checklist, not a list of shipped features.

## Interaction contract

- One application menu, collapsible to labelled icons, plus a contextual mail list
  and a reading/composition pane. Conference selection belongs to the list pane.
- Reading pane positions: right, below, or a separate screen. Resizable boundaries
  support pointer and keyboard input. Persist netmail/echo preferences independently.
- Flat/threaded presentation and compact/comfortable density are independent.
- Mobile uses list, message and compose screens with Back, retained scroll position
  and protection against discarding unsaved drafts.
- Mail rendering and editing share FTN text parsing. Visual styles remain plain-text
  markers: bold, italic, underline, reverse; distinguish quote depths and endings.
  Never render incoming mail as HTML or allow templates to grant posting identities.
- Mail themes are independent of application light/dark mode. Built-ins, read-only
  operator files under themes/mail, and personal UI overrides have separate ownership.
- The editor supports visual/source modes, undo/redo, quoting and outgoing preview.
  Keep MSGID/REPLY and transport kludges under server control.
- Templates cover new, reply, quoted reply and forward, with recipient/sender/date/
  area variables, quote insertion and cursor placement inspired by GoldED+.
  Templates are data, not executable Groovy/JavaScript; no arbitrary file includes.
- Preserve backend subscription/AKA authorization, explicit netmail release and
  normal echomail export. Node administration remains administrator-only.
- EN/UK and light/dark remain supported. No installed-node changes or push implicitly.

## Ordered commits

1. [x] Record design and implementation sequence.
2. [ ] Persist personal workspace preferences with isolation and validation.
3. [ ] Collapsible application navigation and workspace shell.
4. [ ] Accessible reusable resizable split pane.
5. [ ] Netmail right/bottom/separate preview layouts.
6. [ ] Shared echomail layout with conference selection in the list pane.
7. [ ] Independent list density and flat/threaded presentation.
8. [ ] Mobile screens, Back and retained list position.
9. [ ] Adapt administration/settings to the shell.
10. [ ] Shared FIDO text parser with behavior tests.
11. [ ] Highlighted message reader.
12. [ ] Built-in and operator-file mail themes.
13. [ ] Personal theme UI, preview, import/export.
14. [ ] Visual/source editor foundation and plain-text round trips.
15. [ ] Editor styles, quoting, history and preview.
16. [ ] Netmail editor integration.
17. [ ] Echomail editor integration.
18. [ ] Persistent template model and safe expansion.
19. [ ] Built-in new/reply/quoted/forward templates.
20. [ ] Template management UI and preview.
21. [ ] Composer selection and template defaults by kind/AKA/area.
22. [ ] End-to-end checks, affected Linux VRT, documentation and removal of replaced code.

Each functional step carries focused tests. Visible states need Storybook and
affected desktop/mobile EN/UK light/dark VRT. Compare reviewed Linux references
without update flags. Preserve unrelated local edits. Record evidence and any
remaining limitations here at stage boundaries.

## Reference

[GoldED+ reference](https://github.com/golded-plus/golded-plus/blob/master/manuals/gold_ref.txt):
STYLECODES and The Message Template. In particular, @position is cursor placement,
@caddr is the author's address, and @quote inserts the original quote.
