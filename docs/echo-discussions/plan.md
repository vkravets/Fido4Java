# Echomail discussions — implementation plan

Status: D1–D4 implemented.

Execution agreement: commit this plan before implementation and commit each
completed item separately. Run focused checks with the item they verify.

## Goal and boundaries

Replace the contents of the **Conversation** disclosure below the open echomail
message with a threaded list of replies. The main conference message list and
its existing Flat/Threads mode are separate and are not changed by this plan.
Selecting a row opens that message in the reader while keeping the disclosure
available.

The discussion has two views:

- **Current message**: the selected message is the top row; show its descendants
  in reply order as branches are expanded.
- **Whole discussion**: start at the highest reachable ancestor and show the
  selected message highlighted in the tree. If an ancestor is unavailable,
  show the existing missing-parent notice at the top.

The view switch belongs inside the disclosure. Default to **Current message**
until the user chooses another view. Persist the view in the account `workspace`
UI document independently of the main conference list mode.

When opening a row from **Whole discussion**, support two behaviors: retain the
discussion root and move the highlight, or center on the opened message and its
descendants by switching to **Current message**. Default to **center on the
opened message**. Persist this behavior in the same account preference document.
Opening a row in **Current message** always makes that row the new top row.

## Complexity and tasks

| ID | Change | Level / model | Reason |
| --- | --- | --- | --- |
| D1 | Replace the parent/direct-replies markup inside the reader disclosure with threaded rows; add the two-view control, active-row highlight, row navigation and keyboard/ARIA behavior. Reuse the visual language of the existing echo message tree. | Medium / Sol Low | The UI pattern exists, but the discussion is a separate component with its own scope and navigation. |
| D2 | Build a discussion tree from area-scoped `echoContext` responses: walk parents to the available root, load direct replies when a branch opens, page additional replies per branch, and retain loaded branches while the reader stays in the same discussion. | High / Sol Medium | The RPC returns one parent and one page of direct replies, so recursive navigation, stale requests, duplicate IDs, missing parents and cycles need explicit handling. |
| D3 | Store the discussion view and Whole-discussion selection behavior in the existing account `workspace` document; normalize missing values to Current message and center-on-open. Keep these settings separate from `echo.mode` for the main list. | Medium / Sol Low | Uses the established preference mechanism but must survive reload, account changes and failed saves. |
| D4 | Verify both views, both selection behaviors, branch paging, empty/missing-parent cases, stale responses and keyboard access. Add Storybook states and pinned Linux VRT for English/Ukrainian, light/dark and desktop/mobile, then compare without snapshot updates. | Medium / Sol Low | Existing echo fixtures and visual workflow can be extended; the tree states need deliberate coverage. |

The existing `echoContext` RPC remains the data source. Its page limit is 200;
the browser starts with the existing 25-reply page size and requests more only
for the opened branch. A Whole-discussion view represents the full accessible
conversation but loads descendants progressively, so closed branches need not
be fetched before the disclosure appears. No new RPC contract is planned.

## Acceptance and failure behavior

- Parent/child links use the server's exact MSGID/REPLY relationships, never
  subject matching or the main list's currently loaded page. Each visible row
  shows sender, subject and date; the open message has one clear highlight.
- Both views remain usable when the selected message is the root, a reply, or
  has no replies. An unavailable ancestor is reported; it does not prevent
  browsing the accessible descendants.
- A branch load shows progress and supports retry on failure without losing
  already loaded rows. Pending responses from another area, account or selected
  discussion cannot replace the current tree. Duplicate IDs and cyclic links
  cannot create duplicate rows or endless traversal.
- Loading additional replies affects only that branch. A click on a row uses
  the reader's existing authorization, unsaved-draft guard and open-message
  behavior. No new mail data is persisted by the discussion UI.

The separate TODO item for a keyboard shortcut that opens the disclosure is
outside D1–D4 and remains unfinished.
