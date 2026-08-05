# TODO

This file is Budget Brewer's development inbox.

Its purpose is to capture bugs, UI polish, accessibility findings,
responsiveness improvements, and follow-up work discovered during development
without interrupting the current task.

Items should be concise and actionable.

## Guidelines

- Use this file for work that should eventually be completed.
- Keep entries specific enough that Future You immediately understands them.
- If an item grows into a larger feature, move it to `ROADMAP.md`.
- If an item represents an architectural improvement rather than immediate work,
  move it to `TECH_DEBT.md`.
- Remove completed items. Git history already records completed work.

---

## Finances

### Dialogs

- [ ] Reduce the width of the Add/Edit Tip dialog in landscape.
- [ ] Reduce the width of the Add/Edit Income dialog in landscape.
- [ ] Reduce the width of the Add/Edit Expense Category dialog in landscape.
- [ ] Reduce the width of the Add Savings/Spending dialog in landscape.

### Forms

- [ ] Make the Add/Edit Expense dialog scroll when content exceeds the available height.

### Lists

- [ ] Fix the recurring icon in `CategoryAdapter` not rendering next to the
  description text.

### Accessibility / Responsive

- [ ] Verify `layout-land/item_category.xml` touch targets and text rendering.

---

## Savings

### Dialogs

- [ ] Reduce the width of the Create/Edit Bucket dialog in landscape.
- [ ] Reduce the width of the Distribution dialog in landscape.

### Layout

- [ ] Improve the empty-state layout in landscape.
- [ ] Lower the FloatingActionButton position.

### Formatting

- [ ] Add thousands separators to bucket values.

---

## Spending

### Dialogs

- [ ] Reduce the width of the Add/Edit Transaction dialog in landscape.
- [ ] Make the Add/Edit Transaction dialog scroll when necessary.

### Layout

- [ ] Keep the spending and remaining-amount bubbles balanced when a large
  spending value needs additional space.

---

## Calendar

### Layout

- [ ] Wrap the calendar legend into two rows on smaller devices.
- [ ] Evaluate moving the legend to the left side of the page.

### Dialogs

- [ ] Reduce the width of the Edit Month Start Amount dialog in landscape.

---

## Settings

### Archived Buckets

- [ ] Convert `activity_archived_bucket.xml` to `ConstraintLayout`.
- [ ] Add scrolling support to `activity_archived_bucket.xml`.

---

## Exports

### PDF

- [ ] Localize all PDF text using Android string resources.
- [ ] Localize dates, currencies, and timestamps.
- [ ] Decide whether PDF exports should ignore app theme or support selectable export themes.
- [ ] Redesign the optional dark PDF theme if it remains supported.

### CSV

- [ ] Review CSV column naming for consistency.
- [ ] Review CSV organization for easier spreadsheet use.
- [ ] Include additional export metadata where appropriate.

---

## Authentication

### Layout

- [ ] Improve the landscape sign-in/sign-up layout by evaluating a two-column design.

---

## Navigation

- [ ] Fix bottom navigation position after switching between light and dark mode.
- [ ] Fix bottom navigation flashing during selection.

---

## Large-Screen Verification

- [ ] Verify `sw600dp/item_income_row.xml` text rendering.

---

## Engineering

- [ ] Audit Timber logging for release builds.
- [ ] Investigate the RecyclerView warning during Calendar page startup.

---

## Branch Candidates

### finances-polish

- Finances dialog sizing
- Expense dialog scrolling
- Category row polish

### savings-polish

- Bucket formatting
- Empty state
- FAB positioning
- Bucket dialogs

### calendar-polish

- Legend layout
- Month dialog sizing

### navigation-polish

- Theme transition
- Navigation animation

### export-polish

- PDF localization
- PDF theme behavior
- CSV naming and organization