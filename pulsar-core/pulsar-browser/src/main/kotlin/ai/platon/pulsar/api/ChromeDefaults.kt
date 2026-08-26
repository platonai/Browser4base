package ai.platon.pulsar.api

import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_PAGE_LOAD_STRATEGY
import ai.platon.pulsar.common.config.CapabilityTypes.BROWSER_LAUNCH_THROW_EXCEPTION_ON_SCRIPT_ERROR
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
    const val DISABLE_GPU = true
    const val HIDE_SCROLLBARS = true
    const val REMOTE_DEBUGGING_PORT = 0
    const val NO_DEFAULT_BROWSER_CHECK = true
    const val NO_FIRST_RUN = true
    const val NO_STARTUP_WINDOW = true
    const val MUTE_AUDIO = true
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
        )
    }
}
