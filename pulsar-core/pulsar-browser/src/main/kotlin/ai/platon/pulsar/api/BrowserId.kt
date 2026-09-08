package ai.platon.pulsar.api

import ai.platon.pulsar.api.model.ProfilePaths
import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.browser.BrowserType
import ai.platon.pulsar.common.browser.fingerprint.Fingerprint
import ai.platon.pulsar.common.proxy.ProxyEntry
import java.nio.file.Path

/**
 * The unique browser id.
 *
 * A BrowserId names ONE browser instance (or one binding to an external browser). It carries:
 * - the identity of the browser: its [contextDir] (or, for externally-attached browsers, the
 *   virtual context dir that encodes the caller-chosen external key) and its [fingerprint];
 * - a [createTime] "incarnation" marker: equality ignores it, but callers that hold a closed
 *   incarnation (e.g. a closed driver pool) treat a BrowserId with the same profile but a
 *   different createTime as a NEW incarnation of the same profile, so re-creation is allowed.
 *
 * ## Identity vs allocation
 *
 * Browser ids that allocate fresh state (a random temporary context dir, the next sequential
 * context dir, a fresh fingerprint file, or a runtime profile-mode re-configuration) MUST be
 * obtained through the explicit factory functions below ([createRandomTemp], [createNextSequential],
 * [createDefault], ...) — one call, one deliberate allocation. The companion object used to
 * expose such allocations as `val` properties ([RANDOM_TEMP], [NEXT_SEQUENTIAL], ...); those are
 * deprecated because every read silently allocated a fresh identity, which made code like
 * `PulsarBrowser(id = BrowserId.RANDOM_TEMP, ...)` look like a constant reference while it was
 * actually creating a new disposable browser identity on every evaluation.
 *
 * ## External (attached) browsers
 *
 * A browser that is NOT launched by this JVM — e.g. connected over CDP (`attach --cdp`) or
 * bridged through the Chrome extension WebSocket relay — must be named with [external]:
 * the factory builds a PURE, deterministic identity (same key => equal BrowserId, across
 * reconnects and restarts) that never allocates a directory, never writes a fingerprint file
 * and never touches the runtime profile-mode configuration. External ids are classified via
 * [isExternal] and rejected by the browser launch funnels — launch only makes sense for
 * browsers that this JVM starts.
 *
 * @property contextDir For a locally-launched browser: the directory that stores the browser's
 * data. For an external browser ([isExternal]): a purely virtual path derived from the
 * caller-chosen external key — it is never created on disk.
 * @property fingerprint The fingerprint to identify the browser.
 * */
data class BrowserId(
    val contextDir: Path,
    val fingerprint: Fingerprint
) : Comparable<BrowserId> {
    /**
     * The browser type of the browser.
     * */
    val browserType: BrowserType get() = fingerprint.browserType

    /**
     * The browser profile of the browser.
     * */
    val profile = BrowserProfile(contextDir, fingerprint)

    /**
     * The creation time of the browser.
     *
     * This is an "incarnation" marker rather than part of the identity: two BrowserIds are
     * equal iff their profiles are equal, and a BrowserId with the same profile but a newer
     * createTime represents a re-created incarnation of the same profile (e.g. a browser
     * relaunched on the same context dir after the previous one was closed).
     * */
    val createTime = System.currentTimeMillis()

    /**
     * True if this id names an externally-attached browser (CDP attach, Chrome extension
     * relay): [contextDir] is virtual, the browser owns its own user data dir on its own
     * machine, and the launch funnels reject such ids.
     * */
    val isExternal get() = profile.isExternal

    /**
     * The user data directory of the browser.
     *
     * For an external browser ([isExternal]) this is a virtual path as well — it must never
     * be passed to a launcher; the physical browser owns its data dir on its own machine.
     * */
    val userDataDir: Path
        get() = when {
            profile.isSystemDefault -> AppPaths.SYSTEM_DEFAULT_BROWSER_DATA_DIR_PLACEHOLDER
            profile.isPrototype -> ProfilePaths.PROTOTYPE_DATA_DIR
            else -> contextDir.resolve(browserType.name)
        }

    /**
     * A human-readable short display of the context.
     * For example,
     * 1. prototype
     * 2. 07171ChsOE207
     * 3. ext.<sessionId> (for an external browser, [isExternal])
     * */
    val display get() = contextDir.last().toString().substringAfter(ProfilePaths.CONTEXT_DIR_PREFIX)

    /**
     * The constructor of the browser id.
     *
     * @param profile The browser profile of the browser.
     * */
    constructor(profile: BrowserProfile) : this(profile.contextDir, profile.fingerprint)

    /**
     * The constructor of the browser id.
     *
     * @param contextDir The context directory of the browser.
     * @param browserType The browser type of the browser.
     * */
    constructor(contextDir: Path, browserType: BrowserType) : this(contextDir, Fingerprint(browserType))

    fun hasProxy() = fingerprint.hasProxy()
    fun setProxy(schema: String, hostPort: String, username: String?, password: String?) {
        fingerprint.setProxy(schema, hostPort, username, password)
    }

    fun setProxy(proxy: ProxyEntry) = fingerprint.setProxy(proxy)

    fun unsetProxy() = fingerprint.unsetProxy()

    override fun equals(other: Any?): Boolean {
        return other is BrowserId && other.profile == profile
    }

    override fun hashCode() = profile.hashCode()

    override fun compareTo(other: BrowserId) = profile.compareTo(other.profile)

    override fun toString(): String {
        return "{$fingerprint, $contextDir}"
    }

    companion object {
        /**
         * Represent the real user's default browser.
         *
         * Deprecated: this property allocates a NEW BrowserId instance on every access and
         * re-configures the runtime profile mode as a side effect, so it behaves like a
         * factory while looking like a constant. Call the explicit factory
         * [createSystemDefault] instead.
         * */
        @Deprecated(
            "BrowserId.SYSTEM_DEFAULT allocates a fresh browser id on every access (with profile-mode " +
                "configuration side effects). Call the explicit factory BrowserId.createSystemDefault() instead.",
            ReplaceWith("BrowserId.createSystemDefault()")
        )
        val SYSTEM_DEFAULT get() = createSystemDefault()

        /**
         * Represent the default browser.
         *
         * Deprecated for the same reason as [SYSTEM_DEFAULT]: every read allocates a fresh
         * id and re-configures the runtime profile mode. Use [createDefault] explicitly.
         * */
        @Deprecated(
            "BrowserId.DEFAULT allocates a fresh browser id on every access (with profile-mode " +
                "configuration side effects). Call the explicit factory BrowserId.createDefault() instead.",
            ReplaceWith("BrowserId.createDefault()")
        )
        val DEFAULT get() = createDefault()

        /**
         * Represent the prototype browser.
         *
         * Deprecated for the same reason as [SYSTEM_DEFAULT]: every read allocates a fresh
         * id and re-configures the runtime profile mode. Use [createPrototype] explicitly.
         * */
        @Deprecated(
            "BrowserId.PROTOTYPE allocates a fresh browser id on every access (with profile-mode " +
                "configuration side effects). Call the explicit factory BrowserId.createPrototype() instead.",
            ReplaceWith("BrowserId.createPrototype()")
        )
        val PROTOTYPE get() = createPrototype()

        /**
         * Represent a browser with a sequential context dir.
         *
         * Deprecated: every read ADVANCES the sequential context pool and allocates a new id,
         * so two reads are not the same id at all. Use [createNextSequential] explicitly.
         * */
        @Deprecated(
            "BrowserId.NEXT_SEQUENTIAL advances the sequential context pool and allocates a new " +
                "browser id on every access. Call the explicit factory BrowserId.createNextSequential() instead.",
            ReplaceWith("BrowserId.createNextSequential()")
        )
        val NEXT_SEQUENTIAL get() = createNextSequential()

        /**
         * Create a browser with random context dir.
         *
         * Deprecated: every read allocates a fresh random temporary context dir, so the value
         * is different on every access. Use [createRandomTemp] explicitly.
         * */
        @Deprecated(
            "BrowserId.RANDOM_TEMP allocates a new random temporary browser id on every access. " +
                "Call the explicit factory BrowserId.createRandomTemp() instead.",
            ReplaceWith("BrowserId.createRandomTemp()")
        )
        val RANDOM_TEMP get() = createRandomTemp()

        fun createDefault() = BrowserId(BrowserProfile.createDefault())

        fun createDefault(browserType: BrowserType) = BrowserId(BrowserProfile.createDefault(browserType))

        fun createSystemDefault() = BrowserId(BrowserProfile.createSystemDefault())

        fun createSystemDefault(browserType: BrowserType) = BrowserId(BrowserProfile.createSystemDefault(browserType))

        fun createPrototype() = BrowserId(BrowserProfile.createPrototype())

        fun createPrototype(browserType: BrowserType) = BrowserId(BrowserProfile.createPrototype(browserType))

        fun createRandomTemp() = BrowserId(BrowserProfile.createRandomTemp())

        fun createRandomTemp(browserType: BrowserType) = BrowserId(BrowserProfile.createRandomTemp(browserType))

        fun createNextSequential() = BrowserId(BrowserProfile.createNextSequential())

        fun createNextSequential(browserType: BrowserType) = BrowserId(BrowserProfile.createNextSequential(browserType))

        /**
         * Create the stable identity of an EXTERNAL browser — a browser that this JVM does not
         * launch, e.g. one attached over CDP (`attach --cdp`) or bridged through the Chrome
         * extension WebSocket relay.
         *
         * The returned id is
         * - deterministic: the same [externalKey] always yields an EQUAL BrowserId, also across
         *   reconnects and server restarts — so a session keeps naming its browser consistently;
         * - pure: no context directory is created or allocated, no fingerprint file is written,
         *   no runtime profile-mode configuration is touched — the physical browser owns its
         *   user data dir on its own machine;
         * - non-launchable: launch funnels reject ids with [isExternal] == true, use
         *   `connect`/attach flows instead.
         *
         * @param externalKey A stable, caller-chosen key that uniquely identifies the external
         * browser — typically the session id or the CDP endpoint of the attachment. Only
         * letters, digits, '_', '-', '.' and ':' are allowed (at most 64 chars).
         * @param browserType The browser type/channel of the external browser.
         * */
        fun external(externalKey: String, browserType: BrowserType = BrowserType.PULSAR_CHROME): BrowserId {
            val contextDir = ProfilePaths.externalContextDir(externalKey)
            // Deliberately pure: do NOT go through BrowserProfile.create() (which loads/writes a
            // fingerprint file) and do NOT touch BrowserSettings.withBrowserContextMode() (which
            // re-configures the runtime profile mode).
            return BrowserId(BrowserProfile(contextDir, Fingerprint(browserType)))
        }
    }
}
