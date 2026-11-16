# Known issues

These observations apply to the pre-refactor baseline `d30e28f`. They are not
fixed by the readability refactor.

## Success message after a failed refresh

After a successful mutation, the page reloads its data. `refresh()` catches
reload failures internally, so `run()` still sets its success message. The UI
can therefore display both a reload error and a success message with stale data.
The same behavior exists in document and settings saves.

Reproduction: let a write succeed, then fail one of the subsequent list or
summary requests. See the page refresh and action handlers.

## Blank Word template mentions a competition selector

The blank application instructions tell the user to select a competition during
import, although the app has one permanent CloudMigration configuration and no
competition selector. The instructions are emitted by `DocxDocuments.application`.
The text is preserved to avoid changing document output during refactoring.
