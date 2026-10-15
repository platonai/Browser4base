package ai.platon.pulsar.api

/**
 * Which JavaScript world a script evaluation runs in.
 *
 * One document has two JavaScript worlds:
 *
 * - [MAIN] — the page's own world. A script evaluated here sees the page's
 *   globals: `window.myApp`, a framework instance, a value a page script stored,
 *   everything a user means by "the page's JavaScript state". It is also the world
 *   page scripts can observe and tamper with.
 * - [ISOLATED] — the driver's own world for this document (CDP
 *   `Page.createIsolatedWorld`). Page scripts cannot see or modify anything in it,
 *   which is what makes the injected runtime helpers safe from a page's own
 *   `querySelector`/`fetch` overrides — and equally why a page-defined global is
 *   *not* visible there.
 *
 * The distinction matters to callers: evaluating `window.myApp` in the isolated
 * world silently reads `undefined` on a page where the value plainly exists.
 * [DEFAULT] is therefore [MAIN] — the world a caller asking to "evaluate in the
 * page" expects — while the driver's internal runtime keeps using [ISOLATED].
 */
enum class JsWorld {
    /** The page's own world; sees page-defined globals. */
    MAIN,

    /** The driver's isolated world; sees the injected runtime, not page globals. */
    ISOLATED,
    ;

    companion object {
        /**
         * The world a caller gets when none is requested: the page's own world,
         * because that is what "evaluate this in the page" means.
         */
        val DEFAULT: JsWorld = MAIN

        /** The accepted spellings, for error messages and `--help` text. */
        const val NAMES: String = "main, isolated"

        /**
         * Parse a user-supplied world name, tolerating the synonyms callers reach
         * for (`page`/`default` for the main world, `runtime` for the isolated one).
         *
         * @return the world, or null when the value is absent — invalid values are
         *   reported by the caller so the message can list [NAMES].
         */
        fun parse(value: String?): JsWorld? = when (value?.trim()?.lowercase()) {
            null, "" -> null
            "main", "page", "default", "window" -> MAIN
            "isolated", "isolate", "runtime", "extension" -> ISOLATED
            else -> null
        }
    }
}
