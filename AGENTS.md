# AGENTS.md — AI Agent Guidance for Browser4

## Project Overview

**Browser4** (Maven artifact: `ai.platon.pulsar:pulsar`) is a lightning-fast, coroutine-safe browser engine for AI. It provides high-performance browser automation, data extraction (CSS/XPath/X-SQL/LLM/ML), and Chrome DevTools Protocol (CDP) control — all coroutine-safe. The codebase is primarily Kotlin (~93%) with some Java (~7%).

- **Repository**: https://github.com/platonai/Browser4base
- **License**: Apache 2.0
- **Version**: 4.9.x (current branch; see `VERSION` file)
- **Package namespace**: `ai.platon.pulsar`

---

## Quick-Start for Agents

Before touching any code, do these checks:

1. **Read `VERSION`** — the single source of truth for the project version. Don't bump it unless explicitly asked.
2. **Identify the target module** — scan the [Project Structure](#project-structure) to find which Maven module your change belongs in.
3. **Check if a plan is needed** — see [When to Plan vs. Execute](#when-to-plan-vs-execute).
4. **Run a fast compile** to confirm the environment works: `./mvnw compile -q`

---

## Build System

This is a **Maven** multi-module project. Always use the Maven wrapper:

```bash
./mvnw <goal>        # Linux/macOS/Git Bash
mvnw.cmd <goal>       # Windows CMD
```

### Common Build Commands

| Command | Purpose |
|---------|---------|
| `./mvnw compile` | Compile all default modules |
| `./mvnw test` | Run unit tests (fast, no browser/Docker) |
| `./mvnw test -DrunITs=true` | Run integration tests (may need browser/Docker) |
| `./mvnw test -DrunE2Es=true` | Run end-to-end tests |
| `./mvnw install -DskipTests` | Build and install to local repo, skip tests |
| `./mvnw clean` | Clean build artifacts |
| `./mvnw dependency:tree -pl <module>` | Inspect dependency tree of a module |
| `./mvnw test -pl <module> -Dtest=<TestClass>` | Run a single test class |

### Build Profiles

- **Default** — compiles core modules only (no test modules). Safe and fast for local dev.
- **`tests-integration`** (activate with `-DrunITs=true`) — adds `pulsar-it-tests`
- **`tests-e2e`** (activate with `-DrunE2Es=true`) — adds `pulsar-e2e-tests`

### Build Gotchas

- Integration tests are categorized as Slow/Heavy and may require a running browser, AI services, or Docker. Don't run them casually.
- The `pulsar-parent` POM at version `4.5.0` is **published separately** — do not bump it casually. It's a stable parent, not a regular module.
- The root `pom.xml` `<version>` is the **source of truth** for the project version. The `VERSION` file mirrors it.
- Maven profile activation uses **properties** (`-DrunITs=true`), not `-P` flags.
- The first build may download many dependencies; subsequent builds are fast.

---

## Project Structure

```
browser4base/
├── pom.xml                      # Root POM (aggregator)
├── pulsar-parent/               # Parent POM (published, rarely changes)
├── pulsar-dependencies/         # Dependency BOM / version management
├── pulsar-bom/                  # Bill of Materials for consumers
├── pulsar-core/                 # Core library
│   ├── pulsar-common/           #   Config, options, utilities, constants
│   ├── pulsar-browser/          #   Browser abstraction & CDP control (Kotlin)
│   ├── pulsar-dom/              #   DOM manipulation & snapshot parsing
│   ├── pulsar-skeleton/         #   Public API surface
│   ├── pulsar-ql/               #   X-SQL query execution engine
│   ├── pulsar-ql-common/        #   Shared QL types & functions
│   ├── pulsar-plugins/          #   Plugin system
│   │   ├── pulsar-protocol/     #     Network protocol handlers
│   │   └── pulsar-parse/        #     HTML/content parsers
│   ├── pulsar-persist/          #   Local/FS persistence layer
│   ├── pulsar-persist-mongo/    #   MongoDB persistence (optional)
│   ├── pulsar-third/pulsar-llm/ #   LLM integration
│   ├── pulsar-resources/        #   Bundled resources
│   └── pulsar-core-tests/       #   Core test suites
│       ├── pulsar-common-tests/
│       ├── pulsar-dom-tests/
│       └── pulsar-ql-tests/
├── pulsar-spring-support/       # Spring Boot integration
│   ├── pulsar-beans/            #   Spring bean configs
│   └── pulsar-boot/             #   Auto-configuration & starter
├── pulsar-tests/                # Integration & E2E tests
│   ├── pulsar-tests-common/     #   Shared test utilities
│   ├── pulsar-it-tests/         #   Integration tests
│   └── pulsar-e2e-tests/        #   End-to-end tests
├── pulsar-all/                  # Aggregate/shaded JAR
├── examples/                    # Usage examples
│   └── pulsar-examples/
├── docs/                        # Documentation
├── bin/                         # CLI scripts & tools
├── coworker/                    # Scheduled task data
├── .github/workflows/           # CI (ci.yml, nightly.yml, release.yml)
├── mvnw / mvnw.cmd              # Maven wrapper
└── VERSION                      # Current version (e.g. 4.9.0-SNAPSHOT)
```

### Module Selection Guide

| If your change involves... | Work in module... |
|---------------------------|-------------------|
| Browser control, CDP, tabs, windows | `pulsar-core/pulsar-browser` |
| DOM parsing, CSS/XPath selectors, page snapshots | `pulsar-core/pulsar-dom` |
| X-SQL queries, DOM functions | `pulsar-core/pulsar-ql` |
| Query types and shared functions | `pulsar-core/pulsar-ql-common` |
| Configuration constants, options | `pulsar-core/pulsar-common` |
| File/FS persistence | `pulsar-core/pulsar-persist` |
| MongoDB persistence | `pulsar-core/pulsar-persist-mongo` |
| Network protocol handling | `pulsar-core/pulsar-plugins/pulsar-protocol` |
| HTML/content parsing | `pulsar-core/pulsar-plugins/pulsar-parse` |
| LLM integration | `pulsar-core/pulsar-third/pulsar-llm` |
| Spring Boot wiring, auto-config | `pulsar-spring-support/` |
| Plugin system | `pulsar-core/pulsar-plugins` |
| Public API / entry points | `pulsar-core/pulsar-skeleton` |

---

## Architecture

### Layered Architecture

1. **Browser Layer** (`pulsar-browser`) — Kotlin coroutine-safe abstractions over CDP. `Browser`, `WebDriver`, CDP domain handlers (`DirectChromeProtocol`). The entry point for low-level browser control.

2. **DOM Layer** (`pulsar-dom`) — Page snapshots, document parsing, CSS/XPath selection. Operates on captured DOM — not live browser state — for performance.

3. **Persistence Layer** (`pulsar-persist`, `pulsar-persist-mongo`) — Stores pages, documents, and extraction results. FS-based by default; MongoDB optionally.

4. **Query Layer** (`pulsar-ql`) — X-SQL, an SQL-like query language extended with DOM functions (`dom_first_text`, `llm_extract`, etc.).

5. **Plugin Layer** (`pulsar-plugins`) — Protocol handlers and content parsers as pluggable components.

6. **Spring Integration** (`pulsar-spring-support`) — Spring Boot auto-configuration, bean definitions, starters.

### Key Patterns

- **Coroutine safety**: All browser operations are designed to be safe across Kotlin coroutines. Blocking calls are avoided.
- **Session-based state**: `AgenticContexts` manages per-operation sessions that own browser drivers, companion agents, and page state.
- **Snapshot-then-parse**: Pages are captured to in-memory documents (`session.capture()` → `session.parse()`), then selectors/extractors run against the snapshot — not against the live DOM. This enables offline, reproducible extraction.
- **Event-driven browsing**: `eventHandlers.browseEventHandlers` provides lifecycle hooks (onWillNavigate, onPageLoad, etc.) for intercepting and modifying browser behavior.

### Architecture Rules

- **Don't bypass the layers.** If you're adding browser logic, it goes through the Browser layer — don't reach into CDP directly from higher layers.
- **Don't operate on live DOM.** Always capture first, then parse. The snapshot-then-parse pattern is fundamental to Browser4's performance model.
- **Configuration constants go in `pulsar-common`**, not scattered across modules. Use existing constant classes (`CapabilityTypes`, `Params`, `AppConstants`) or add new ones in `pulsar-common/src/main/java/ai/platon/pulsar/common/config/`.

---

## Code Conventions

- **Line endings**: LF (`\n`) for all source files (`.bat`/`.cmd`/`.ps1` use CRLF)
- **Charset**: UTF-8
- **Trailing whitespace**: Trimmed
- **Final newline**: Required at end of every file
- **Indentation**: Follow IntelliJ IDEA defaults for Kotlin/Java (4 spaces for Java, 4 for Kotlin)
- **Package**: `ai.platon.pulsar.<module>`
- **Naming**: Follow Kotlin idioms — camelCase for functions/properties, PascalCase for classes
- **When adding a new file**: Match the style of surrounding files in the same module

### Kotlin Files

- Use the `ai.platon.pulsar` base package. Sub-packages mirror the module structure.
- Prefer Kotlin `data class` for DTOs/value objects.
- Use Kotlin extension functions for adding behavior to existing types.
- Keep CDP type mappings in `pulsar-browser/src/main/kotlin/ai/platon/pulsar/chrome/`.
- Use `@DisplayName` on classes and methods for readable test output.

### Java Files

- Java is used mainly in `pulsar-common` for configuration constants (`CapabilityTypes`, `AppConstants`, `Params`).
- New configuration types should go in `pulsar-common/src/main/java/ai/platon/pulsar/common/config/`.
- **Prefer Kotlin for all new code** unless there's a specific reason for Java (e.g., interop with Java-first libraries).

---

## Testing

### Test Categories

| Category | Annotation | Characteristics | Runs in CI? |
|----------|-----------|----------------|-------------|
| Unit | (none) | Fast, no external deps, runs in default profile | Yes, every push |
| Integration | `@Tag("integration")` | May need browser/Docker, run with `-DrunITs=true` | On PR / main |
| E2E | `@Tag("E2E")` | Full stack, run with `-DrunE2Es=true` | Scheduled |
| Heavy/Slow | Included by default in IT | Expected to take time | With IT |
| ManualOnly | Excluded group | Never run in CI | No |

### Running Tests

```bash
# Unit tests only (fast, safe for local dev)
./mvnw test

# Single module tests
./mvnw test -pl pulsar-core/pulsar-common

# Single test class
./mvnw test -pl pulsar-core/pulsar-ql -Dtest=QueryEngineTest

# Integration tests
./mvnw test -DrunITs=true

# Skip tests for faster builds
./mvnw install -DskipTests
```

### Test Modules

- `pulsar-core-tests/pulsar-common-tests` — common utility tests
- `pulsar-core-tests/pulsar-dom-tests` — DOM/parsing tests
- `pulsar-core-tests/pulsar-ql-tests` — X-SQL query tests
- `pulsar-tests/pulsar-it-tests` — integration tests
- `pulsar-tests/pulsar-e2e-tests` — end-to-end tests

### Test Naming Conventions

- **Files:** Unit: `<ClassName>Test.kt` | Integration: `<ClassName>IT.kt` | E2E: `<ClassName>E2ETest.kt`
- **Methods: Use camelCase, NOT backtick naming**
    - ✅ `fun testUserLoginWithValidCredentials()` + `@DisplayName("test user login with valid credentials")`
    - ❌ `` fun `test user login with valid credentials`() ``

### Test Expectations

- **Unit tests must pass without any external services** (no browser, no Docker, no network). If you write a test that needs these, it's an integration test — annotate it with `@Tag("integration")`.
- **Always run unit tests after making changes** — at minimum for the module you touched: `./mvnw test -pl <module>`.
- **If your change touches multiple modules**, run `./mvnw test` to catch cross-module regressions.

---

## Dependency Management

Browser4 uses a centralized dependency management approach:

- **`pulsar-dependencies/pom.xml`** — The central BOM that declares ALL dependency versions. This is where version numbers live.
- **`pulsar-bom/pom.xml`** — A consumable BOM for downstream projects that depend on Browser4.
- **Module POMs** — Declare only `groupId:artifactId` (no version) for managed dependencies. Versions are inherited from `pulsar-dependencies`.

### Adding a New Dependency

1. Add the version property to `pulsar-dependencies/pom.xml` (if it doesn't exist)
2. Add the `<dependency>` to the appropriate module's `pom.xml` — **without a version** (it inherits from the BOM)
3. If the dependency brings transitive bloat, add `<exclusions>` aggressively (see existing Hadoop dependency for the pattern)
4. Run `./mvnw dependency:tree -pl <module>` to verify the dependency graph

### Key Dependencies

- **Chrome DevTools Protocol**: `ai.platon.cdt:cdt-kt` — Kotlin CDP bindings
- **Spring Boot**: For application wiring and auto-configuration
- **MongoDB**: Optional persistence backend
- **Hadoop**: Client libraries (with aggressive exclusions to minimize transitive bloat)
- **LLM**: Integration module in `pulsar-third/pulsar-llm`

---

## Important Files

| File | Purpose | When to touch |
|------|---------|---------------|
| `VERSION` | Current project version (single source of truth) | Only when explicitly bumping the version |
| `ROOT.md` | Project root marker | Never |
| `pom.xml` | Root aggregator POM with module list and profiles | When adding/removing modules |
| `pulsar-dependencies/pom.xml` | Central dependency version management | When adding/updating dependencies |
| `pulsar-parent/pom.xml` | Published parent POM (v4.5.0, stable) | Rarely — it's separately published |
| `application.properties` | Default application configuration | When adding new config keys |
| `.editorconfig` | Code style rules (EOL, charset, whitespace) | Never (standard config) |
| `.gitattributes` | Git line-ending and diff settings | When adding file types |
| `docs/` | QL function reference, load options, PowerDOM guide | When adding/updating features |

---

## When to Plan vs. Execute

### Skip planning, just execute:
- Single-file fixes (typos, obvious bugs, small tweaks)
- Adding a single function/constant with clear requirements
- Editing content that follows an existing template
- Tasks where the user gives detailed, step-by-step instructions

### Use plan mode for:
- New features that span multiple modules
- Adding a new Maven module
- Changes to public API (`pulsar-skeleton`)
- Dependency additions or upgrades
- Refactoring that touches >3 files
- Any change where you're unsure which module to work in
- Changes to the build system (POM files, profiles)

---

## Common Workflows

### Adding a New Feature

1. Identify which module it belongs to (use the [Module Selection Guide](#module-selection-guide))
2. Add the implementation in the appropriate `src/main/kotlin` directory
3. Add unit tests in `src/test/kotlin` within the same module
4. If it introduces new configuration, add constants to `pulsar-common`
5. If it needs a new dependency, follow the [Dependency Management](#dependency-management) steps
6. Run `./mvnw test -pl <module>` to verify
7. If it's a user-facing feature, consider adding an example in `examples/pulsar-examples/`

### Adding a New Maven Module

1. Create the module directory with the standard layout (`src/main/kotlin`, `src/test/kotlin`, `pom.xml`)
2. Add the module to root `pom.xml` `<modules>` section under the appropriate profile:
   - Default profile: core/library modules
   - `tests-integration` profile: integration test modules
   - `tests-e2e` profile: E2E test modules
3. Wire dependencies — use `pulsar-dependencies` as the BOM
4. Run `./mvnw compile` to verify the reactor picks up the new module

### Fixing a Bug

1. Locate the relevant module by tracing from the public API inward
2. **Write a failing test first** in the appropriate test directory
3. Fix the issue in the production code
4. Run `./mvnw test -pl <module>` to verify the fix
5. If the bug affects multiple modules, run `./mvnw test`
6. Confirm the new test passes and no existing tests regress

### Working with CDP (Chrome DevTools Protocol)

- CDP domain handlers live in `pulsar-browser/src/main/kotlin/ai/platon/pulsar/chrome/handler/`
- Protocol types are in `ai.platon.cdt.kt.protocol.types.*` (external dependency — don't modify these)
- The main entry point is `DirectChromeProtocol` which implements `BrowserProtocol`
- Event listeners are registered via `addEventListener` / `removeEventListener`

### Making a PR

1. Branch from `main` (or the current version branch like `4.9.x`)
2. Make changes, commit with [conventional commits](#git-conventions)
3. Run `./mvnw test` to confirm nothing is broken
4. Push and open a PR against the base branch
5. CI runs `ci.yml` on push — check the results

---

## Verification Checklist (After Changes)

Before considering any change complete:

- [ ] `./mvnw compile` passes with no errors
- [ ] `./mvnw test -pl <touched-module>` passes all unit tests
- [ ] If touching multiple modules: `./mvnw test` passes
- [ ] Test names use camelCase (not backtick names)
- [ ] New public API has `@DisplayName` annotations on test classes
- [ ] No version was added to module-level dependency declarations (versions live in `pulsar-dependencies`)
- [ ] Files end with a final newline, use LF line endings, no trailing whitespace
- [ ] `pulsar-parent` version was NOT bumped (unless explicitly required)
- [ ] New config constants are in `pulsar-common`, not scattered

---

## CI/CD

CI workflows live in `.github/workflows/`:

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci.yml` | Push/PR to main, 4.9.x | Compile, unit tests, integration tests |
| `nightly.yml` | Scheduled (daily) | Full build including E2E tests |
| `release.yml` | Manual / tag | Build, sign, publish to Maven Central |

---

## Git Conventions

- **Main branch**: `main`
- **Current branch**: `4.9.x` (version-specific development)
- **Commit format**: Conventional commits — `feat:`, `fix:`, `chore:`, `test:`, `docs:`
- **Co-author commits** with: `Co-Authored-By: Claude <noreply@anthropic.com>`
- **Don't commit**: `settings.local.json`, `application-private.*`, `target/`, `.idea/` (all in `.gitignore`)

---

## Troubleshooting

### Build fails with "Could not resolve dependencies"
- Ensure you're using the Maven wrapper (`./mvnw`) — it pins the Maven version.
- Run `./mvnw clean compile` to force a fresh resolve.
- Check that `pulsar-dependencies` module compiled first: `./mvnw compile -pl pulsar-dependencies`.

### Test fails but the logic looks correct
- Check if the test is an integration test that needs `-DrunITs=true` — running with just `./mvnw test` won't execute it.
- Check if the test needs external services (browser, Docker, AI service). Unit tests shouldn't.
- Look for `@Tag("integration")` or `@Tag("E2E")` annotations — these are excluded from the default profile.

### "Unresolved reference" in IDE but Maven compiles fine
- This project uses `pulsar-dependencies` as a BOM with managed versions. Refresh the Maven project in your IDE.
- The `pulsar-parent` POM is published separately; ensure your local Maven repo has it: `./mvnw install -pl pulsar-parent -DskipTests`.

### Adding a module but it doesn't compile
- Verify the module is listed in root `pom.xml` `<modules>` under the correct profile.
- Check the module's `pom.xml` has `<parent>` pointing to `pulsar-parent`.
- Run `./mvnw compile -pl <new-module>` to isolate the issue.
