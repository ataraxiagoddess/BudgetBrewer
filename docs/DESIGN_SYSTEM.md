# Budget Brewer Design System

The design system defines the visual, interactive, and structural standards used
throughout Budget Brewer.

Its purpose is to help every screen feel like it belongs to the same app,
regardless of when it was created or who worked on it.

Following these standards improves:

- consistency;
- accessibility;
- maintainability;
- scalability;
- localization;
- responsive behavior.

The design system is the source of truth for user interface decisions. When an
existing screen conflicts with this document, the screen should generally be
updated unless there is a compelling reason to change the design system itself.

Responsive layout process lives in `docs/RESPONSIVE_STRATEGY.md`. Accessibility
process lives in `docs/ACCESSIBILITY.md`. This file focuses on visual,
component, and interaction standards.

## Design Philosophy

Budget Brewer is guided by five core principles.

### 1. Clarity

Financial information should be understandable at a glance.

Layouts should reduce cognitive load by presenting information in a logical,
organized manner.

Whenever possible:

- show relationships visually;
- group related information;
- avoid unnecessary decoration;
- prioritize readability over density.

### 2. Intentionality

Every visual element should serve a purpose.

If removing a component would not reduce usability or understanding, its
existence should be questioned.

Spacing, typography, color, icons, and animation should communicate meaning
rather than simply decorating the interface.

### 3. Consistency

Similar components should behave similarly.

Buttons should feel related to other buttons. Cards should use familiar spacing.
Dialogs should follow the same basic structure. Users should not have to relearn
the interface because they moved to another screen.

### 4. Accessibility

Accessibility is a fundamental requirement, not an optional enhancement.

Budget Brewer should remain usable across:

- different screen sizes;
- different orientations;
- different text scales;
- screen readers;
- multiple languages;
- various motor abilities.

Accessibility decisions should not be sacrificed purely for aesthetics.

### 5. Human-Centered Design

Budget Brewer exists to help people make informed financial decisions.

The interface should inspire confidence rather than anxiety. Interactions should
feel approachable, forgiving, and predictable.

The software should adapt to people, not require people to adapt to the
software.

## Responsive Design Philosophy

Budget Brewer is designed to adapt, not simply scale.

Different devices provide different opportunities. Responsive layouts should use
available space more effectively while preserving familiarity.

Examples include:

- reflowing content into multiple columns;
- displaying additional contextual information;
- increasing spacing where appropriate;
- improving readability on larger displays;
- simplifying layout when accessibility settings need a clearer path.

Layouts should not exist solely because a device is larger or smaller. A new
layout variant should only be created when the user experience is meaningfully
improved.

## Design Tokens

Budget Brewer is gradually moving toward a clearer token system.

The current resource files contain a mixture of reusable design tokens,
component-specific dimensions, and older dimensions that predate the current
design-system work.

Documentation should describe resources that actually exist. Proposed tokens
should not be presented as implemented until they are added to the project.

### Current Global Spacing Tokens

The current general-purpose spacing tokens are:

| Token      | Value | Purpose                                        |
|------------|-------|------------------------------------------------|
| `space_xs` | 4dp   | Tight spacing between closely related elements |
| `space_sm` | 8dp   | Standard spacing between related elements      |

The project also contains older spacing resources such as
`spacing_small` and `spacing_medium`. These should be reviewed before creating
new spacing tokens so duplicate meanings are not added unnecessarily.

### Current Screen Spacing

| Resource                    | Value | Purpose                           |
|-----------------------------|-------|-----------------------------------|
| `screen_horizontal_padding` | 16dp  | Default horizontal screen padding |
| `screen_vertical_padding`   | 16dp  | Default vertical screen padding   |

### Current Typography Resources

| Resource               | Value | Typical Usage                   |
|------------------------|-------|---------------------------------|
| `text_size_header`     | 24sp  | Screen or major section headers |
| `text_size_subheader`  | 20sp  | Card and subsection headers     |
| `text_size_dropdown`   | 16sp  | Dropdown text                   |
| `text_size_body`       | 14sp  | Standard body text              |
| `text_size_body_small` | 12sp  | Supporting text                 |
| `text_size_caption`    | 12sp  | Captions                        |
| `text_size_error`      | 12sp  | Error and validation text       |
| `text_size_calendar`   | 10sp  | Compact calendar text           |

Text roles should use named resources rather than hardcoded `sp` values.

### Current Touch Target Resource

```text
button_height = 48dp
```

This resource establishes the minimum height used by many buttons. Controls that
contain scalable text should generally use it as a minimum height rather than a
fixed height.

### Component-Specific Dimensions

The project includes purpose-specific resources for components such as:

- dashboard card padding and spacing;
- chart dimensions;
- chart legend spacing;
- navigation rail width;
- Home content insets;
- timeframe control spacing.

These are valid design resources even when they are not global tokens, because
their names describe their intended use.

### Corner Radius

Corner radii are currently defined through existing drawables and styles rather
than one complete shared radius-token system.

Do not document universal card, button, or dialog radius values until those
values have been verified across the relevant drawables or replaced with shared
dimension resources.

### Future Token Cleanup

A future design-token pass may:

- consolidate duplicate spacing resources;
- introduce missing general-purpose spacing levels;
- standardize corner-radius resources;
- confirm typography roles across all screens;
- replace hardcoded values that still exist in layouts or drawables.

Until that work is completed, the current Android resources remain the source of
truth.

### Cards

Cards should contain one coherent idea or task.

For dashboard-style cards:

- use a clear section title;
- keep the primary number or comparison easy to find;
- group related labels and values visually;
- provide a useful accessibility summary when child-by-child reading would be
  noisy;
- avoid nesting cards inside cards.

Component standards from the older UI notes belong here:

- card padding;
- corner radius;
- header spacing;
- divider spacing;
- content spacing;
- footer spacing.

### List Rows

Rows should be easy to scan and easy to activate.

Define repeated row behavior through shared styles and dimensions where
possible:

- row height or minimum height;
- leading icon size;
- trailing icon size;
- text hierarchy;
- row padding;
- grouped accessibility labels when the row should be read as one item.

### Dialogs

Dialogs should feel consistent across the app.

Standardize:

- width;
- title spacing;
- body spacing;
- button spacing;
- validation and error placement.

### Forms

Forms should be calm and predictable.

Standardize:

- label spacing;
- field spacing;
- button spacing;
- error spacing;
- keyboard behavior;
- required-field wording.

### Buttons

Buttons should remain readable and tappable when text or display size increases.

Use:

- shared component styles for repeated button types;
- minimum heights for touch targets;
- `wrap_content` height when labels may need to wrap;
- two-line labels when that is clearer than clipping;
- deterministic two-row layouts for small fixed button groups when one row is
  not enough.

Avoid:

- fixed heights that clip large text;
- shrinking text as the first response to accessibility scaling;
- duplicating stateful button sets unless that is clearly simpler and safer.
- mixing Material 3 widget styles with Material Components themes on the
  same control.

### Timeframe Controls

The Home timeframe selector is the current reference implementation.

See `RESPONSIVE_STRATEGY.md` for the rules governing when the control changes
between one-row and two-row layouts.

## Icons

Icons should:

- be visually simple;
- use a single color unless conveying meaning;
- maintain consistent optical sizing;
- follow the app's witchy aesthetic where appropriate;
- prioritize recognition over decoration.

Touch targets should remain 48dp even when icons render smaller.

If an icon is decorative, it should usually be hidden from TalkBack. If an icon
is interactive, it needs a clear accessible label.

## Data Visualizations

Charts are supplemental representations of financial data.

Every chart must have an equivalent textual representation that communicates the
same essential values and conclusions to users who cannot perceive or interact
with the chart.

### Accessibility

- Charts must not be the only source of important financial information.
- Essential values must also be provided through readable text.
- Legends must use text labels in addition to color.
- Color must never be the only method used to identify a category or status.
- Decorative chart elements should be excluded from the accessibility tree.
- The associated textual summary must remain accessible to screen readers.

### Sizing

Chart dimensions must use named dimension resources.

Chart sizes must not be hardcoded directly in layout files or Kotlin code.

Responsive resource overrides may adjust chart dimensions for:

- short landscape screens;
- tablets;
- large screens;
- other configurations where the default size harms usability.

### Runtime-Created Views

Dimensions assigned programmatically must be loaded from Android resources.

Raw integer values passed to layout parameters, margins, or padding represent
physical pixels and should not be used for interface spacing or sizing.

## Resource Organization

Budget Brewer follows Android's resource qualifier structure.

### Layouts

Current layout resource types include:

```text
layout/
layout-land/
layout-sw600dp/
```

New layout directories should only be introduced when existing layouts cannot
reasonably adapt through responsive design.

Avoid creating duplicate layouts solely to adjust small spacing differences.

### Values

Current value resource types include:

```text
values/
values-land/
values-night/
values-sw600dp/
values-sw600dp-land/
values-sw720dp/
values-sw720dp-land/
values-de/
values-es/
values-fil/
values-fr/
values-hi/
values-it/
values-ja/
values-pt/
```

The `values/` directory should contain default design tokens. Alternative values
directories should override only resources that genuinely differ for a specific
configuration or locale.

## Naming Conventions

Dimension names should describe purpose, not appearance.

Good current examples:

```text
space_xs
space_sm
text_size_body
text_size_header
button_height
dashboard_card_padding
```

Avoid ambiguous names:

```text
smallPadding
padding1
largeMargin
spacing2
```

## XML Guidelines

Whenever practical:

- use ViewBinding;
- avoid hardcoded dimensions;
- avoid hardcoded colors;
- avoid hardcoded strings;
- keep attribute ordering consistent;
- group related attributes together;
- use styles for repeated text appearances;
- prefer reusable resources over duplication.

## What Belongs Here vs Responsive Strategy

Keep these topics in this file:

- visual hierarchy;
- component standards;
- typography;
- spacing;
- touch targets;
- color and icon rules;
- chart presentation;
- localization concerns that affect component design.

Put these topics in `docs/RESPONSIVE_STRATEGY.md`:

- when to use alternate layouts;
- when to use resource qualifiers;
- how layouts change across orientation or screen size;
- how touch exploration changes layout;
- how to decide between measurement changes and structural changes;
- screen-by-screen responsive review rules.

## Design Review Checklist

Every screen should be reviewed for:

- 48dp touch targets;
- TalkBack labels and grouping;
- focus order;
- text scaling;
- color contrast;
- localization;
- landscape support;
- tablet support;
- chart alternatives when charts are present.

Accessibility should be considered complete only after the relevant checklist
items have been verified.