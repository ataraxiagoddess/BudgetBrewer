# Accessibility

Accessibility is part of Budget Brewer's design, not a separate polish step.

This document describes the current accessibility expectations for the app. It
is based mostly on the completed Home screen pass, because Home is currently the
best reviewed example.

## Goal

Budget Brewer should make sense when a user:

- sees the screen visually;
- uses TalkBack;
- uses touch exploration;
- increases font size;
- increases display size;
- uses a smaller phone;
- uses a larger phone;
- uses translated text with longer labels.

The goal is not to technically pass a checklist while still feeling awkward. The
goal is that the app remains understandable and usable.

## Current Reference Screen

The Home screen is the current reference implementation.

The completed Home work includes:

- TalkBack-friendly dashboard structure.
- Accessibility headings for real dashboard sections.
- Grouped announcements for cards and rows.
- Chart summaries or alternatives where visual information needs an accessible
  equivalent.
- Decorative chart pieces and duplicate labels removed from the accessibility
  tree where appropriate.
- A single-column dashboard while touch exploration is active.
- Scroll-position preservation when the dashboard layout changes.
- Responsive timeframe controls that handle large text, display scaling, and
  longer localized labels.

## Headings

Use accessibility headings for visible titles that introduce real sections.

Good Home examples:

- Income versus expenses.
- Expenses breakdown.
- Savings comparison.
- Spending trends.
- Spending by tag.

When TalkBack says something like `Income versus expenses, heading`, that is
expected. The word `heading` is supplied by TalkBack because the view has
heading semantics. It helps screen-reader users understand sections and move
between them.

Do not mark a view as a heading if it is decorative, hidden from accessibility,
duplicated by a parent summary, or not actually introducing a section.

## Grouped Announcements

Some visual layouts are made of several small labels, values, icons, chart
pieces, and legends. That can become noisy in TalkBack if every child is
announced separately.

Use grouped announcements when the grouped version is clearer.

Examples:

- A dashboard card may read as one summary instead of several disconnected
  labels.
- A Spending Trends row may read as one row, such as month plus amount.
- A Savings Comparison card may provide a single useful summary instead of
  exposing decorative chart pieces and legend labels separately.

When a parent row or card provides the useful announcement, child views that only
duplicate the same information should usually be hidden from the accessibility
tree.

## Charts

Charts cannot be the only way to access financial information.

For chart-heavy cards:

- provide a text summary or equivalent content description;
- hide decorative chart segments when they do not add useful TalkBack
  information;
- avoid making users swipe through every visual fragment of a chart when a
  concise summary is clearer;
- keep the visible chart and the accessible summary aligned.

The Home dashboard uses this pattern and should be the starting point for future
screen reviews.

## TalkBack And Touch Exploration

TalkBack commonly uses Android's touch-exploration mode so a user can move a
finger around the screen and hear the item beneath it.

Budget Brewer listens for touch-exploration state when deciding whether certain
layouts need a simpler reading and exploration path.

The Home dashboard switches to one column while touch exploration is active.
This makes TalkBack navigation and direct touch exploration more predictable
than a two-column dashboard.

This does not mean every screen should automatically change structure whenever
TalkBack is enabled. Layout changes should only be made when they meaningfully
improve reading order, exploration, or usability.

If the layout changes while the user is already on the screen, preserve the
user's place where practical. The Home screen preserves scroll position when
the dashboard rearranges.

## Focus Order

Focus order should follow the meaning of the screen, not just the order views
happen to be written in XML.

Check:

- what TalkBack announces first;
- whether headings are reachable in a useful order;
- whether controls appear before or after the content they affect;
- whether hidden decorative content is skipped;
- whether grouped rows/cards avoid duplicate child announcements.

## Large Text And Display Scaling

Text should not clip just because the user made it larger.

For controls:

- prefer `wrap_content` height with a minimum height over fixed heights;
- allow important labels to wrap when that is the better result;
- reflow controls when one row no longer has enough room;
- do not shrink text as the first response to accessibility scaling.

The Home timeframe selector is the current example. It keeps a one-row layout
when possible and uses a two-row layout when larger text, display scaling, or
longer labels need more room.

## Localization

Accessibility text should be localized along with visible text.

When testing layout, use at least one longer-label language when practical.
German testing during the Home work exposed where English-sized assumptions were
too optimistic.

Check for:

- button labels that wrap awkwardly;
- headings that become too long;
- content descriptions that read naturally;
- currency and date formats that still make sense.

## Touch Targets

Interactive controls should remain comfortable to activate.

Use minimum touch-target dimensions through shared dimensions or styles, but do
not force a fixed height when the label may need more vertical room.

## Screen Review Checklist

For each screen, check:

- TalkBack launch behavior.
- TalkBack focus order.
- Heading announcements.
- Grouped card and row announcements.
- Chart alternatives.
- Decorative content skipped.
- Large font.
- Maximum or near-maximum font.
- Increased display size.
- Portrait and landscape when supported.
- Empty and populated data states.
- At least one longer-label localization when practical.

Record the result in `docs/SCREEN_REVIEWS.md`.

## Known Current Coverage

The Home screen was manually tested on:

- Galaxy S10e;
- Galaxy S24 Ultra.

The final Home pass included TalkBack behavior, large text/display behavior, and
Logcat checks from the tested scenarios.

## Future Work

As each screen is reviewed, add screen-specific notes to
`docs/SCREEN_REVIEWS.md`. If a pattern becomes reusable across several screens,
move the durable rule back into this document.