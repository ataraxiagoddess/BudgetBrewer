# AI Agent Directives for Budget Brewer

This file is for AI tools, coding assistants, and future automation helpers
working in this repository.

Keep this file short on purpose. The full project story belongs in `docs/`, not
inside the agent instructions.

## Critical Project Rules

Read these before suggesting code, dependencies, architecture changes, or UI
rewrites.

- Do not assume architecture or dependencies from this document alone. Inspect
  the current code and Gradle configuration before proposing changes.
- No Jetpack Compose. Budget Brewer uses traditional Android XML layouts with
  ViewBinding.
- No Hilt or Dagger. Dependency injection is manual through ViewModel factories.
- No MVI rewrite. The app uses standard MVVM with ViewModels, UI state, and UI
  events.
- Do not replace the current navigation model with a Jetpack Navigation graph.
  Navigation is handled programmatically with `NavDestination`, `BaseActivity`,
  and `NavigationManager`.
- Do not split data access into feature-specific repositories unless the project
  intentionally changes architecture. `BudgetRepository.kt` is the central data
  access layer.
- Primary keys are `String` UUIDs, usually created with
  `UUID.randomUUID().toString()`.
- Timestamps are stored as epoch milliseconds. Do not introduce database fields
  that require Java `Date` objects.
- Entities use Kotlin serialization annotations such as `@Serializable` and
  `@SerialName("snake_case")` for Supabase alignment.
- Do not add `@ForeignKey` annotations casually. The current project relies on
  explicit repository behavior and migration control.
- Do not add `@ColumnInfo` casually. Existing exceptions, such as Room default
  values needed for migration behavior, should be preserved unless there is a
  deliberate migration decision.

## Current Architecture Snapshot

- Package: `com.ataraxiagoddess.budgetbrewer`
- minSdk: 24
- compileSdk / targetSdk: 37
- UI: XML layouts, ViewBinding, Material Components
- Architecture: MVVM + central repository + Room + offline-first Supabase sync
- Database: Room, currently version 8
- Sync: Room first, then Supabase through `SyncManager`; failed operations are
  queued in `pending_sync` and retried by `SyncWorker`.
- Navigation destinations: Home, Finances, Savings, Expenses, Spending,
  Calendar, Settings

If this snapshot conflicts with the code, trust the code and update this file.

## Start Here

Before changing code or docs, read the relevant files:

- `README.md` for the project overview.
- `docs/ARCHITECTURE.md` for durable architecture decisions.
- `docs/DESIGN_SYSTEM.md` for visual and component standards.
- `docs/RESPONSIVE_STRATEGY.md` for layout adaptation rules.
- `docs/ACCESSIBILITY.md` for TalkBack and accessibility expectations.
- `docs/TESTING.md` for the review checklist.
- `docs/SCREEN_REVIEWS.md` for screen-by-screen review status.
- `CHANGELOG.md` before adding user-facing changes.

## Coding Expectations

- Keep Activities and Fragments focused on observing state and binding views.
- Keep business/data behavior in ViewModels, repository code, or the existing
  data/sync layers as appropriate.
- ViewModels should expose a single screen UI state where the local pattern
  already does that.
- One-time UI events, such as Snackbars, should use the existing event pattern
  rather than being mixed into persistent UI state.
- Database and network work should stay off the main thread and follow the
  existing coroutine/ViewModel patterns.
- Add strings through Android resources, including accessibility strings.
- Use resource dimensions instead of raw pixel values for spacing, margins,
  padding, and runtime-created views.
- Prefer existing styles and drawables before creating a new one-off style.

## UI And Accessibility Expectations

Budget Brewer treats accessibility and responsive layout as part of the feature,
not as cleanup after the screen is already built.

For UI work:

- Preserve the XML/ViewBinding approach.
- Keep text readable at larger font and display sizes.
- Use minimum heights for touch targets instead of fixed heights when labels may
  need to grow.
- Do not shrink important text just to keep a compact layout.
- Group card or row content for TalkBack when that is clearer than exposing
  every child view.
- Hide decorative chart pieces, legends, and duplicate child labels when the
  parent view provides the useful accessible summary.
- Use accessibility headings only for real section headers.
- Preserve scroll position when accessibility-driven layout changes rearrange
  the screen, where practical.

The Home screen is the current reference implementation for accessibility and
responsive layout behavior.

## Documentation Expectations

If behavior changes in a user-facing way, update docs in the same branch when
practical.

- Put completed changes in `CHANGELOG.md`.
- Put durable architecture decisions in `docs/ARCHITECTURE.md`.
- Put visual/component rules in `docs/DESIGN_SYSTEM.md`.
- Put layout adaptation rules in `docs/RESPONSIVE_STRATEGY.md`.
- Put accessibility standards in `docs/ACCESSIBILITY.md`.
- Put repeatable testing steps in `docs/TESTING.md`.
- Put screen-specific findings in `docs/SCREEN_REVIEWS.md`.

Do not add README links to files that do not exist.

## Version Control Expectations

- Commit by purpose, not by file.
- Use clear conventional commit messages where practical.
- Avoid permanent work-in-progress commits unless the branch will be cleaned up before merging.
- See `CONTRIBUTING.md` for the repository workflow.