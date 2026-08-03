# Responsive Strategy

Budget Brewer is designed to adapt, not simply scale.

This document explains how layouts should respond to screen size, orientation,
font scaling, display scaling, localization, and accessibility state.

The useful rule from the older UI architecture notes is now the core rule for
this file:

> A layout should adapt by changing measurements first, structure second, and
> only duplicate XML as a last resort.

## Order Of Preference

When a layout starts to fail, use this order.

### 1. Override Measurements

First ask whether the existing structure is fine but the measurements need help.

Examples:

- adjust a named dimension;
- increase or reduce spacing;
- allow `wrap_content` height;
- use a minimum height instead of a fixed height;
- give text enough room to wrap;
- override a chart dimension for a specific configuration.

Do not hardcode one-off spacing or text sizes to make a screen pass on one
device.

### 2. Improve The XML

If dimensions are not enough, improve the layout itself.

Examples:

- use ConstraintLayout constraints more effectively;
- use weights where they make the relationship clear;
- use guidelines or barriers;
- make a fixed control group use explicit rows;
- let a card or row grow naturally with content.

This is often better than adding a new layout file.

### 3. Adjust Visibility Or Orientation Programmatically

If the same views need a different arrangement in a known state, programmatic
layout adjustment can be appropriate.

Examples:

- moving dashboard cards between columns;
- switching a fixed button group between one row and two rows;
- hiding decorative views from accessibility when a grouped summary exists;
- changing a dashboard to one column while touch exploration is active.

Be careful with stateful views. Moving the existing views is usually safer than
duplicating controls and synchronizing checked state, listeners, enabled state,
and accessibility state.

### 4. Create Alternate Layouts Only When Structure Genuinely Changes

Alternate XML layouts are valid, but they should exist because the structure
needs to change.

Do not create a duplicate layout just to change small spacing values. Use
qualified dimensions for that.

## Rules

- No hardcoded spacing.
- No hardcoded text sizes.
- No repeated card styling.
- Never optimize for one phone.
- Optimize for window sizes and layout behavior, not device names.
- Accessibility wins over aesthetics.
- Text should grow or reflow before it clips.
- Do not shrink important text just to preserve a compact layout.
- Do not assume English label length is enough.

## Window And Device Coverage

Test against behavior categories, not only physical devices.

Useful categories:

- compact phone;
- medium phone;
- large phone;
- tablet or expanded width;
- portrait;
- landscape;
- normal font;
- large or maximum font;
- normal display size;
- increased or maximum display size;
- longer translated labels;
- TalkBack/touch exploration.

## Current Test Coverage

The completed Home review used one physical device and several Android Studio
emulators.

### Physical Device

- Galaxy S24 Ultra.

### Emulators

- Galaxy S10e.
- Galaxy S26.
- Galaxy S26 Ultra.
- Galaxy Z Fold7 Cover Display.
- Galaxy Z Fold7 Main Display.
- Galaxy Tab S11.
- Galaxy Tab S11 Ultra.

These provide useful coverage across compact phones, larger phones, foldable
display states, and tablets.

They should still be treated as test coverage rather than device-specific design
targets. The goal is not to optimize Budget Brewer for these model names. The
goal is to verify that the layout behaves correctly across the window sizes and
configurations they represent.

## Resource Qualifier Guidance

Use resource qualifiers when the default resources cannot reasonably support a
real configuration.

Common examples:

```text
layout-land/
layout-sw600dp/
values-land/
values-sw600dp/
values-sw600dp-land/
values-sw720dp/
values-sw720dp-land/
```

Localized `values-*` directories are for strings and locale-specific resources.

Do not duplicate every dimension into every `dimens.xml` file. Android falls
back to the default value when a more specific resource does not override it.

Add an override only when testing shows the default value is wrong for that
configuration.

## Home Screen Reference Decisions

The Home screen is the current reference for responsive behavior.

### Dashboard Columns

The dashboard can use multiple columns when that works visually, but it switches
to one column while touch exploration is active.

Reason:

- TalkBack reading and touch exploration are easier in one predictable vertical
  path.
- Two visual columns can make exploration order harder to understand.

### Scroll Preservation

When the dashboard rearranges because accessibility state changes, preserve the
user's scroll position.

Reason:

- A user should not lose their place just because TalkBack was enabled,
  disabled, or detected while the screen was open.

### Timeframe Selector

The Home timeframe selector should preserve a one-row layout when it fits, but
it must reflow when larger text, display scaling, smaller width, or localization
needs more room.

Reason:

- Forcing four buttons into one row can clip text or create awkward word breaks.
- Shrinking text would punish the user for using accessibility settings.
- A fixed four-button group does not need an overly generic wrapping algorithm
  if the only acceptable layouts are one row or two rows.

Expected behavior:

```text
Normal:
[ Last Month ][ 3 Months ][ 6 Months ][ 1 Year ]

Constrained:
[ Last Month ][ 3 Months ]
[ 6 Months  ][ 1 Year  ]
```

Avoid an awkward `3 + 1` result for this fixed control group.

## Layout Helpers vs Explicit Layouts

Generic layout helpers are useful when content is genuinely dynamic.

Use a helper when:

- the number of items changes;
- content-driven wrapping is actually desired;
- the helper behaves consistently across supported Android versions;
- the result is still easy to reason about.

Use explicit rows or columns when:

- the number of controls is fixed;
- only two layout states are acceptable;
- the helper creates awkward intermediate states;
- old Android behavior differs from newer Android behavior;
- runtime layout code becomes harder than the layout problem.

## Text Scaling Rules

Do:

- let controls become taller;
- let labels wrap when appropriate;
- give controls more width by reflowing;
- test maximum or near-maximum font size;
- test display scaling separately from font scaling.

Do not:

- use fixed heights for text-heavy controls;
- shrink important text as the first fix;
- assume display zoom and font scale fail in the same way;
- assume a layout is safe because it works on a large modern phone.

## Localization Rules

Longer labels are normal.

When reviewing a layout:

- test a longer-label language when practical;
- watch for single words breaking awkwardly;
- watch for controls that only fit English;
- prefer clearer reflow over tighter text.

German testing during the Home timeframe work is the current example. The text
was not wrong; the one-row space was too narrow.

## Orientation Rules

Landscape is not automatically "more room."

On compact devices, landscape can reduce vertical space enough that cards,
charts, and controls need different behavior.

For each screen, check:

- whether content remains reachable;
- whether the reading order still makes sense;
- whether two-column layouts help or hurt;
- whether TalkBack should force a simpler vertical path.

## Review Questions

Before adding a layout variant or runtime layout logic, ask:

- What exactly is failing: width, height, reading order, touch target, or visual
  hierarchy?
- Can spacing, wrapping, or minimum sizes fix it?
- Is the component fixed or dynamic?
- Are there only two acceptable layout states?
- Would a resource qualifier be clearer than runtime code?
- Would moving existing views preserve state better than duplicating them?
- Does TalkBack need a different structure from the visual layout?
- Has this been tested on both compact and large layouts?

## Where The Older Architecture Notes Went

The useful parts of `BudgetBrewer_Architecture.txt` were split this way:

- Component standards, typography, spacing, touch targets, chart presentation,
  and visual rules belong in `docs/DESIGN_SYSTEM.md`.
- Layout adaptation order, resource qualifier guidance, orientation behavior,
  accessibility-driven layout changes, and the measurements/structure/XML rule
  belong in this file.