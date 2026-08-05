# Technical Debt

This document tracks known engineering improvements that are not currently
blocking development.

Technical debt is not necessarily a bug. These items generally describe areas
where the implementation could become cleaner, more maintainable, or easier to
extend.

When a technical debt item becomes active work, create a branch and remove it
from this document once completed.

---

# Architecture

- Review `EncryptedDataStoreSettings` for long-term maintainability.
- Continue evaluating opportunities to reduce large Activities and Fragments.
- Continue separating business logic from UI where appropriate.

---

# UI System

- Continue replacing duplicated view styles with reusable theme resources.
- Continue standardizing spacing resources.
- Continue standardizing corner radius usage.
- Continue reviewing reusable dialog layouts.

---

# Exports

- Decouple PDF generation from the current application theme.
- Continue centralizing export formatting and localization.

---

# Logging

- Reduce unnecessary debug logging.
- Establish consistent rules for release-safe diagnostic logging.

---

# Build System

- Continue auditing project dependencies.
- Keep the version catalog clean and remove unused libraries promptly.

---

# Testing

- Introduce a unit testing foundation.
- Introduce an instrumentation testing foundation.
- Expand automated accessibility verification where practical.

---

# Documentation

- Keep documentation synchronized with implementation.
- Continue documenting significant architectural decisions.