# Changelog

All notable changes to Budget Brewer are documented in this file.

The project has not yet reached its first public production release. Until a
version is tagged, completed changes remain under `[Unreleased]`.

## [Unreleased]

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
  review, contribution, user guide, and template documentation.
- Added a structured Home screen review.

#### Changed

- Reworked `AGENTS.md` into concise repository-specific instructions.
- Split the older project report into focused documentation files.
- Updated the design system to separate visual standards from responsive layout
  strategy.
- Cleaned up the README documentation structure.

#### Fixed

- Removed README links to documentation files that did not exist.
- Removed duplicated documentation sections from the README.