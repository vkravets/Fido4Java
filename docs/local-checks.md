# Selecting local checks

Backend-only work should use focused `:<module>:test` tasks and formatting checks.
Local `check` does not implicitly run `:web-ui:frontendTest`. Pass `-PuiChecks=true`
to include it, or invoke the task directly. `CI=true` enables it automatically;
the existing CI browser and visual jobs remain unchanged.

For browser changes run `:web-ui:frontendTest :web-ui:typecheckFrontend
:web-ui:typecheckStorybook`, then the affected component/application tests.
Visible changes also require relevant Storybook states and the pinned Linux VRT
workflow described in [web-ui.md](web-ui.md). Component/browser/VRT suites remain
explicit tasks, so server changes do not require a container or browser launch.

Frontend compilation and type checking can still be dependencies of packaging
and Java resource preparation. Building assets is distinct from running UI tests.
