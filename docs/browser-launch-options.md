# Browser Launch Options

How to customize the browser command line — the Chrome switches a browser process is started with
(`--lang`, `--disable-features`, `--proxy-server`, `--window-size`, ...).

> Rule of thumb: a Chrome process can only be configured **when it starts**. Launch options never
> reach a browser that is already running.

## The five levels

| Level | Mechanism | Scope | Where |
|-------|-----------|-------|-------|
| Config | `browser.launch.chrome.args` + dedicated keys | every browser this JVM launches | `application.properties`, system properties, ... |
| Context | `context.launchBrowser(browserId, extra)` | one browser owned by the context | `PulsarContext` |
| Manager | `browserManager.launchWithExtraOptions(browserId, extra)` | one browser owned by the manager | `BrowserManager` |
| Session | `session.createBoundDriver(extra)` | the browser of one session | `PulsarSession` |
| Full control | `browserManager.launch(browserId, launcherOptions, chromeOptions)` | one browser, verbatim command line | `BrowserManager` |

## 1. Configuration file (recommended default)

```properties
# application.properties / application-private.properties / ${PULSAR_DATA_HOME}/config/conf-enabled/*

# Free-form extra Chrome switches, exactly as written on the command line.
browser.launch.chrome.args=--disable-features=Translate --proxy-server="http=foopy:80;ftp=foopy2"
```

* Arguments are whitespace-separated; double quotes group an argument that contains whitespace.
* They are applied by `ChromeLauncher.applyConfiguredArguments()` on **every** launch path, so they
  also cover callers that use the launcher directly.
* They have the **lowest priority** — see [Priority rules](#2-priority-rules-read-this-first).

### Config sources (highest wins)

1. dynamic/in-memory overrides
2. Spring `Environment` (your Spring Boot `application.properties`/`application.yml`)
3. JVM system properties (`-Dbrowser.launch.chrome.args=...`)
4. OS environment variables
5. local config files: Spring Boot `application-private.properties` / `application.properties`,
   and files in `${PULSAR_DATA_HOME}/config/conf-enabled/`

### Dedicated keys

| Key | Chrome switch | Default |
|-----|---------------|---------|
| `browser.launch.chrome.args` | free-form | (empty) |
| `browser.launch.no.sandbox` | `--no-sandbox` | `true` |
| `browser.launch.window.position` | `--window-position` | `0,0` |
| `browser.display.mode` | `--headless` when `HEADLESS` | `GUI` |
| `browser.launch.user.agent` | `--user-agent` | (empty → derived) |
| `browser.launch.user.agent.stealth` | `--user-agent` (reduced UA) | `true` |
| `browser.launch.disable.gpu` | `--disable-gpu` | `false` |
| `browser.launch.hide.scrollbars` | `--hide-scrollbars` | `false` |
| `browser.launch.mute.audio` | `--mute-audio` | `false` |
| `browser.launch.page.load.strategy` | *(not emitted — Selenium capability)* | `none` |
| `browser.launch.throw.exception.on.script.error` | *(not emitted — Selenium capability)* | `true` |
| `browser.launch.supervisor.process` / `.args` | supervised display mode | (empty) |
| `browser.launch.runtime.enable` | *(CDP `Runtime.enable`, a bot-detection tell)* | `false` |
| `browser.launch.register.script.once` | *(script injection strategy)* | `true` |
| `browser.profile.mode` | which profile a session's browser uses | `DEFAULT` |
| `browser.max.active.tabs` | tabs per browser context | `8` |

The browser **binary** is chosen by the `chrome.path` system property (`-Dchrome.path=/opt/chrome/chrome`),
then by the built-in search paths.

## 2. Priority rules (read this first)

`ChromeOptions.toList()` resolves the final command line in this order:

1. **The program wins over the config file.** If a key is *effectively set by the program* (a value
   other than `null` / `false` / `0` / empty string), a `browser.launch.chrome.args` entry with the
   same key is **ignored**. This keeps session-forced flags safe.
2. Otherwise the configured argument takes effect; trivial placeholders the program emitted for
   that key are removed first, so **each key appears at most once**.
3. Among several configured arguments with the same key, the **last one wins**.
4. Programmatic `addArgument(key, value)` (an *additional argument*) overrides both the built-in
   fields and the raw arguments; `addArguments(...)` (a *raw argument*) behaves like the config file.

### Keys you cannot change through `browser.launch.chrome.args`

Forced by `BrowserSettings.createChromeOptions()` in the standard launch line:

`--headless` (headless mode), `--no-startup-window`, `--window-size`, `--window-position`,
`--disable-blink-features=AutomationControlled`, `--no-sandbox` (while
`browser.launch.no.sandbox=true`), `--user-agent` (when a user agent is set/derived).

Use the dedicated keys above, or the programmatic APIs below, to change them.

`--disable-gpu`, `--hide-scrollbars` and `--mute-audio` are deliberately **not** forced, so
`browser.launch.chrome.args` *can* set them.

## 3. Context / manager: standard launch line + extras

```kotlin
val context = PulsarContexts.create()

val browser = context.launchBrowser(
    BrowserId.createDefault(),
    ChromeOptions().addArguments("--lang=zh-CN"),
)
val driver = browser.newDriver()
```

or, at the manager level:

```kotlin
val browser = context.browserManager.launchWithExtraOptions(
    BrowserId.createRandomTemp(),
    ChromeOptions()
        .addArguments("--lang=zh-CN --disable-features=Translate") // raw: fills keys the program did not set
        .addArgument("window-size", "1280,900"),                   // additional: overrides the standard value
)
```

The browser still gets the whole standard launch line (headless, window size, user agent, the proxy
of the browser fingerprint, ...); your options are applied **on top of it** under the priority rules
above. The launched browser is owned by the context/manager and closed with it.

## 4. Session: a browser with a custom command line

```kotlin
val session = PulsarContexts.createSession()

val driver = session.createBoundDriver(ChromeOptions().addArguments("--lang=zh-CN"))
val page = session.load("https://example.com")
```

The browser is launched for the profile mode of the session (`browser.profile.mode`), and the
created driver is bound to the session. To be sure a **new** process is started (so the options
really apply), give the session its own profile:

```kotlin
val session = PulsarContexts.createSession(PulsarSettings(profileMode = BrowserProfileMode.TEMPORARY))
val driver = session.createBoundDriver(ChromeOptions().addArguments("--lang=zh-CN"))
```

## 5. Full control over the command line

When the standard launch line is in the way, pass the complete `ChromeOptions` yourself:

```kotlin
val settings = context.browserManager.settings

val browser = context.browserManager.launch(
    BrowserId.createRandomTemp(),
    LauncherOptions(settings),
    settings.createChromeOptions(emptyMap()).addArguments("--window-size=1280,900"), // verbatim
)
```

`launchOptions` is handed to the browser process exactly as given — nothing is added, so build every
switch you need (starting from `settings.createChromeOptions(emptyMap())` keeps the standard ones).

## 6. Extension points (application-wide)

* `BrowserSettings.createChromeOptions(generalOptions)` is `open` — subclass it and override, then
  hand the subclass to the factory (`AbstractBrowserFactory(conf, settings)`) or pass it per launch
  (`browserManager.launch(browserId, customSettings)`).
* `BrowserLauncher`, `BrowserFactory` and `AbstractBrowserFactory` are all open/abstract.
* Spring Boot: the `browserSettings`, `browserFactory` and `browserManager` beans are all
  `@ConditionalOnMissingBean` — declare your own bean with the same name to take over.

## 7. Not supported on purpose: per-load launch options

`LoadOptions` has no Chrome-args option. Browsers are pooled per `BrowserId` (profile +
fingerprint) and a Chrome process is configured only at start, so a per-load switch could not be
honoured reliably — it would either be silently ignored or force a brand new browser on every load.

When a call needs a different command line:

```kotlin
val browser = context.launchBrowser(BrowserId.createRandomTemp(), ChromeOptions().addArguments("--lang=zh-CN"))
val driver = browser.newDriver()
session.bindDriver(driver)          // or: session.open(url, driver) / session.load(url, driver, ...)
```

## 8. Troubleshooting

| Symptom | Cause |
|---------|-------|
| `browser.launch.chrome.args` seems ignored | The key is already *effectively set* by the program — see [priority rules](#2-priority-rules-read-this-first) |
| Extra options ignored on a session/context launch | A Chrome process is already running for that profile; the launcher attaches to it. Use a fresh profile |
| Options only take effect sometimes | The browser was reused from a driver pool; launch options only apply at process start |
| Chrome not found | Set `-Dchrome.path=/path/to/chrome` |

To see the command line the running browser actually got, open `chrome://version` in it.

## Source map

| Concern | Code |
|---------|------|
| Config keys | `CapabilityTypes` (`pulsar-common`) |
| `browser.launch.chrome.args` parsing | `BrowserSettings.chromeArguments`, `ChromeOptions.parseArguments` |
| Applying configured args on every launch | `ChromeLauncher.applyConfiguredArguments` |
| Standard launch line | `BrowserSettings.createChromeOptions` |
| Final command line + priority | `ChromeOptions.toList` |
| Extra-options launch | `BrowserFactory.launchWithExtraOptions`, `BrowserManager.launchWithExtraOptions` |
| Session driver | `PulsarSession.createBoundDriver(ChromeOptions)` |
