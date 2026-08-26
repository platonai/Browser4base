package ai.platon.pulsar.api

import ai.platon.pulsar.api.model.BrowserSettings

/**
 * The launch config
 * */
class LauncherOptions(
    val settings: BrowserSettings = BrowserSettings(),
    var supervisorProcess: String? = null,
    val supervisorProcessArgs: MutableList<String> = mutableListOf()
) {
    var startupWaitTime = ChromeDefaults.DEFAULT_STARTUP_WAIT_TIME
    var shutdownWaitTime = ChromeDefaults.DEFAULT_SHUTDOWN_WAIT_TIME
    var threadWaitTime = ChromeDefaults.THREAD_JOIN_WAIT_TIME

    companion object {
        /** Default startup wait time in seconds. */
        val DEFAULT_STARTUP_WAIT_TIME get() = ChromeDefaults.DEFAULT_STARTUP_WAIT_TIME

        /** Default shutdown wait time in seconds. */
        val DEFAULT_SHUTDOWN_WAIT_TIME get() = ChromeDefaults.DEFAULT_SHUTDOWN_WAIT_TIME

        /** 5 seconds wait time for threads to stop. */
        val THREAD_JOIN_WAIT_TIME get() = ChromeDefaults.THREAD_JOIN_WAIT_TIME
    }
}

/** Chrome argument */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class ChromeParameter(val value: String)

/**
 * The options to open chrome devtools, list of chrome command-line switches can be found in the below link:
 * http://peter.sh/experiments/chromium-command-line-switches/
 *
 * @property proxyServer The proxy server to use for the connection.
 * */
class ChromeOptions(
    /**
     * The proxy server to use for the connection.
     *
     * You can specify a custom proxy configuration in three ways:
     * By providing a semi-colon-separated mapping of list scheme to url/port pairs.
     * For example, you can specify:
     *
     * --proxy-server="http=foopy:80;ftp=foopy2"
     *
     * to use HTTP proxy "foopy:80" for http URLs and HTTP proxy "foopy2:80" for ftp URLs.
     *
     * By providing a single uri with optional port to use for all URLs.
     * For example:
     *
     * --proxy-server="foopy:8080"
     *
     * will use the proxy at foopy:8080 for all traffic.
     *
     * By using the special "direct://" value.
     * --proxy-server="direct://" will cause all connections to not use a proxy.
     *
     * @see <a href='https://www.chromium.org/developers/design-documents/network-settings/#command-line-options-for-proxy-settings'>
     *     Command-line options for proxy settings</a>
     * */
    @ChromeParameter("proxy-server")
    var proxyServer: String? = ChromeDefaults.PROXY_SERVER,
    @ChromeParameter("headless")
    var headless: Boolean = ChromeDefaults.HEADLESS,
    @ChromeParameter("incognito")
    var incognito: Boolean = ChromeDefaults.INCOGNITO,
    @ChromeParameter("disable-gpu")
    var disableGpu: Boolean = ChromeDefaults.DISABLE_GPU,
    @ChromeParameter("hide-scrollbars")
    var hideScrollbars: Boolean = ChromeDefaults.HIDE_SCROLLBARS,
    @ChromeParameter("remote-debugging-port")
    var remoteDebuggingPort: Int = ChromeDefaults.REMOTE_DEBUGGING_PORT,
    @ChromeParameter("no-default-browser-check")
    var noDefaultBrowserCheck: Boolean = ChromeDefaults.NO_DEFAULT_BROWSER_CHECK,
    @ChromeParameter("no-first-run")
    var noFirstRun: Boolean = ChromeDefaults.NO_FIRST_RUN,
    @ChromeParameter("no-startup-window")
    var noStartupWindow: Boolean = ChromeDefaults.NO_STARTUP_WINDOW,
    @ChromeParameter("mute-audio")
    var muteAudio: Boolean = ChromeDefaults.MUTE_AUDIO,
    @ChromeParameter("disable-background-networking")
    var disableBackgroundNetworking: Boolean = ChromeDefaults.DISABLE_BACKGROUND_NETWORKING,
    @ChromeParameter("disable-background-timer-throttling")
    var disableBackgroundTimerThrottling: Boolean = ChromeDefaults.DISABLE_BACKGROUND_TIMER_THROTTLING,
    @ChromeParameter("disable-client-side-phishing-detection")
    var disableClientSidePhishingDetection: Boolean = ChromeDefaults.DISABLE_CLIENT_SIDE_PHISHING_DETECTION,
    @ChromeParameter("disable-default-apps")
    var disableDefaultApps: Boolean = ChromeDefaults.DISABLE_DEFAULT_APPS,
    @ChromeParameter("disable-extensions")
    var disableExtensions: Boolean = ChromeDefaults.DISABLE_EXTENSIONS,
    @ChromeParameter("disable-hang-monitor")
    var disableHangMonitor: Boolean = ChromeDefaults.DISABLE_HANG_MONITOR,
    @ChromeParameter("disable-popup-blocking")
    var disablePopupBlocking: Boolean = ChromeDefaults.DISABLE_POPUP_BLOCKING,
    @ChromeParameter("disable-prompt-on-repost")
    var disablePromptOnRepost: Boolean = ChromeDefaults.DISABLE_PROMPT_ON_REPOST,
    @ChromeParameter("disable-sync")
    var disableSync: Boolean = ChromeDefaults.DISABLE_SYNC,
    @ChromeParameter("disable-translate")
    var disableTranslate: Boolean = ChromeDefaults.DISABLE_TRANSLATE,
    @ChromeParameter("disable-geolocation")
    var disableGeolocation: Boolean = ChromeDefaults.DISABLE_GEOLOCATION,
    @ChromeParameter("disable-blink-features")
    var disableBlinkFeatures: String = ChromeDefaults.DISABLE_BLINK_FEATURES,
    @ChromeParameter("metrics-recording-only")
    var metricsRecordingOnly: Boolean = ChromeDefaults.METRICS_RECORDING_ONLY,
    @ChromeParameter("safebrowsing-disable-auto-update")
    var safebrowsingDisableAutoUpdate: Boolean = ChromeDefaults.SAFEBROWSING_DISABLE_AUTO_UPDATE,
    @ChromeParameter("no-sandbox")
    var noSandbox: Boolean = ChromeDefaults.NO_SANDBOX,
    @ChromeParameter("ignore-certificate-errors")
    var ignoreCertificateErrors: Boolean = ChromeDefaults.IGNORE_CERTIFICATE_ERRORS,
    /**
     * The origin for DevTools Websocket connections must now be specified explicitly from Chrome 111.
     * @see [fluidsonic's pull](https://github.com/kklisura/chrome-devtools-java-client/pull/85)
     * @see [ChromeDriver 111.0.5563.19 unable to establish connection to chrome](https://groups.google.com/g/chromedriver-users/c/xL5-13_qGaA?pli=1)
     * */
    @ChromeParameter("remote-allow-origins")
    var remoteAllowOrigins: String = ChromeDefaults.REMOTE_ALLOW_ORIGINS
) {
    val additionalArguments: MutableMap<String, Any?> = mutableMapOf()

    /**
     * Extra arguments in raw command-line form, e.g. `--disable-features=Translate`.
     *
     * Unlike [additionalArguments], raw arguments are passed to Chrome exactly as written
     * (values like `false` are kept). They have the **lowest priority**: a raw argument
     * takes effect only when its key is not effectively set by the program (built-in
     * fields or [additionalArguments]) — see [toList] for the full priority rules.
     */
    val rawArguments: MutableList<String> = mutableListOf()

    /**
     * Add an argument.
     * */
    fun addArgument(key: String, value: String? = null): ChromeOptions {
        additionalArguments[key] = value
        return this
    }

    /**
     * Parse a command-line style argument string and add the arguments verbatim.
     *
     * The string is split on whitespace; double quotes group an argument that contains
     * whitespace. For example:
     *
     * ```
     * --disable-features=Translate --proxy-server="http=foopy:80;ftp=foopy2"
     * ```
     *
     * results in `--disable-features=Translate` and `--proxy-server=http=foopy:80;ftp=foopy2`.
     *
     * This is the entry point to load arguments from a configuration file. The added
     * arguments have the lowest priority — they take effect only for keys that are not
     * effectively set by the program, see [toList].
     * */
    fun addArguments(arguments: String): ChromeOptions {
        rawArguments.addAll(parseArguments(arguments))
        return this
    }

    /**
     * Add raw arguments verbatim.
     * */
    fun addArguments(arguments: Collection<String>): ChromeOptions {
        rawArguments.addAll(arguments)
        return this
    }

    /**
     * Add raw arguments verbatim.
     * */
    fun addArguments(vararg arguments: String): ChromeOptions {
        rawArguments.addAll(arguments)
        return this
    }

    /**
     * Remove an argument.
     * */
    fun removeArgument(key: String): ChromeOptions {
        additionalArguments.remove(key)
        return this
    }

    /**
     * Merge an arguments map to this
     * */
    fun merge(args: Map<String, Any?>) = args.forEach { (key, value) -> addArgument(key, value?.toString()) }

    /**
     * Convert all the arguments to a map.
     * */
    fun toMap(): Map<String, Any?> {
        val args = ChromeOptions::class.java.declaredFields
            .filter { it.annotations.any { it is ChromeParameter } }
            .onEach { it.isAccessible = true }
            .associateTo(LinkedHashMap()) { it.getAnnotation(ChromeParameter::class.java).value to it.get(this) }

        args.putAll(additionalArguments)

        return args
    }

    /**
     * Convert all the arguments to a list.
     *
     * Priority rules between the programmatic arguments (built-in fields and
     * [additionalArguments]) and the raw arguments ([rawArguments], e.g. loaded from
     * the config file `browser.launch.chrome.args`):
     *
     * 1. If a key is **effectively set by the program** (a value other than
     *    `null`/`false`/`0`/empty string), the raw argument with the same key is
     *    ignored — the program wins, so session/forced settings (headless, no-sandbox,
     *    window-size, ...) can not be overridden by the config file.
     * 2. Otherwise the raw argument takes effect; any trivial placeholder emitted by the
     *    program (e.g. `--remote-debugging-port=0`) is removed first, so each key appears
     *    **at most once** in the final command line.
     * 3. Among raw arguments with the same key, the last one wins.
     * */
    fun toList(): List<String> {
        val map = toMap()
        val result = toList(map).toMutableList()

        for (raw in rawArguments) {
            val key = argumentKey(raw) ?: continue
            if (hasEffectiveValue(map[key])) {
                // The key is effectively set by the program, the raw argument is ignored
                continue
            }
            result.removeAll { it == "--$key" || it.startsWith("--$key=") }
            result.add(raw)
        }

        return result
    }

    fun toList(args: Map<String, Any?>): List<String> {
        val result = ArrayList<String>()
        for ((key, value) in args) {
            if (value != null && false != value) {
                if (true == value) {
                    result.add("--$key")
                } else {
                    result.add("--$key=$value")
                }
            }
        }
        return result
    }

    override fun toString() = toList().joinToString(" ") { it }

    companion object {
        /**
         * Extract the key of a raw command-line argument, e.g.
         * `--disable-features=Translate` → `disable-features`, `--headless` → `headless`.
         * Returns null for a blank argument.
         * */
        private fun argumentKey(argument: String): String? {
            val trimmed = argument.trim()
            if (trimmed.isEmpty()) {
                return null
            }
            return trimmed.removePrefix("--").removePrefix("-").substringBefore("=")
        }

        /**
         * Check whether the value effectively enables a command-line argument.
         *
         * Trivial values (`null`, `false`, `0`, empty string) mean the key is not really
         * set by the program, so a raw argument with the same key can take effect.
         * */
        private fun hasEffectiveValue(value: Any?): Boolean = when (value) {
            null, false -> false
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotEmpty()
            else -> true
        }

        /**
         * Tokenize a command-line style argument string into a list of arguments.
         *
         * Arguments are separated by whitespace; double quotes group an argument that
         * contains whitespace and are stripped from the result. An unbalanced quote is
         * tolerated: the remaining text is treated as a single argument.
         *
         * Example:
         * ```
         * --proxy-server="http=foopy:80;ftp=foopy2" --disable-features=Translate,Autofill
         * ```
         * results in
         * ```
         * ["--proxy-server=http=foopy:80;ftp=foopy2", "--disable-features=Translate,Autofill"]
         * ```
         * */
        fun parseArguments(arguments: String): List<String> {
            if (arguments.isBlank()) {
                return emptyList()
            }

            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false

            for (ch in arguments) {
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    ch.isWhitespace() && !inQuotes -> {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.clear()
                        }
                    }

                    else -> current.append(ch)
                }
            }

            if (current.isNotEmpty()) {
                result.add(current.toString())
            }

            return result
        }
    }
}
