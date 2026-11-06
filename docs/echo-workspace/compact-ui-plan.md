# Compact echomail controls — implementation plan

Status: CUI1–CUI3 implemented; CUI4 remains.

## Goal

Make conference and message navigation compact and aligned with the existing
JFtn interface, especially on narrow screens, while retaining keyboard access
and visible focus.

## Tasks and complexity

| ID | Change | Complexity |
| --- | --- | --- |
| CUI1 | Put conference/message/editor stage buttons in one row and restore their left alignment with the surrounding panel content. | Low — Luna Medium |
| CUI2 | Reduce the echomail creation action's text size, height and padding to match neighboring application actions; retain its icon, label and primary-action status. | Low — Luna Medium |
| CUI3 | Reduce the visual weight of focused fields with a thinner, closer outline while preserving keyboard-visible focus and adequate contrast in both themes. | Medium — Sol Low |
| CUI4 | Keep the conference search name inside the field and in its accessible name, removing the duplicate visible label. Align it with message search and compact the conference list controls and rows. | Medium — Sol Low |

## Verification

- Review desktop and mobile layouts in both themes and languages.
- Cover conference, message and editor stages, including narrow layouts and long
  conference names.
- Update only affected pinned Linux VRT snapshots, inspect them, then compare
  the same selection without snapshot updates.

Implement each task in its own commit. Record accepted compactness and alignment
rules in `docs/decisions.md` when the UI changes are implemented.
