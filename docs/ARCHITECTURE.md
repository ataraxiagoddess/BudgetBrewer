# Architecture

This document is the durable architecture overview for Budget Brewer.

It replaces the older "single source of truth" project report approach. That
report was useful as a snapshot, but it mixed architecture, feature inventory,
file trees, dependency versions, backlog, known issues, and testing notes in one
place. This file keeps the parts that should remain true over time.

If something here conflicts with the code, trust the code and update the doc.

## Project Overview

- App: Budget Brewer
- Package: `com.ataraxiagoddess.budgetbrewer`
- Purpose: hands-on zero-dollar budgeting with manual control, local storage,
  optional cloud sync, and offline-first behavior
- minSdk: 24
- compileSdk / targetSdk: 37
- UI framework: Android XML layouts with ViewBinding
- Architecture: MVVM + central repository + Room + offline-first Supabase sync
- Database: Room, currently schema version 8

Budget Brewer is intentionally not built with Jetpack Compose, Hilt, Dagger, or
a Jetpack Navigation graph.

### Budget Month Model

A Budget Brewer budget represents a month's financial record and planning data.
It is not a collection of category spending limits.

The current model includes expected income, expenses, savings and spending
allocations, savings activity, spending entries, checklist state, and related
month settings.

Do not introduce app-wide concepts such as "budget percentage used," automatic
month completion, or category budget limits without a deliberate product and
data-model decision.

## Core Architecture Rules

### UI

- Screens are built with XML layouts and ViewBinding.
- Activities and Fragments should observe state, bind views, and handle view
  events.
- Heavy budgeting logic should not live in Activities or Fragments.
- Main screens are represented by Activities plus the Home Fragment.
- Modals and inputs use DialogFragments where that matches the existing pattern.

### Why Budget Brewer Uses XML Views

Budget Brewer began with Android's traditional View system and XML layouts. The
original choice was not the result of a formal comparison with Jetpack Compose,
but the architecture has since grown in ways that make Views the more practical
long-term fit for the project.

The app relies on mature View-based libraries and components, including charting,
calendar, blur, RecyclerView, DialogFragment, and custom View behavior. Rewriting
the interface in Compose would either require replacing those components or
hosting them through interoperability wrappers, creating a hybrid UI stack.

Budget Brewer also makes extensive use of Android's resource system:

- qualified layouts and dimensions;
- localized strings;
- XML drawables and selectors;
- reusable styles;
- light and dark resources;
- configuration-specific adaptation.

This model supports the project's responsive strategy directly. Measurements can
change through qualified values, structures can change through layout resources
or controlled View rearrangement, and Android can select resources for the
current configuration.

The current accessibility implementation is also built around the platform View
hierarchy. It uses heading semantics, grouped content descriptions, child
suppression, screen-reader focus, touch-exploration state, and direct focus and
scroll management. These behaviors have been manually tested across compact,
large, foldable, tablet, older-Android, and modern-Android configurations.

Jetpack Compose is capable of implementing accessible and adaptive interfaces.
The decision to remain with Views is therefore not based on a limitation of
Compose. It is based on avoiding a large rewrite, reducing interoperability
boundaries, preserving verified behavior, and continuing to use an architecture
that closely matches the app's current dependencies and responsive design
strategy.

Compose may still be evaluated for isolated future components when it provides a
clear functional or maintenance benefit. A migration would be treated as an
architectural decision requiring its own accessibility, responsiveness,
performance, and compatibility review.

XML was not necessarily the uniquely correct starting choice, but continuing with
XML is now the lower-risk, more coherent choice for this specific application.

### State

- Screen state belongs in ViewModels.
- UI state should follow the existing sealed-class/state-flow patterns where
  the screen already uses them.
- One-time UI events, such as Snackbars, should follow the existing event flow
  pattern rather than being stored as permanent screen state.

### Data

- `BudgetRepository.kt` is the central data access layer.
- Room is the local source of truth.
- The app should keep working locally without requiring an account.
- Repository methods handle local data behavior and manual cascade behavior where
  needed.
- Do not introduce feature-specific repositories unless the project deliberately
  changes this architecture.

### Sync

Budget Brewer follows an offline-first sync model:

```text
Room -> SyncManager -> Supabase
                 |
                 v
            PendingSync
                 |
                 v
             SyncWorker
```

The expected pattern is:

1. A user action updates local Room data.
2. The ViewModel or repository path triggers the relevant sync operation.
3. `SyncManager` attempts the Supabase operation.
4. If sync fails, the operation is queued in `pending_sync`.
5. `SyncWorker` retries queued operations later.

This keeps local use responsive and allows changes to be made while offline.

## Navigation

Navigation is programmatic.

The current navigation model uses:

- `NavDestination`
- `NavigationManager`
- `BaseActivity`
- explicit `navigateToX()` methods

Current destinations:

```text
HOME
FINANCES
SAVINGS
EXPENSES
SPENDING
CALENDAR
SETTINGS
```

The app does not use the Jetpack Navigation Component.

Do not introduce `nav_graph.xml`, `NavController`, or NavigationUI unless the
project deliberately changes its navigation architecture.

## Data Model Rules

The durable rules from the older project outline are:

- Primary keys are `String` UUIDs.
- New IDs are usually created with `UUID.randomUUID().toString()`.
- Database timestamps use epoch milliseconds.
- Supabase field names use snake_case.
- Kotlin serialization uses `@Serializable` and `@SerialName("snake_case")`.
- Database tables use plural snake_case names.
- DAOs expose clear insert/update/delete/query methods.

Be careful with absolute rules from older docs. For example, the older outline
said "no `@ColumnInfo`." The current code has limited `@ColumnInfo` usage for
Room default values in `MonthSettings`, so the real rule is:

> Do not add persistence annotations casually. Preserve existing migration and
> default-value exceptions unless a deliberate migration changes them.

## Main Data Areas

The current Room database includes these main data areas:

- budgets;
- incomes;
- expense categories;
- expenses;
- allocations;
- daily checklist;
- spending entries;
- month settings;
- daily income assignments;
- pending sync operations;
- savings buckets;
- savings transactions.

This list should stay high-level. Avoid turning this document into a full schema
dump. The source files and Room migrations are the source of truth for exact
fields.

## Screen Areas

Budget Brewer currently has these main user-facing areas:

- Home
- Finances
- Savings
- Expenses
- Spending
- Calendar
- Settings
- Authentication
- Lock screen
- Archive/Past Months flows

The Home screen is the current reference implementation for accessibility and
responsive layout review.

## Home Screen Decisions

The completed Home work established several reusable decisions:

- Dashboard cards can move between column layouts based on accessibility state.
- Touch exploration can justify a simpler one-column layout.
- Scroll position should be preserved when accessibility changes rearrange a
  screen.
- Charts should expose useful text summaries or equivalents.
- Decorative chart pieces, legends, and duplicate child labels should not create
  unnecessary TalkBack stops.
- Timeframe controls should adapt by giving labels more space rather than
  shrinking text or clipping.
- Fixed, known-size control groups can use deterministic rows instead of generic
  wrapping helpers when that is more stable across Android versions.

## Resource And UI Architecture

The app uses Android resources for layouts, values, colors, strings, dimensions,
fonts, and drawables.

Important resource patterns:

- Default values belong in `values/`.
- Qualified values should override only what is genuinely different for that
  configuration.
- Layout variants should exist because the structure needs to change, not just
  because spacing is slightly different.
- User-facing strings and accessibility strings belong in resource files.
- Runtime-created dimensions should come from resources, not raw pixel values.

Current resource areas include:

- `layout/`
- `layout-land/`
- `layout-sw600dp/`
- localized `values-*` directories
- `values-land/`
- `values-sw600dp/`
- `values-sw600dp-land/`
- `values-sw720dp/`
- `values-sw720dp-land/`

## Dependencies

Important dependency families include:

- AndroidX core/appcompat/activity;
- Material Components;
- ConstraintLayout;
- Room;
- Lifecycle;
- Coroutines;
- WorkManager;
- Supabase auth and Postgrest;
- Ktor;
- MPAndroidChart;
- Kizitonwose Calendar;
- BlurView;
- Timber;
- Biometric;
- DataStore;
- Tink;
- Kotlin serialization/date/time support.

Use `gradle/libs.versions.toml` as the source of truth for current versions.

## Documentation Boundaries

Use the docs this way:

- `README.md` is the public entry point.
- `docs/ARCHITECTURE.md` records durable app structure and decisions.
- `docs/DESIGN_SYSTEM.md` records visual and component standards.
- `docs/RESPONSIVE_STRATEGY.md` records layout adaptation rules.
- `docs/ACCESSIBILITY.md` records TalkBack and accessibility expectations.
- `docs/TESTING.md` records repeatable testing process.
- `docs/SCREEN_REVIEWS.md` records per-screen review status.
- `docs/USER_GUIDE.md` explains the app from the user's point of view.
- `docs/TODO.md` records discovered, actionable work.
- `docs/ROADMAP.md` records larger product goals and future direction.
- `docs/TECH_DEBT.md` records known engineering improvements.
- `CHANGELOG.md` records notable completed changes.
- `AGENTS.md` gives concise AI/tooling instructions.

Avoid copying the same full project report into multiple files. If one document
is the source of truth for a topic, link to it instead of duplicating it.

## What This File Should Not Become

Do not use this file for:

- full file trees;
- every dependency version;
- temporary TODO lists;
- current bug lists;
- roadmap planning;
- technical-debt tracking;
- every screen's review status;
- changelog entries;
- long user-guide instructions.

Those details belong in focused docs or in the code itself.