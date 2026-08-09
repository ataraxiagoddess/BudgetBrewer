# Changelog

All notable changes to Budget Brewer are documented in this file.

The project has not yet reached its first public production release. Until a
version is tagged, completed changes remain under `[Unreleased]`.

## [Unreleased]

### App State

#### Fixed

- Changed month selection to persist only for the current app session, keeping
  screen data aligned with the selector while resetting to the real current
  month after a fresh app launch.

### Finances / Savings

#### Fixed

- Replaced incompatible Material 3 icon button styles with shared Material Components styles.
- Removed the runtime ResourcesCompat ColorStateList inflation warning.

### Build

#### Changed

- Removed unused Android dependencies and obsolete version-catalog entries.
- Added Fragment KTX as an explicit dependency instead of relying on a transitive dependency.

#### Verified

- Verified a clean Gradle build after dependency cleanup.

### Home

#### Added

- Added structured TalkBack support for Home dashboard content.
- Added accessibility headings for dashboard sections.
- Added grouped TalkBack announcements for Income, Savings Comparison, and
  Spending Trends rows.
- Added localized accessibility text for Home dashboard announcements.
- Added responsive timeframe controls that switch between one row and two rows
  when labels need more space.

#### Changed

- Changed the Home dashboard to one column while touch exploration is active.
- Changed chart and card accessibility so decorative pieces and duplicate labels
  do not create unnecessary TalkBack stops.
- Changed timeframe controls to use flexible heights so larger text and
  translated labels can wrap safely.
- Changed fixed four-button layout behavior to use deterministic one-row or
  two-row arrangements.

#### Fixed

- Fixed scroll-position loss when the Home dashboard rearranges.
- Fixed timeframe labels clipping or wrapping badly at large font and display
  settings.
- Fixed duplicate or noisy TalkBack announcements in grouped dashboard content.

#### Verified

- Verified Home on a physical Galaxy S24 Ultra.
- Verified Home with Galaxy S10e, Galaxy S26, Galaxy S26 Ultra, Galaxy Z Fold7,
  Galaxy Tab S11, and Galaxy Tab S11 Ultra emulator configurations.
- Verified TalkBack heading semantics.
- Verified large-font and display-scaling behavior.
- Verified longer German labels in the timeframe selector.
- Verified no known crashes or relevant Logcat errors in the completed Home
  scenarios.

### Documentation

#### Added

- Added architecture, accessibility, responsive strategy, testing, screen
  review, contribution, user guide, template, TODO, roadmap, and technical-debt
  documentation.
- Added a structured Home screen review.
- Added the initial Budget Brewer design system.
- Added Home timeframe selector guidance as a reference pattern.

#### Changed

- Clarified Budget Brewer's product direction and monthly budget model.
- Separated actionable work, future product direction, and known engineering
  improvements.
- Reworked `AGENTS.md` into concise repository-specific instructions.
- Split the older project report into focused documentation files.
- Updated the design system to separate visual standards from responsive layout
  strategy.
- Cleaned up the README documentation structure.
- Consolidated useful component and review standards from older UI notes.
- Moved responsive layout adaptation rules into
  `docs/RESPONSIVE_STRATEGY.md`.

#### Fixed

- Removed README links to documentation files that did not exist.
- Removed duplicated documentation sections from the README.
