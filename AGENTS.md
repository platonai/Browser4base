# AGENTS.md — AI Agent Guidance for Browser4

## Project Overview

**Browser4** (Maven artifact: `ai.platon.pulsar:pulsar`) is a lightning-fast, coroutine-safe browser engine for AI. It provides high-performance browser automation, data extraction (CSS/XPath/X-SQL/LLM/ML), and Chrome DevTools Protocol (CDP) control — all coroutine-safe. The codebase is primarily Kotlin (~93%) with some Java (~7%).

- **Repository**: https://github.com/platonai/Browser4-base
- **License**: Apache 2.0
- **Version**: 4.9.x (current branch)
- **Package namespace**: `ai.platon.pulsar`

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
| `./mvnw test -PrunITs` | Run integration tests (may need browser/Docker) |
| `./mvnw test -PrunE2Es` | Run end-to-end tests |
| `./mvnw install -DskipTests` | Build and install to local repo, skip tests |
| `./mvnw clean` | Clean build artifacts |
| `./mvnw dependency:tree -pl <module>` | Inspect dependency tree of a module |

### Build Profiles

- **Default** — compiles core modules only (no tests modules)
- **`tests-integration`** (activate with `-DrunITs=true`) — adds `pulsar-it-tests`
- **`tests-e2e`** (activate with `-DrunE2Es=true`) — adds `pulsar-e2e-tests`

### Important Build Notes

- Integration tests are categorized as Slow/Heavy and may require a running browser, AI services, or Docker.
- The `pulsar-parent` POM at version `4.5.0` is published separately — do not bump it casually.
- The root `pom.xml` `version` is the source of truth for the project version.

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
└── VERSION                      # Current version (4.9.0-SNAPSHOT)
```

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

## Code Conventions

- **Line endings**: LF (`\n`) for all source files (`.bat`/`.cmd`/`.ps1` use CRLF)
- **Charset**: UTF-8
- **Trailing whitespace**: Trimmed
- **Final newline**: Required at end of every file
- **Indentation**: Follow IntelliJ IDEA defaults for Kotlin/Java (4 spaces for Java, 4 for Kotlin)
- **Package**: `ai.platon.pulsar.<module>`
- **Naming**: Follow Kotlin idioms — camelCase for functions/properties, PascalCase for classes
- **When adding a new file**: Match the style of surrounding files in the same module

### Editing Kotlin Files

- Use the `ai.platon.pulsar` base package. Sub-packages mirror the module structure.
- Prefer Kotlin `data class` for DTOs/value objects.
- Use Kotlin extension functions for adding behavior to existing types.
- Keep CDP type mappings in `pulsar-browser/src/main/kotlin/ai/platon/pulsar/chrome/`.

### Editing Java Files

- Java is used mainly in `pulsar-common` for configuration constants (`CapabilityTypes`, `AppConstants`, `Params`).
- New configuration types should go in `pulsar-common/src/main/java/ai/platon/pulsar/common/config/`.
- Prefer Kotlin for all new code unless there's a specific reason for Java (e.g., interop with Java-first libraries).

## Testing

### Test Categories

| Category | Annotation | Characteristics |
|----------|-----------|----------------|
| Unit | (none) | Fast, no external deps, runs in default profile |
| Integration | `@Tag("integration")` | May need browser/Docker, run with `-DrunITs=true` |
| E2E | `@Tag("E2E")` | Full stack, run with `-DrunE2Es=true` |
| Heavy/Slow | Included by default in IT | Expected to take time |
| ManualOnly | Excluded group | Never run in CI |

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

## Key Dependencies

- **Chrome DevTools Protocol**: `ai.platon.cdt:cdt-kt` — Kotlin CDP bindings
- **Spring Boot**: For application wiring and auto-configuration
- **MongoDB**: Optional persistence backend
- **Hadoop**: Client libraries (recently added, with exclusions)
- **LLM**: Integration module in `pulsar-third/pulsar-llm`

## Common Workflows for Agents

### Adding a New Feature

1. Identify which module it belongs to (browser, DOM, persistence, QL, plugins, etc.)
2. Add the implementation in the appropriate `src/main/kotlin` directory
3. Add tests in the corresponding test module or `src/test/kotlin`
4. If it introduces new configuration, add constants to `pulsar-common`
5. If it needs a new dependency, add it to the module's `pom.xml`
6. Run `./mvnw test -pl <module>` to verify

### Adding a New Maven Module

1. Create the module directory with the standard layout (`src/main/kotlin`, `src/test/kotlin`, `pom.xml`)
2. Add the module to root `pom.xml` `<modules>` section (under the appropriate profile if it's test-only)
3. If the module should be part of default compilation, add it to the default `<modules>` list
4. Wire dependencies appropriately — use `pulsar-dependencies` for version management

### Fixing a Bug

1. Locate the relevant module by tracing from the public API inward
2. Write a failing test first (in the appropriate test module)
3. Fix the issue in the production code
4. Run `./mvnw test -pl <module>` to verify the fix
5. If the bug affects multiple modules, run the full `./mvnw test`

### Working with CDP (Chrome DevTools Protocol)

- CDP domain handlers live in `pulsar-browser/src/main/kotlin/ai/platon/pulsar/chrome/handler/`
- Protocol types are in `ai.platon.cdt.kt.protocol.types.*` (external dependency)
- The main entry point is `DirectChromeProtocol` which implements `BrowserProtocol`
- Event listeners are registered via `addEventListener` / `removeEventListener`

## Important Files

| File | Purpose |
|------|---------|
| `VERSION` | Current project version (single source of truth) |
| `ROOT.md` | Project root marker |
| `pom.xml` | Root aggregator POM with module list and profiles |
| `pulsar-dependencies/pom.xml` | Central dependency version management |
| `application.properties` | Default application configuration |
| `.editorconfig` | Code style rules |
| `.gitattributes` | Git line-ending and diff settings |
| `docs/` | QL function reference, load options, PowerDOM guide |

## Git Conventions

- **Main branch**: `main`
- **Current branch**: `4.9.x` (version-specific development)
- **Commit format**: Conventional commits — `feat:`, `fix:`, `chore:`, `test:`, `docs:`
- **Co-author commits** with: `Co-Authored-By: Claude <noreply@anthropic.com>`
- **Don't commit**: `settings.local.json`, `application-private.*`, `target/`, `.idea/` (all in `.gitignore`)
