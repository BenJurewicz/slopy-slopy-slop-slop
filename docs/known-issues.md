# Known issues

All seven issues below are now fixed. The original observations are retained
for context; they also applied to the pre-refactor baseline `d30e28f`.
Each section ends with the resolution and regression coverage.

## Success message after a failed refresh

After a successful mutation, the page reloads its data. `refresh()` catches
reload failures internally, so `run()` still sets its success message. The UI
can therefore display both a reload error and a success message with stale data.
The same behavior exists in document and settings saves.

Reproduction: let a write succeed, then fail one of the subsequent list or
summary requests. The refactored handlers are in `workspace.svelte.ts`.
Characterization coverage: the workspace unit tests and `e2e/workflow.spec.ts`.

**Fixed:** `refresh()` returns its outcome. Action and save handlers show
success only after a successful refresh. Covered by workspace unit tests and the
settings-refresh browser test.

## Blank Word template mentions a competition selector

The blank application instructions tell the user to select a competition during
import, although the app has one permanent CloudMigration configuration and no
competition selector. The instructions are emitted by `DocxDocuments.application`.
The text is preserved to avoid changing document output during refactoring.

**Fixed:** Blank exports describe automatic assignment to CloudMigration,
including the fallback heading. Covered by `DocxDocumentsTest`; supplied Word
templates are unchanged.

## Rejected submission closes the application editor

The submission handler awaits the action runner, which catches request errors,
and then unconditionally closes the editor. A failed submission therefore
returns to the application list and discards any unsaved local edits.

Reproduction: open a saved draft, edit its title, then reject the submission
request. `e2e/workspace.spec.ts` preserves this behavior with mocked requests.

**Fixed:** The action runner reports whether the write succeeded. The submission
handler closes the editor only on success. Covered by `e2e/workspace.spec.ts`.

## Deleting the last row leaves pagination out of range

Refreshing the application list does not adjust the current page index.
Deleting the only application on the second page leaves an empty table and
`Strona 2 z 1`, although ten applications remain on the first page.

Reproduction: list eleven drafts, go to the second page, then delete its only
row. `e2e/workspace.spec.ts` preserves this behavior with mocked requests.

**Fixed:** Refreshing applications clamps the current page to the available
filtered pages. Covered by the eleven-draft deletion browser regression.

## Successful submission locks previously saved application data

The submission button in `DocumentView.svelte` passes
`workspace.selectedApplication`, not the current draft held by `Editor.svelte`.
Submitting without first saving therefore locks the previously saved content
and discards the visible unsaved edits. Unlike the rejected-submission issue
above, this occurs when submission succeeds and the application becomes
read-only.

Reproduction: open a saved application draft, change its title without saving,
then click **Złóż wniosek** and confirm. The submitted application retains the
old title. Save the editor draft before submitting, or block submission while
there are unsaved changes.

**Fixed:** The editor exposes whether its draft differs from the saved value.
Submission is disabled with a save-first notice until edits are saved or undone.
Covered by the dirty-draft browser regression and the full save/submit workflow.

## Report results beyond the first 500 rows are inaccessible

`Workspace.runReport()` always requests `offset: 0` and `limit: 500`.
`Reports.svelte` displays the total result count but provides no pagination or
way to retrieve subsequent rows.

Reproduction: run a report with 501 matching records. The heading reports 501
results, but only the first 500 can be viewed. Add pagination or retrieve
subsequent pages.

**Fixed:** Reports provide previous/next controls using API offsets and 500-row
pages. Running a report again starts at the first page. Covered by unit tests
and a 501-row browser regression.

## Document lists redundantly refetch every document

`DocumentRepository.list()` receives complete XML documents from `list.xq`,
then extracts their IDs and calls `get()` separately for every document. Each
additional call makes a sequential BaseX REST request and repeats XML decoding
and validation. Listing 500 documents therefore requires 501 BaseX requests
instead of one; workspace refreshes and funding calculations repeat this cost.
This is an important performance issue as the dataset grows.

Verification: trace or count `BaseXClient.execute()` calls while listing N
stored documents: one list request is followed by N individual get requests.
Decode the XML documents already returned by `list.xq` instead of refetching
them.

**Fixed:** The repository decodes the XML elements already returned by the list
query, retaining UUID and schema validation. Parameterized repository tests
cover all four document types and assert exactly one BaseX request; integration
tests cover real storage.
