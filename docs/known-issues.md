# Known issues

These observations apply to the pre-refactor baseline `d30e28f`. They are not
fixed by the readability refactor.

## Success message after a failed refresh

After a successful mutation, the page reloads its data. `refresh()` catches
reload failures internally, so `run()` still sets its success message. The UI
can therefore display both a reload error and a success message with stale data.
The same behavior exists in document and settings saves.

Reproduction: let a write succeed, then fail one of the subsequent list or
summary requests. The refactored handlers are in `workspace.svelte.ts`.
Characterization coverage: the workspace unit tests and `e2e/workflow.spec.ts`.

## Blank Word template mentions a competition selector

The blank application instructions tell the user to select a competition during
import, although the app has one permanent CloudMigration configuration and no
competition selector. The instructions are emitted by `DocxDocuments.application`.
The text is preserved to avoid changing document output during refactoring.

## Rejected submission closes the application editor

The submission handler awaits the action runner, which catches request errors,
and then unconditionally closes the editor. A failed submission therefore
returns to the application list and discards any unsaved local edits.

Reproduction: open a saved draft, edit its title, then reject the submission
request. `e2e/workspace.spec.ts` preserves this behavior with mocked requests.

## Deleting the last row leaves pagination out of range

Refreshing the application list does not adjust the current page index.
Deleting the only application on the second page leaves an empty table and
`Strona 2 z 1`, although ten applications remain on the first page.

Reproduction: list eleven drafts, go to the second page, then delete its only
row. `e2e/workspace.spec.ts` preserves this behavior with mocked requests.
