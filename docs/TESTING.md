# Testing

This document is the repeatable testing checklist for Budget Brewer.

The goal is to make screen reviews less dependent on memory. If a screen is
marked complete, this file should make it clear what "complete" actually means.

## Testing Levels

Use the level that matches the risk of the change.

### Small Change

Use for text, documentation, tiny styling fixes, or changes that do not affect
screen behavior.

- Build or sync if code/resources changed.
- Open the affected screen if UI changed.
- Check for obvious clipping or broken layout.
- Update docs if behavior changed.

### Screen Change

Use for layout, accessibility, visual hierarchy, navigation, chart, or control
changes.

- Build the app.
- Open the affected screen.
- Test normal font and display size.
- Test large or maximum font size.
- Test increased display size or display zoom.
- Test portrait.
- Test landscape if supported.
- Test TalkBack if the screen has non-trivial structure.
- Check Logcat while exercising the changed flow.
- Record the result in `docs/SCREEN_REVIEWS.md`.

### Cross-Screen Change

Use for shared resources, shared styles, navigation, app-wide accessibility
patterns, persistence behavior, or anything that affects multiple screens.

- Run the screen-change checklist for each affected screen.
- Check at least one unaffected screen that uses the same shared resource or
  pattern.
- Update the relevant docs.
- Add a changelog entry if the change is user-facing.

## Build And Static Checks

Use the checks that match the change.

- Android Studio sync/build.
- `./gradlew assembleDebug` when code or resources changed.
- Android Studio inspections for targeted UI files when polishing a screen.
- Lint review for changed layouts/resources when applicable.
- Logcat review during manual UI testing.

This repo does not currently have a complete automated unit/integration test
suite documented as the main quality gate, so manual screen review is still
important.

## Using The Checklists

Not every checklist item applies to every change.

Mark an item as not applicable when it does not affect the behavior being
reviewed. Do not imply that something was tested when it was not.

Suggested notation:

- `[x]` Passed.
- `[ ]` Not yet tested.
- `N/A` Not applicable.

## Manual UI Checklist

For each reviewed screen:

- [ ] Normal font size.
- [ ] Large or maximum font size.
- [ ] Normal display size.
- [ ] Increased or maximum display size.
- [ ] Portrait.
- [ ] Landscape, if supported.
- [ ] Screen state survives an orientation or configuration change, when relevant.
- [ ] Compact device or emulator.
- [ ] Large device or emulator.
- [ ] Light theme, if supported.
- [ ] Dark theme, if supported.
- [ ] English.
- [ ] Longer-label localization when practical.
- [ ] Empty data state.
- [ ] Normal data state.
- [ ] Edge-value data state when relevant.

## Accessibility Checklist

- [ ] TalkBack off.
- [ ] TalkBack enabled before opening the screen.
- [ ] TalkBack toggled while the screen is open, if relevant.
- [ ] Initial focus makes sense.
- [ ] Headings are announced for real sections.
- [ ] Focus order follows the screen's meaning.
- [ ] Cards or rows are grouped when that is clearer.
- [ ] Decorative chart or icon content is skipped.
- [ ] Chart values have text summaries or alternatives.
- [ ] Controls have useful labels.
- [ ] Dynamic content updates do not create noisy or duplicate announcements.
- [ ] Layout changes do not cause the user to lose their place when avoidable.

## Responsive Layout Checklist

- [ ] No clipped button text.
- [ ] No overlapping labels.
- [ ] No text hidden behind another view.
- [ ] Controls maintain usable touch targets.
- [ ] Important labels can wrap when needed.
- [ ] The layout does not shrink important text to solve space problems.
- [ ] Fixed control groups avoid awkward intermediate states, such as `3 + 1`
      for a four-button selector.
- [ ] Alternate layouts are deterministic and understandable.
- [ ] Resource qualifiers are used only where they are actually needed.

## Home Regression Checklist

Use this when Home changes again.

- [ ] Launch Home with TalkBack off.
- [ ] Launch Home with TalkBack already on.
- [ ] Toggle TalkBack or touch exploration while Home is open.
- [ ] Confirm the dashboard switches to one column during touch exploration.
- [ ] Confirm scroll position is preserved when the dashboard rearranges.
- [ ] Confirm headings announce as headings.
- [ ] Confirm Income, Savings Comparison, and Spending Trends read as useful
      grouped announcements.
- [ ] Confirm decorative chart content is skipped where appropriate.
- [ ] Switch Spending Trends timeframe buttons.
- [ ] Test timeframe labels at large font and display scaling.
- [ ] Test portrait.
- [ ] Test landscape.
- [ ] Check Logcat during the tested flow.

## Current Home Test Coverage

### Physical Device

- Galaxy S24 Ultra.

### Android Studio Emulators

- Galaxy S10e.
- Galaxy S26.
- Galaxy S26 Ultra.
- Galaxy Z Fold7 Cover Display.
- Galaxy Z Fold7 Main Display.
- Galaxy Tab S11.
- Galaxy Tab S11 Ultra.

Add future devices or emulators only after they are actually used during a
structured review.

## Recording Results

After testing a screen, update `docs/SCREEN_REVIEWS.md`.

Include:

- device or emulator;
- font/display settings;
- orientation;
- language;
- TalkBack state;
- what passed;
- what needs follow-up;
- any known limitations.

If the change is notable, update `CHANGELOG.md`.