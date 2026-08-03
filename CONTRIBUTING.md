# Contributing To Budget Brewer

Thank you for considering contributing to Budget Brewer.

Contributions can include bug fixes, accessibility improvements, documentation,
translations, tests, design-system improvements, or carefully scoped features.

Budget Brewer is still preparing for its first public production release, so
larger architectural changes should be discussed before implementation.

## Before Starting

Before writing code:

1. Search existing issues and pull requests to see whether the work is already
   being discussed.
2. Open an issue for significant features, architecture changes, or behavior
   changes.
3. Read the relevant project documentation:
  - `docs/ARCHITECTURE.md`
  - `docs/DESIGN_SYSTEM.md`
  - `docs/RESPONSIVE_STRATEGY.md`
  - `docs/ACCESSIBILITY.md`
  - `docs/TESTING.md`
4. Read `AGENTS.md` when using AI-assisted development tools.

Small typo fixes and straightforward documentation corrections usually do not
need a separate issue first.

## Set Up The Project

### 1. Fork The Repository

Use GitHub's **Fork** button to create a copy under your own GitHub account.

### 2. Clone Your Fork

```bash
git clone https://github.com/YOUR_USERNAME/BudgetBrewer.git
cd BudgetBrewer
```

### 3. Add The Original Repository As Upstream

```bash
git remote add upstream https://github.com/ataraxiagoddess/BudgetBrewer.git
```
Verify the remotes:
```bash
git remote -v
```

### 4. Open The Project

Open the repository in Android Studio and allow Gradle sync to complete.

Do not commit local machine configuration such as:

- `local.properties`;
- signing keys;
- API secrets;
- personal environment files.

## Create A Branch

Start from an up-to-date `main` branch:

```bash
git switch main
git fetch upstream
git pull --ff-only upstream main
```

Create a focused branch:

```bash
git switch -c type/short-description
```

Examples:

```text
fix/home-timeframe-layout
feat/spending-filter
docs/accessibility-guide
test/home-viewmodel
```

Keep each branch focused on one coherent change.

## Make The Change

While working:

- follow the existing XML/ViewBinding architecture;
- do not introduce Compose, Hilt, or a Navigation graph;
- use existing styles and resources where practical;
- add user-facing strings through Android resources;
- preserve accessibility and localization behavior;
- avoid unrelated cleanup in the same pull request.

For architectural expectations, see `docs/ARCHITECTURE.md`.

For UI work, see:

- `docs/DESIGN_SYSTEM.md`;
- `docs/RESPONSIVE_STRATEGY.md`;
- `docs/ACCESSIBILITY.md`.

## Test The Change

At minimum:

```bash
./gradlew assembleDebug
```

Run additional checks appropriate to the change.

For UI changes, test:

- normal and large text;
- increased display size;
- portrait and landscape where applicable;
- TalkBack where applicable;
- compact and larger layouts;
- light and dark themes where applicable;
- longer translated labels when practical;
- Logcat during the changed flow.

Use `docs/TESTING.md` for the full checklist.

Document screen-review results in `docs/SCREEN_REVIEWS.md` when the change is part
of a structured screen review.

## Commit The Change

Write commits that describe the purpose of the change.

Examples:

```text
fix(home): preserve scroll position during dashboard reflow
feat(spending): add transaction filtering
docs: clarify accessibility testing process
```

Commit by purpose rather than by file.

Before committing, review the staged changes:

```bash
git diff --staged
```

## Keep The Branch Updated

Before opening a pull request:

```bash
git fetch upstream
git rebase upstream/main
```

Resolve any conflicts, then run the relevant tests again.

If the branch has already been pushed, updating a rebased branch may require:

```bash
git push --force-with-lease
```

Use `--force-with-lease`, not plain `--force`.

## Push The Branch

```bash
git push -u origin your-branch-name
```

## Open A Pull Request

Open a pull request from your branch into the original repository's `main`
branch.

The pull request should explain:

- what changed;
- why it changed;
- how it was tested;
- which devices or emulators were used for UI changes;
- any known limitations;
- screenshots for visible UI changes;
- related issue numbers.

Keep the pull request focused. Unrelated changes may be requested in a separate
pull request.

## Pull Request Checklist

Before submitting:

- [ ] The project builds.
- [ ] The change matches the existing architecture.
- [ ] Relevant tests or manual checks were completed.
- [ ] Accessibility was reviewed when applicable.
- [ ] Responsive behavior was reviewed when applicable.
- [ ] New user-facing text uses string resources.
- [ ] Documentation was updated when behavior changed.
- [ ] No secrets or local configuration files were committed.
- [ ] The pull request description explains what changed and how it was tested.

## Reporting Bugs

When reporting a bug, include:

- what you expected;
- what actually happened;
- steps to reproduce it;
- Android version;
- device or emulator;
- app version or commit;
- screenshots or screen recordings when useful;
- relevant Logcat output with personal information removed.

## Suggesting Features

Feature requests should explain:

- the problem being solved;
- who benefits;
- the expected behavior;
- accessibility or responsive-layout considerations;
- whether the idea changes existing architecture or data behavior.

## Using AI-Assisted Tools

AI-assisted contributions are welcome, but contributors remain responsible for
understanding, reviewing, and testing their changes.

Generated code or documentation must still follow the current project
architecture, design system, and contribution requirements.