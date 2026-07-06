# LoadOptions Reference

**LoadOptions** controls every aspect of how Browser4 fetches, caches, validates, interacts with, and persists web pages. Options are expressed as CLI-style arguments (`-key value`) and parsed into a `LoadOptions` object by [JCommander](https://jcommander.org/).

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Option Reference](#option-reference)
   - [Identification](#identification)
   - [Expiration & Caching](#expiration--caching)
   - [Failure Handling & Retry](#failure-handling--retry)
   - [Quality Gates](#quality-gates)
   - [Portal Page & Outlink Extraction](#portal-page--outlink-extraction)
   - [Browser & Interaction Control](#browser--interaction-control)
   - [Item Page Options](#item-page-options)
   - [Persistence](#persistence)
   - [Miscellaneous](#miscellaneous)
3. [Time Duration Formats](#time-duration-formats)
4. [Usage Patterns](#usage-patterns)
   - [Creating Options](#creating-options)
   - [Loading Pages](#loading-pages)
   - [Merging & Overriding](#merging--overriding)
   - [Portal + Item Page Crawling](#portal--item-page-crawling)
   - [JSON Serialization](#json-serialization)
5. [Default Values](#default-values)
6. [Priority System](#priority-system)
7. [API Public Options](#api-public-options)

---

## Quick Start

```kotlin
val session = PulsarContexts.createSession()
val url = "https://www.amazon.com/dp/B08PP5MSVB"

// Fetch fresh if last fetch was more than 1 day ago
session.load(url, "-expires 1d")

// Force immediate refresh
session.load(url, "-refresh")

// Parse page content and store it
session.load(url, "-parse -storeContent")

// With quality gates: require at least 300KB and 10 images
session.load(url, "-requireSize 300000 -requireImages 10")
```

---

## Option Reference

### Identification

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-entity` | `-e`, `--entity` | `String` | `""` | Entity type of the page (e.g., article, product, hotel). Used for classifying and specialized processing. |
| `-label` | `-l`, `--label` | `String` | `""` | Logical group label for organizing tasks. |
| `-taskId` | `--task-id` | `String` | `""` | Unique task identifier for tracking individual operations. |
| `-taskTime` | `--task-time` | `Instant` | `Instant.EPOCH` | Timestamp to group related tasks into a batch. |
| `-deadline` | `--deadline` | `Instant` | `DateTimes.doomsday` | Absolute deadline; tasks past this time are immediately abandoned. |
| `-authToken` | `--auth-token` | `String` | `""` | Authentication token for accessing protected resources. |

```kotlin
// Label and task ID for tracking
session.load(url, "-label my-crawl -taskId abc-123")

// Deadline: abandon if not fetched by this time
session.load(url, "-deadline 2022-04-15T18:36:54.941Z")
```

### Expiration & Caching

Control when cached pages should be re-fetched from the internet.

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-expires` | `-i`, `-expire`, `--expire` | `Duration` | `DECADES` | Duration after which cached content is stale. |
| `-expireAt` | `--expire-at` | `Instant` | `DateTimes.doomsday` | Absolute timestamp after which content is stale. |
| `-refresh` | `--refresh` | `Boolean` | `false` | Force immediate re-fetch. Equivalent to `-ignoreFailure -i 0s` + reset retry counters. |

**Expiration logic** (from `isExpired`):

```
refresh ? true
       : expireAt in (prevFetchTime..now) ? true
       : now >= prevFetchTime + expires ? true
       : false
```

```kotlin
// Re-fetch if older than 10 seconds
session.load(url, "-expires 10s")
session.load(url, "-i 30m")   // 30 minutes
session.load(url, "-i 1h")    // 1 hour
session.load(url, "-i 7d")    // 7 days

// Absolute expiration
session.load(url, "-expireAt 2022-04-15T18:36:54.941Z")

// Nuclear option: force refresh, ignore all failures
session.load(url, "-refresh")
```

### Failure Handling & Retry

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-ignoreFailure` | `-ignF`, `--ignore-failure` | `Boolean` | `false` | Retry even if previous attempts failed. |
| `-nMaxRetry` | `-nmr`, `--n-max-retry` | `Int` | `3` | Max retries in the crawl loop before marking page as Gone. |
| `-nJitRetry` | `-njr`, `--n-jit-retry` | `Int` | `-1` | Max immediate retries on RETRY(1601) status code. |

```kotlin
// Retry even previously-failed pages
session.load(url, "-ignoreFailure -i 0s")

// Allow 5 crawl-loop retries before giving up
session.load(url, "-nMaxRetry 5")

// Retry immediately up to 2 times on transient failures
session.load(url, "-nJitRetry 2")

// Combined: full retry strategy
session.load(url, "-ignoreFailure -nMaxRetry 5 -nJitRetry 2")
```

### Quality Gates

Pages that don't meet quality thresholds are automatically re-fetched.

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-requireSize` | `-rs`, `--require-size` | `Int` | `0` | Minimum page size in bytes. |
| `-requireImages` | `-ri`, `--require-images` | `Int` | `0` | Minimum number of `<img>` elements. |
| `-requireAnchors` | `-ra`, `--require-anchors` | `Int` | `0` | Minimum number of `<a>` elements. |
| `-requireNotBlank` | `-rnb` | `String` | `""` | CSS selector whose text must be non-blank. |
| `-waitNonBlank` | `-wnb`, `--wait-non-blank` | `String` | `""` | CSS selector to wait for non-blank text before proceeding. |

```kotlin
// Require at least 300KB (helps detect bot-block pages)
session.load(url, "-requireSize 300000")

// Require at least 10 images (ensures images loaded)
session.load(url, "-requireImages 10")

// Require at least 100 links (ensures navigation loaded)
session.load(url, "-requireAnchors 100")

// Wait for product title to appear
session.load(url, "-waitNonBlank #productTitle")

// Require price element to have content, else re-fetch
session.load(url, "-requireNotBlank .price")

// Combining quality gates
session.load(url, "-requireSize 300000 -requireImages 10 -requireAnchors 50")
```

### Portal Page & Outlink Extraction

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-outLinkSelector` | `-ol`, `-outLink`, `-outlinkSelector`, `--outlink-selector` | `String` | `""` | CSS selector to extract links from portal pages. |
| `-outLinkPattern` | `-olp`, `--out-link-pattern` | `String` | `".+"` | Regex filter for extracted outlinks. |
| `-topLinks` | `-tl`, `--top-links` | `Int` | `20` | Max number of outlinks to follow. |

```kotlin
// Extract product links from a category page
session.load(url, "-outLink a[href~=product] -topLinks 10")

// Only follow links matching a pattern
session.load(url, "-outLink .item-list a -outLinkPattern /dp/")

// Full portal crawling
session.load(url, "-outLink .products a -topLinks 50 -outLinkPattern .+")
```

### Browser & Interaction Control

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-fetchMode` | `-fm`, `--fetch-mode` | `FetchMode` | `BROWSER` | Content fetch mechanism. |
| `-browser` | `-b`, `--browser` | `BrowserType` | `PULSAR_CHROME` | Browser engine to use. |
| `-autoScrollCount` | `-sc`, `-scrollCount`, `--scroll-count` | `Int` | from `InteractSettings` | Number of scroll-down actions after page load. |
| `-scrollInterval` | `-si`, `--scroll-interval` | `Duration` | from `InteractSettings` | Interval between scrolls. |
| `-scriptTimeout` | `-stt`, `--script-timeout` | `Duration` | from `InteractSettings` | Max time for injected JS execution. |
| `-pageLoadTimeout` | `-plt`, `--page-load-timeout` | `Duration` | from `InteractSettings` | Max time to wait for page load. |
| `-interactLevel` | `-ilv`, `--interact-level` | `InteractLevel` | `DEFAULT` | Interaction aggressiveness (higher = better data, slower). |
| `-iframe` | `-ifr`, `--iframe` | `Int` | `0` | Iframe index to focus on. (Beta) |
| `-isResource` | `-resource` | `Boolean` | `false` | Fetch as raw resource without browser rendering. |
| `-incognito` | `-ic`, `--incognito` | `Boolean` | `false` | Browser incognito mode. |

```kotlin
// Scroll 10 times with 2-second intervals
session.load(url, "-scrollCount 10 -scrollInterval 2s")

// Set interaction level (balances quality vs speed)
session.load(url, "-interactLevel MEDIUM")

// Increase page load timeout for slow pages
session.load(url, "-pageLoadTimeout 60s")

// Fetch as a plain resource (no browser rendering)
session.load("https://example.com/api/data.json", "-isResource")
```

### Item Page Options

These override the main options when processing **item/detail pages** (as opposed to portal/index pages). When transitioning from a portal page to its outlinked item pages, the item options are promoted to primary options via `itemOptions2MajorOptions()`.

| Option | Short | Type | Default | Mirrors |
|--------|-------|------|---------|---------|
| `-itemExpires` | `-ii`, `-itemExpire`, `--item-expires` | `Duration` | `DECADES` | `expires` |
| `-itemExpireAt` | `--item-expire-at` | `Instant` | `DateTimes.doomsday` | `expireAt` |
| `-itemScrollCount` | `-isc`, `--item-scroll-count` | `Int` | `autoScrollCount` | `autoScrollCount` |
| `-itemScrollInterval` | `-isi`, `--item-scroll-interval` | `Duration` | `scrollInterval` | `scrollInterval` |
| `-itemScriptTimeout` | `-ist`, `--item-script-timeout` | `Duration` | `scriptTimeout` | `scriptTimeout` |
| `-itemPageLoadTimeout` | `-iplt`, `--item-page-load-timeout` | `Duration` | `pageLoadTimeout` | `pageLoadTimeout` |
| `-itemWaitNonBlank` | `-iwnb`, `--item-wait-non-blank` | `String` | `""` | `waitNonBlank` |
| `-itemRequireNotBlank` | `-irnb`, `--item-require-not-blank` | `String` | `""` | `requireNotBlank` |
| `-itemRequireSize` | `-irs`, `--item-require-size` | `Int` | `0` | `requireSize` |
| `-itemRequireImages` | `-iri`, `--item-require-images` | `Int` | `0` | `requireImages` |
| `-itemRequireAnchors` | `-ira`, `--item-require-anchors` | `Int` | `0` | `requireAnchors` |
| `-itemBrowser` | `-ib`, `--item-browser` | `BrowserType` | `PULSAR_CHROME` | `browser` |

```kotlin
// Portal page expires in 1d; each product detail page expires in 7d
session.load(url, "-expires 1d -itemExpires 7d")

// Different quality gates for portal vs detail pages
session.load(url, "-requireSize 100000 -itemRequireSize 600000 -itemRequireImages 10")
```

### Persistence

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-persist` | `--persist` | `Boolean` | `true` | Persist fetched pages to storage. |
| `-storeContent` | `-sct`, `--store-content` | `Boolean` | `true` | Store page content (HTML) in database. |
| `-dropContent` | `--drop-content` | `Boolean` | `false` | Inverse of `storeContent`; drops page content from storage. |
| `-lazyFlush` | `--lazy-flush` | `Boolean` | `true` | Batch writes (true) vs immediate writes (false). |

```kotlin
// Store only metadata, not page HTML (saves storage)
session.load(url, "-dropContent")

// Explicit: store with content
session.load(url, "-storeContent")

// Persist immediately (no batching)
session.load(url, "-lazyFlush false")
```

### Miscellaneous

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `-parse` | `-ps`, `--parse` | `Boolean` | `false` | Parse page into FeaturedDocument after fetch. |
| `-priority` | `-p` | `Int` | `0` | Task execution priority (lower = higher priority). |
| `-readonly` | | `Boolean` | `false` | Non-destructive mode; no side effects on target page. |
| `-ignoreUrlQuery` | `--ignore-url-query` | `Boolean` | `false` | Strip query parameters from URLs. |
| `-noNorm` | `--no-link-normalizer` | `Boolean` | `false` | Disable URL normalization. |
| `-test` | `--test` | `Int` | `0` | Test mode verbosity level (0 = disabled). |
| `-version` | `-v`, `--version` | `String` | `"20220918"` | Load options format version. |

```kotlin
// Enable parsing (required for DOM operations)
session.load(url, "-parse")

// High priority task (lower number = higher priority)
session.load(url, "-priority -2000")

// Strip query params (treat ?a=1 and ?a=2 as same page)
session.load(url, "-ignoreUrlQuery")

// Disable URL normalization
session.load(url, "-noNorm")
```

---

## Time Duration Formats

LoadOptions supports **two** time duration formats:

### Hadoop-style (human-readable)

```
10s      → 10 seconds
5m       → 5 minutes
2h       → 2 hours
7d       → 7 days
500ms    → 500 milliseconds
```

### ISO-8601 Duration

```
PT30S     → 30 seconds
PT1H30M   → 1 hour 30 minutes
P1DT12H   → 1 day 12 hours
```

```kotlin
// All equivalent to "1 day"
session.load(url, "-expires 1d")
session.load(url, "-expires 24h")
session.load(url, "-expires 1440m")
session.load(url, "-expires PT24H")
```

---

## Usage Patterns

### Creating Options

```kotlin
// Parse from string
val options = LoadOptions.parse("-expires 1d -ignoreFailure -parse")

// From a session (injects session config + URL args)
val options = session.options("-expires 1d")

// Create empty, modify programmatically
val options = LoadOptions.createUnsafe().apply {
    expires = Duration.ofDays(1)
    ignoreFailure = true
    parse = true
}

// Copy existing options
val clone = options.clone()
```

### Loading Pages

```kotlin
val session = PulsarContexts.createSession()
val url = "https://www.amazon.com/dp/B08PP5MSVB"

// Inline options string
session.load(url, "-expires 1d -parse")

// Pre-built options object
val options = session.options("-expires 1d -parse")
session.load(url, options)

// Submit for background processing
session.submit(url, "-expires 1d -parse")

// Load portal page + all outlinked pages
session.loadOutPages(url, "-outLink a.product-link -topLinks 10 -itemExpires 7d")
```

### Merging & Overriding

Later arguments take precedence over earlier ones:

```kotlin
// Base options
val base = LoadOptions.parse("-expires 1d -parse -incognito")

// Override with new args
val merged = LoadOptions.merge(base, "-expires 7d")
// → expires = 7d, parse = true, incognito = true

// Merge two strings
val result = LoadOptions.merge("-expires 1d -parse", "-expires 7d -storeContent")
// → -expires 7d -parse -storeContent

// Normalize multiple args into one canonical string
val normalized = LoadOptions.normalize("-expires 7d", "-i 1s", "-parse")
// Later values win: expires = 1s

// Erase specific options from an args string
val cleaned = LoadOptions.eraseOptions("-incognito -expires 1s -ignoreFailure", "incognito", "expires")
// → "-erased -erased 1s -ignoreFailure"
```

### Portal + Item Page Crawling

The key pattern for e-commerce scraping:

```kotlin
val portalUrl = "https://www.example.com/category/electronics"

val pages = session.loadOutPages(portalUrl,
    "-expires 1d" +                    // Portal page: re-fetch after 1 day
    " -outLink a[href~=product]" +     // Extract product links
    " -topLinks 10" +                  // Max 10 products
    " -itemExpires 7d" +               // Product pages: re-fetch after 7 days
    " -itemRequireSize 600000" +       // Product pages: min 600KB
    " -itemRequireImages 5" +          // Product pages: min 5 images
    " -itemRequireAnchors 50"          // Product pages: min 50 links
)

// pages contains: portal page + 10 product detail pages
```

**How `itemOptions2MajorOptions()` works:**

When processing switches from portal → item:
1. Item options (`itemExpires`, `itemRequireSize`, etc.) are **copied** to the main options
2. Item options are **reset** to defaults (no further nesting)
3. `outLinkSelector` is cleared (item pages don't spawn more items)

```kotlin
// Programmatic approach
val options = LoadOptions.parse("-expires 1d -itemExpires 7d -itemRequireSize 600000")
val itemOptions = options.createItemOptions()
// itemOptions now has expires=7d, requireSize=600000
// and itemExpires/itemRequireSize reset to defaults
```

### JSON Serialization

```kotlin
// Convert to JSON
val options = LoadOptions.parse("-expires 1d -ignoreFailure -parse")
val json = LoadOptionsJson.toJson(options)
// → {"expires":"1d","ignoreFailure":true,"parse":true}

// Pretty-print
val pretty = LoadOptionsJson.toPrettyJson(options)

// Include all fields (including defaults)
val allJson = LoadOptionsJson.toJson(options, includeDefaults = true)

// Parse from JSON
val parsed = LoadOptionsJson.fromJson(json)

// Generate a template with all fields and defaults
val template = LoadOptionsJson.generateJsonTemplate()
```

**Reading options from a JSON config file:**

```kotlin
// config.json:
// {
//   "expires": "7d",
//   "parse": true,
//   "storeContent": false,
//   "ignoreFailure": true,
//   "requireSize": 500000
// }

val json = File("config.json").readText()
val options = LoadOptionsJson.fromJson(json)
session.load(url, options)
```

### Checking Options Programmatically

```kotlin
val options = LoadOptions.parse("-expires 1d -parse -ignoreFailure")

// Check individual values
println(options.expires)          // PT24H
println(options.parse)            // true
println(options.ignoreFailure)    // true

// Check if parser is engaged
options.parserEngaged()           // true (because parse=true)

// Check if a specific option is modified from default
options.isDefault("expires")      // false
options.isDefault("storeContent") // true

// Get only modified options
options.modifiedOptions           // Map<String, Any>
options.modifiedParams            // Params (formatted)

// String representation (canonical args)
println(options.toString())       // "-expires 1d -ignoreFailure -parse"

// Check expiration
val lastFetch = Instant.now().minus(2, ChronoUnit.DAYS)
options.isExpired(lastFetch)      // true (last fetch was 2 days ago, expires in 1d)

// Check deadline
options.isDead()                  // false (deadline is far in the future)
```

---

## Default Values

```kotlin
// From LoadOptionDefaults
expires       = ChronoUnit.DECADES.duration  // ~1000 years — effectively "never expire"
expireAt      = DateTimes.doomsday           // far-future sentinel value
lazyFlush     = true
parse         = false                        // parsing is opt-in
storeContent  = true
ignoreFailure = false
nJitRetry     = -1                           // disabled
browser       = BrowserType.PULSAR_CHROME
test          = 0

// In LoadOptions constructor
fetchMode         = FetchMode.BROWSER
autoScrollCount   = InteractSettings.DEFAULT.autoScrollCount
scrollInterval    = InteractSettings.DEFAULT.scrollInterval
scriptTimeout     = InteractSettings.DEFAULT.scriptTimeout
pageLoadTimeout   = InteractSettings.DEFAULT.pageLoadTimeout
interactLevel     = InteractLevel.DEFAULT
topLinks          = 20
outLinkPattern    = ".+"
nMaxRetry         = 3
priority          = 0
```

---

## Priority System

The priority value determines task execution order in the browser queue. **Lower values = higher priority** (consistent with `PriorityBlockingQueue`).

```kotlin
// Priority can be set in 4 ways (checked in this order):

// 1. In the URL itself
session.load("http://example.com -priority -2000")

// 2. In a Hyperlink's args
Hyperlink("http://example.com", "", args = "-priority -2000")

// 3. In LoadOptions
val options = LoadOptions.parse("-priority -2000")
session.load("http://example.com", options)

// 4. In a UrlAware
Hyperlink("http://example.com", "", priority = -2000)
```

---

## API Public Options

Options marked with `@ApiPublic` are exposed through REST APIs. These are the most commonly used options:

```
-e, -entity              → entity type
-l, -label               → task label
-taskId                  → task ID
-taskTime                → batch timestamp
-deadline                → task deadline
-authToken               → auth token
-readonly                → read-only mode
-resource                → resource fetch mode
-p, -priority            → task priority
-i, -expires             → cache expiry
-expireAt                → absolute expiry
-ol, -outLinkSelector    → outlink CSS selector
-olp, -outLinkPattern    → outlink regex filter
-tl, -topLinks           → max outlinks
-wnb, -waitNonBlank      → wait for non-blank selector
-rnb, -requireNotBlank   → require non-blank selector
-rs, -requireSize        → min page size
-ri, -requireImages      → min images
-ra, -requireAnchors     → min anchors
-ii, -itemExpires        → item page expiry
-itemExpireAt            → item page absolute expiry
-iwnb, -itemWaitNonBlank → item page wait selector
-irnb, -itemRequireNotBlank → item page require selector
-irs, -itemRequireSize   → item page min size
-iri, -itemRequireImages → item page min images
-ira, -itemRequireAnchors → item page min anchors
-refresh                 → force refresh
-ignF, -ignoreFailure    → ignore past failures
```

---

## Real-World Examples

### Example 1: Quick one-off scrape

```kotlin
// Fetch a page once, parse it, don't store content
val page = session.load(
    "https://example.com/product/123",
    "-refresh -parse -dropContent"
)
println(page.document.title)
```

### Example 2: Daily batch crawl with quality gates

```kotlin
val config = """
    -expires 1d
    -parse
    -storeContent
    -requireSize 300000
    -requireImages 10
    -ignoreFailure
    -nMaxRetry 5
    -pageLoadTimeout 45s
    -scrollCount 5
""".trimIndent().replace("\n", " ")

val page = session.load(url, config)
```

### Example 3: E-commerce category with product detail pages

```kotlin
val pages = session.loadOutPages(
    "https://www.example.com/category/electronics",
    "-expires 1d" +
    " -outLink a.product-title-link" +
    " -topLinks 20" +
    " -itemExpires 7d" +
    " -itemRequireSize 600000" +
    " -itemRequireImages 5" +
    " -itemPageLoadTimeout 30s" +
    " -itemScrollCount 10" +
    " -parse -storeContent"
)
// pages[0] = portal (category listing)
// pages[1..20] = product detail pages
```

### Example 4: API data fetch (no browser)

```kotlin
val apiData = session.load(
    "https://api.example.com/data.json",
    "-isResource -expires 1h -storeContent"
)
println(apiData.contentAsString)
```

### Example 5: Programmatic options building

```kotlin
val options = LoadOptions.createUnsafe().apply {
    expires = Duration.ofDays(7)
    parse = true
    storeContent = true
    ignoreFailure = true
    requireSize = 300_000
    requireImages = 10
    pageLoadTimeout = Duration.ofSeconds(60)
    autoScrollCount = 5
}

// Serialize for logging/monitoring
println(options.toString())
// → "-expires 7d -ignoreFailure -pageLoadTimeout 60s -parse -requireImages 10 -requireSize 300000 -scrollCount 5 -storeContent"

// Convert to JSON for API
val json = LoadOptionsJson.toJson(options)
```

### Example 6: Merging user-provided overrides

```kotlin
// System defaults
val defaults = LoadOptions.parse("-expires 1d -parse -storeContent -nMaxRetry 3")

// User-provided overrides (e.g., from API query params)
val userOverrides = "-expires 7d -nMaxRetry 5"

// Merge: user values win
val finalOptions = LoadOptions.merge(defaults, userOverrides)
// → -expires 7d -nMaxRetry 5 -parse -storeContent
```
