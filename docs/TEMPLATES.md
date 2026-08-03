# Template Resources

These templates do not need to be followed perfectly. They are starting points.

## Commit Message

```text
type(scope): short description

- detail change one
- detail change two
- detail change three
```

Examples:

```text
feat(home): improve dashboard accessibility

- mark dashboard sections as accessibility headings
- group income and savings information into concise TalkBack announcements
- expose spending history rows as single accessible items
- hide decorative chart elements from screen readers
- add localized accessibility announcements
```

```text
fix(home): preserve scroll position during dashboard reflow

- capture the nearest visible dashboard card before rearranging columns
- restore the card's viewport offset after the new layout completes
- guard accessibility callbacks after the fragment view is destroyed
```

```text
fix(home): adapt timeframe controls to large text

- switch between one-row and two-row timeframe layouts when labels no longer fit
- reuse existing buttons across layouts to preserve state and listeners
- replace fixed heights so translated labels can wrap instead of clipping
```

## Changelog Entry

```markdown
## [Unreleased]

### Added

- Added ...

### Changed

- Changed ...

### Fixed

- Fixed ...

### Verified

- Verified ...
```

Questions to answer:

- What changed that a user would notice?
- What changed that future-me would need to know while debugging?
- What devices, languages, font sizes, display sizes, or screen states were
  tested?
- Was this a feature, fix, documentation change, cleanup, or a mix?

## Screen Review

```markdown
## Screen Name

Status: Not started | In progress | Complete for now | Needs revisit

Last reviewed:

Reviewed on:
- Device/emulator:
- Android version:
- Orientation:
- Theme:
- Font size:
- Display size:
- Language:
- TalkBack:

### What This Screen Needs To Do

-

### What Was Checked

-

### Findings

-

### Known Limitations

-

### Follow-Up

-
```

Questions to answer:

- What are the most important things on this screen?
- What should TalkBack read first?
- Which visual elements are decorative?
- Which rows or cards should be grouped into one announcement?
- Does anything clip, overlap, or become too small at large font sizes?
- Does the layout still make sense in landscape?
- What would I want to know six months from now?

## Accessibility Review

```markdown
## Accessibility Review: Screen Name

Date:
Reviewer:

### Structure

- Headings:
- Focus order:
- Grouped content:
- Decorative content hidden:

### TalkBack

- Startup announcement:
- Important controls:
- Dynamic content:
- Chart alternatives:
- Empty states:

### Scaling

- Large font:
- Maximum font:
- Increased display size:
- Maximum display size:

### Results

- Passed:
- Needs follow-up:
- Notes:
```

Questions to answer:

- Does the screen make sense without seeing it?
- Are headings being used for real sections?
- Is TalkBack reading duplicate labels?
- Are chart values available as text or summaries?
- Can controls grow or wrap without clipping?
- Does touch exploration change the layout, and if so, is the user's place
  preserved?

## Test Checklist

```markdown
## Test Checklist: Feature or Screen

Build:
- [ ] App builds
- [ ] No relevant lint warnings
- [ ] No relevant Logcat errors during tested flow

Layout:
- [ ] Portrait
- [ ] Landscape
- [ ] Normal font
- [ ] Large or maximum font
- [ ] Normal display size
- [ ] Increased or maximum display size
- [ ] Smaller phone
- [ ] Larger phone

Accessibility:
- [ ] TalkBack off
- [ ] TalkBack on before launch
- [ ] TalkBack toggled while screen is open
- [ ] Headings announced correctly
- [ ] Focus order makes sense
- [ ] Decorative content skipped
- [ ] Grouped rows/cards announced correctly

Localization:
- [ ] English
- [ ] Longer-label language when practical
- [ ] No clipping or awkward wrapping in key controls

Data states:
- [ ] Normal data
- [ ] Empty state
- [ ] Edge values
- [ ] Loading or refresh state if applicable
```

## Architecture Decision

```markdown
# ADR: Decision Title

Date:
Status: Proposed | Accepted | Replaced

## Context

What problem are we solving?

## Decision

What are we choosing?

## Why

Why does this fit Budget Brewer?

## Alternatives Considered

- Option:
- Pros:
- Cons:

## Consequences

What becomes easier?

What becomes harder?

What should future-me watch for?
```