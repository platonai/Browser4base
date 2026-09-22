package ai.platon.pulsar.api

import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_DISABLE_GPU
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_HIDE_SCROLLBARS
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_MUTE_AUDIO
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_PAGE_LOAD_STRATEGY
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_REGISTER_SCRIPT_ONCE
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_RUNTIME_ENABLE
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_THROW_EXCEPTION_ON_SCRIPT_ERROR
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_USER_AGENT
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_USER_AGENT_STEALTH
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_WINDOW_POSITION
import ai.platon.pulsar.common.config.ImmutableConfig
import java.time.Duration

/**
 * Central place for all hard-coded browser launch configuration defaults.
 *
 * Every launch default scattered in the codebase (Chrome command-line argument defaults,
 * session-forced arguments, process launch logic, wait times, etc.) is defined here;
 * do not write such literals in business code anymore. Items that need to be overridden
 * by the config file are loaded once through [ChromeLaunchConfig.load].
 */
object ChromeDefaults {

    // ------------------------------------------------------------------
    // ChromeOptions built-in field defaults (referenced by Options.kt constructor params)
    // ------------------------------------------------------------------
    /** Default value of --proxy-server, null means not set */
    val PROXY_SERVER: String? = null
    const val HEADLESS = false
    const val INCOGNITO = false
    /**
     * `--disable-gpu` is **not** forced by default.
     *
     * Forcing it makes Chrome fall back to a software WebGL renderer, which is a strong
     * headless/VM tell on machines that do have a GPU. Leaving it `false` means the program does
     * not effectively set the key, so it can be enabled from configuration
     * (`browser.launch.disable.gpu` or `browser.launch.chrome.args`). See issue #11 section 6.
     */
    const val DISABLE_GPU = false
    /**
     * `--hide-scrollbars` is **not** forced by default: it makes
     * `window.innerWidth - document.documentElement.clientWidth == 0`, which the page can
     * measure. Enable it via `browser.launch.hide.scrollbars` when needed.
     */
    const val HIDE_SCROLLBARS = false
    const val REMOTE_DEBUGGING_PORT = 0
    const val NO_DEFAULT_BROWSER_CHECK = true
    const val NO_FIRST_RUN = true
    const val NO_STARTUP_WINDOW = true
    /**
     * `--mute-audio` is **not** forced by default: a browser that never plays audio is an
     * unusual configuration. Enable it via `browser.launch.mute.audio` when needed.
     */
    const val MUTE_AUDIO = false
    const val DISABLE_BACKGROUND_NETWORKING = true
    const val DISABLE_BACKGROUND_TIMER_THROTTLING = true
    const val DISABLE_CLIENT_SIDE_PHISHING_DETECTION = true
    const val DISABLE_DEFAULT_APPS = false
    const val DISABLE_EXTENSIONS = false
    const val DISABLE_HANG_MONITOR = true
    const val DISABLE_POPUP_BLOCKING = true
    const val DISABLE_PROMPT_ON_REPOST = true
    const val DISABLE_SYNC = true
    const val DISABLE_TRANSLATE = true
    const val DISABLE_GEOLOCATION = true
    const val DISABLE_BLINK_FEATURES = "AutomationControlled"
    const val METRICS_RECORDING_ONLY = true
    const val SAFEBROWSING_DISABLE_AUTO_UPDATE = true
    const val NO_SANDBOX = false
    const val IGNORE_CERTIFICATE_ERRORS = true
    const val REMOTE_ALLOW_ORIGINS = "*"

    // ------------------------------------------------------------------
    // Session-forced arguments (written by BrowserSettings.createChromeOptions)
    // ------------------------------------------------------------------
    /** Default value of --window-position, overridable by browser.launch.window.position */
    const val WINDOW_POSITION = "0,0"
    /** Page load strategy, overridable by browser.launch.page.load.strategy */
    const val PAGE_LOAD_STRATEGY = "none"
    /** --throwExceptionOnScriptError, overridable by browser.launch.throw.exception.on.script.error */
    const val THROW_EXCEPTION_ON_SCRIPT_ERROR = true

    // ------------------------------------------------------------------
    // Process launch logic (ChromeLauncher)
    // ------------------------------------------------------------------
    /** Arguments used to open the system default browser, all other arguments are replaced */
    val SYSTEM_DEFAULT_BROWSER_ARGS = listOf(
        "--remote-debugging-port=0",
        "--remote-allow-origins=*",
        "about:blank"
    )
    /** Max retry count when Chrome fails to start */
    const val LAUNCH_RETRY_COUNT = 5
    /** Retry interval in milliseconds */
    const val LAUNCH_RETRY_INTERVAL_MS = 3000L
    /** Expiry of temporary user data directories */
    val TEMPORARY_UDD_EXPIRY: Duration = Duration.ofMinutes(60)
    /** Min age of temporary user data directories kept during cleanup */
    val TEMP_UDD_KEEP_MIN_AGE: Duration = Duration.ofMinutes(2)
    /** Number of recent temporary user data directories to keep */
    const val RECENT_TEMP_UDD_TO_KEEP = 5
    /** Wait time for graceful destroy of the Chrome process */
    val GRACEFUL_DESTROY_WAIT_TIME: Duration = Duration.ofSeconds(5)

    // ------------------------------------------------------------------
    // Startup/shutdown wait times (LauncherOptions defaults)
    // ------------------------------------------------------------------
    /** Default startup wait time */
    val DEFAULT_STARTUP_WAIT_TIME: Duration = Duration.ofSeconds(60)
    /** Default shutdown wait time */
    val DEFAULT_SHUTDOWN_WAIT_TIME: Duration = Duration.ofSeconds(60)
    /** Wait time for threads to stop */
    val THREAD_JOIN_WAIT_TIME: Duration = Duration.ofSeconds(5)

    // ------------------------------------------------------------------
    // Others
    // ------------------------------------------------------------------
    /** Default screenshot quality (jpeg, range [0..100]) */
    const val SCREENSHOT_QUALITY = 50
    /** Default user agent at the CDP layer */
    const val DEFAULT_USER_AGENT = "Browser4 Agent/1.0"

    // ------------------------------------------------------------------
    // Stealth-related launch defaults (see issue #11)
    // ------------------------------------------------------------------
    /**
     * Replace the `HeadlessChrome/<version>` token at launch with a plain `Chrome/<major>.0.0.0`.
     *
     * See [ai.platon.pulsar.api.model.ReducedUserAgent] for why the launch switch, and not a CDP
     * override or a page-world patch, is the mechanism that covers every JavaScript scope.
     */
    const val USER_AGENT_STEALTH = true
    /**
     * Whether to issue `Runtime.enable` on every navigation. **Off by default.**
     *
     * `Runtime.enable` is the canonical CDP-leak signal used by bot detection — the signal
     * `rebrowser-patches` exists to remove — and the driver does not need it: execution context
     * ids come from `Page.createIsolatedWorld`, `Runtime.evaluate` works without it, and nothing
     * in the library consumes `Runtime.*` events (no console-callback, exception or
     * execution-context listeners). It is therefore simply not sent unless a caller opts back in
     * with `browser.launch.runtime.enable=true`, which is only needed when relying on
     * `Runtime.consoleAPICalled`, `Runtime.exceptionThrown` or `Runtime.executionContextCreated`.
     *
     * See issue #11 section 8.
     */
    const val RUNTIME_ENABLE = false
    /**
     * Whether to register the page-world script once per target instead of once per navigation.
     *
     * Registering per navigation leaves one permanent copy of the payload per navigation, each
     * re-running on every later document.
     */
    const val REGISTER_SCRIPT_ONCE = true
}

/**
 * The effective browser launch config, loaded once.
 *
 * Code defaults come from [ChromeDefaults]; configurable items are overridden by the
 * config file through [load], which should be called once before the browser launches
 * and reused afterwards, so config values are not read again and again.
 */
data class ChromeLaunchConfig(
    /** Value of --window-position, config key browser.launch.window.position */
    val windowPosition: String = ChromeDefaults.WINDOW_POSITION,
    /** Page load strategy, config key browser.launch.page.load.strategy */
    val pageLoadStrategy: String = ChromeDefaults.PAGE_LOAD_STRATEGY,
    /** Value of --throwExceptionOnScriptError, config key browser.launch.throw.exception.on.script.error */
    val throwExceptionOnScriptError: Boolean = ChromeDefaults.THROW_EXCEPTION_ON_SCRIPT_ERROR,
    /**
     * The --user-agent argument, config key browser.launch.user.agent.
     *
     * Empty means "not configured": a reduced user agent is derived from the installed Chrome
     * version when [userAgentStealth] is enabled.
     */
    val userAgent: String = "",
    /**
     * Whether to replace the headless token in the User-Agent at launch,
     * config key browser.launch.user.agent.stealth.
     */
    val userAgentStealth: Boolean = ChromeDefaults.USER_AGENT_STEALTH,
    /** Value of --disable-gpu, config key browser.launch.disable.gpu */
    val disableGpu: Boolean = ChromeDefaults.DISABLE_GPU,
    /** Value of --hide-scrollbars, config key browser.launch.hide.scrollbars */
    val hideScrollbars: Boolean = ChromeDefaults.HIDE_SCROLLBARS,
    /** Value of --mute-audio, config key browser.launch.mute.audio */
    val muteAudio: Boolean = ChromeDefaults.MUTE_AUDIO,
    /**
     * Whether to issue `Runtime.enable` on every navigation,
     * config key browser.launch.runtime.enable. **Off by default.**
     *
     * `Runtime.enable` is the canonical CDP-leak signal used by bot detection, and the driver
     * does not need it structurally: execution context ids come from `Page.createIsolatedWorld`,
     * `Runtime.evaluate` works without it, and no `Runtime.*` event is consumed anywhere in the
     * library. Opt back in only if you rely on `Runtime.consoleAPICalled`,
     * `Runtime.exceptionThrown` or `Runtime.executionContextCreated`.
     *
     * See issue #11 section 8.
     */
    val runtimeEnable: Boolean = ChromeDefaults.RUNTIME_ENABLE,
    /**
     * Whether to register the page-world script once per target instead of once per navigation,
     * config key browser.launch.register.script.once.
     *
     * Registering per navigation leaves one permanent copy of the (~140 KB) payload per
     * navigation, each re-running on every later document. See issue #11 section 4.
     */
    val registerScriptOnce: Boolean = ChromeDefaults.REGISTER_SCRIPT_ONCE,
) {
    companion object {
        /**
         * Load the launch config from the given config once; items not configured
         * fall back to the code defaults in [ChromeDefaults].
         *
         * @param config the config source, usually an [ImmutableConfig]
         * */
        fun load(config: ImmutableConfig): ChromeLaunchConfig = ChromeLaunchConfig(
            windowPosition = config.get(BROWSER_LAUNCH_WINDOW_POSITION)?.trim()?.ifEmpty { null }
                ?: ChromeDefaults.WINDOW_POSITION,
            pageLoadStrategy = config.get(BROWSER_LAUNCH_PAGE_LOAD_STRATEGY)?.trim()?.ifEmpty { null }
                ?: ChromeDefaults.PAGE_LOAD_STRATEGY,
            throwExceptionOnScriptError = config.getBoolean(
                BROWSER_LAUNCH_THROW_EXCEPTION_ON_SCRIPT_ERROR,
                ChromeDefaults.THROW_EXCEPTION_ON_SCRIPT_ERROR
            ),
            userAgent = config.get(BROWSER_LAUNCH_USER_AGENT)?.trim().orEmpty(),
            userAgentStealth = config.getBoolean(
                BROWSER_LAUNCH_USER_AGENT_STEALTH,
                ChromeDefaults.USER_AGENT_STEALTH
            ),
            disableGpu = config.getBoolean(BROWSER_LAUNCH_DISABLE_GPU, ChromeDefaults.DISABLE_GPU),
            hideScrollbars = config.getBoolean(BROWSER_LAUNCH_HIDE_SCROLLBARS, ChromeDefaults.HIDE_SCROLLBARS),
            muteAudio = config.getBoolean(BROWSER_LAUNCH_MUTE_AUDIO, ChromeDefaults.MUTE_AUDIO),
            runtimeEnable = config.getBoolean(BROWSER_LAUNCH_RUNTIME_ENABLE, ChromeDefaults.RUNTIME_ENABLE),
            registerScriptOnce = config.getBoolean(
                BROWSER_LAUNCH_REGISTER_SCRIPT_ONCE,
                ChromeDefaults.REGISTER_SCRIPT_ONCE
            ),
        )
    }
}
