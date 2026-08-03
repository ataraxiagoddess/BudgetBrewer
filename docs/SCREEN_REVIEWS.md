# Screen Reviews

This file tracks screen-by-screen review status.

It is intentionally practical: what was checked, what passed, what still needs a
look, and what future-me needs to remember.

Do not treat this as a promise that every screen is done. If a screen has not
been reviewed, leave it marked that way.

## Status Key

- `Not started` - no structured review yet.
- `In progress` - review started, but known checks remain.
- `Complete for now` - reviewed and good enough to move on.
- `Needs revisit` - reviewed, but there are known problems or unanswered
  questions.

## Home

Status: Complete for now

Last reviewed: July 30, 2026

The Home screen is the current reference screen for accessibility and responsive
layout behavior.

### What Was Completed

- Startup TalkBack behavior was reviewed.
- Dashboard sections were given heading semantics where appropriate.
- Income and dashboard card content were grouped into clearer TalkBack
  announcements.
- Spending Trends rows were structured as combined accessible rows.
- Savings Comparison was summarized instead of exposing decorative chart pieces
  and duplicate labels.
- Decorative charts, legends, and redundant child labels were hidden from
  TalkBack where appropriate.
- The dashboard switches to one column while touch exploration is active.
- Scroll position is preserved when the dashboard rearranges.
- Timeframe controls adapt between a one-row and two-row layout.
- Timeframe labels no longer rely on fixed button heights that clip larger text.
- Large-font and display-scaling behavior was tested.
- German label behavior was used to catch layout assumptions that English did
  not expose.

### Review Coverage

#### Physical Device

- Galaxy S24 Ultra - Android 16.

#### Android Studio Emulators

- Galaxy S10e - Android 7.
- Galaxy S26 - Android 17.
- Galaxy S26 Ultra - Android 17.
- Galaxy Z Fold7 Cover Display - Android 17.
- Galaxy Z Fold7 Main Display - Android 17.
- Galaxy Tab S11 - Android 17.
- Galaxy Tab S11 Ultra - Android 17.

Record exact Android versions here when they have been verified from the test
configurations.

### Verified Behavior

- The final tested Home pass had no known crashes.
- The final tested Home pass had no relevant Logcat errors.
- TalkBack headings announced with the expected heading role.
- Touch exploration behavior was considered normal and desirable after review.
- Responsive timeframe behavior worked smoothly on the tested devices.

### Known Limitations

- None currently documented from the completed review.

### Follow-Up

- Use Home as the comparison point when reviewing the next screen.
- Refresh this entry if Home behavior or layout changes.
- Future Home changes may require this screen to be reviewed again.
- Add exact Android versions to the review coverage when they are confirmed.

## Finances

Status: Not started

### Notes

- This screen is likely one of the next important accessibility/responsive
  reviews because it contains the main income, expense, category, and allocation
  workflow.

## Savings

Status: Not started

### Notes

- Savings has visually rich bucket cards and transaction history behavior, so it
  will need both visual and TalkBack review.

## Expenses

Status: Not started

## Spending

Status: Not started

## Calendar

Status: Not started

### Notes

- Calendar review should include colored-dot meaning, day detail behavior, and
  whether all visual markers have a non-color-only equivalent.

## Settings

Status: Not started

## Authentication

Status: Not started

> [!NOTE]
> Use the screen-review template in `docs/TEMPLATES.md` when adding a new review.