package ai.platon.pulsar.ql.context

import ai.platon.pulsar.common.config.MutableConfig
import ai.platon.pulsar.ql.SQLSession
import ai.platon.pulsar.ql.context.SQLContexts.createSession
import ai.platon.pulsar.ql.context.SQLContexts.shutdown
import ai.platon.pulsar.ql.session.BasicSQLSession
import ai.platon.pulsar.ql.session.GenericSQLSession
import ai.platon.pulsar.ql.session.StaticSQLSession
import ai.platon.pulsar.skeleton.PulsarSettings
import ai.platon.pulsar.skeleton.context.PulsarContexts
import ai.platon.pulsar.skeleton.context.support.TrivialContextDefaults
import ai.platon.pulsar.skeleton.session.AbstractPulsarSession
import ai.platon.pulsar.skeleton.session.BasicPulsarSession
import ai.platon.pulsar.skeleton.session.PulsarSession
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticApplicationContext

abstract class AbstractAgenticContext(
    applicationContext: AbstractApplicationContext
) : AbstractH2SQLContext(applicationContext), SQLContext {

    abstract override fun createSession(): AbstractPulsarSession

    abstract override fun createSession(settings: PulsarSettings): AbstractPulsarSession

    override fun getOrCreateSession(): AbstractPulsarSession =
        sessions.values.filterIsInstance<AbstractPulsarSession>().firstOrNull() ?: createSession()

    override fun getOrCreateSession(settings: PulsarSettings): AbstractPulsarSession {
        // TODO: consider changed settings, for example, REST-level sessionId requires associated PulsarSession
        return sessions.values.filterIsInstance<AbstractPulsarSession>().firstOrNull() ?: createSession()
    }
}

open class BasicAgenticContext(
    override val applicationContext: AbstractApplicationContext
) : AbstractH2SQLContext(applicationContext) {

    val initConfiguration = MutableConfig(true)

    /**
     * Create a [ai.platon.pulsar.ql.session.BasicSQLSession] that accepts a [AbstractApplicationContext].
     *
     * > **NOTE:** The session is not a SQLSession, use [execute], [executeQuery] to access [SQLSession].
     * */
    @Throws(Exception::class)
    override fun createSession(): BasicPulsarSession {
        val session = BasicPulsarSession(this, initConfiguration.toVolatileConfig())
        return session.also { sessions[it.id] = it }
    }

    override fun createSession(settings: PulsarSettings): BasicSQLSession {
        val session = BasicSQLSession(this, initConfiguration.toVolatileConfig())
        settings.label?.let { session.label = it }
        settings.overrideConfiguration(session.sessionConfig)
        return session.also { sessions[it.id] = it }
    }
}

open class GenericAgenticContext(
    override val applicationContext: GenericApplicationContext,
    autoRefresh: Boolean = false
) : AbstractH2SQLContext(applicationContext) {

    val initConfiguration = MutableConfig(true)

    /**
     * Create a [ai.platon.pulsar.ql.session.GenericSQLSession].
     *
     * > **NOTE:** The session is not a SQLSession, use [execute], [executeQuery] to access [SQLSession].
     * */
    @Throws(Exception::class)
    override fun createSession(): GenericSQLSession {
        val session = GenericSQLSession(this, initConfiguration.toVolatileConfig())
        return session.also { sessions[it.id] = it }
    }

    override fun createSession(settings: PulsarSettings): GenericSQLSession {
        val session = GenericSQLSession(this, initConfiguration.toVolatileConfig())
        settings.label?.let { session.label = it }
        settings.overrideConfiguration(session.sessionConfig)
        return session.also { sessions[it.id] = it }
    }

    init {
        if (autoRefresh) {
            applicationContext.refresh()
        }
//        System.err.println("WARNING: Initialized static application context, " +
//                "this context is designed for test purpose only. " +
//                "Use @Browser4AutoConfiguration in spring-boot application for full functionality in production")
    }
}

/**
 * Simple static agentic context, components might be incomplete or trivial, used for test only.
 * */
open class StaticAgenticContext(
    override val applicationContext: StaticApplicationContext = StaticApplicationContext(),
    autoRefresh: Boolean = false
) : GenericAgenticContext(applicationContext, false) {

    private val defaults by lazy { TrivialContextDefaults(this) }

    /**
     * The unmodified config
     * */
    override val configuration get() = defaults.configuration

    /**
     * Url normalizer
     * */
    override val urlNormalizer get() = defaults.urlNormalizer

    /**
     * The web db
     * */
    override val webDb get() = defaults.webDb

    /**
     * The global cache
     * */
    override val globalCacheFactory get() = defaults.globalCacheFactory

    /**
     * The fetch component
     * */
    override val fetchComponent get() = defaults.fetchComponent

    /**
     * The parse component
     * */
    override val parseComponent get() = defaults.parseComponent

    /**
     * The update component
     * */
    override val updateComponent get() = defaults.updateComponent

    /**
     * The load component
     * */
    override val loadComponent get() = defaults.loadComponent

    /**
     * Create a [ai.platon.pulsar.ql.session.StaticSQLSession].
     *
     * > **NOTE:** The session is not a SQLSession, use [execute], [executeQuery] to access [SQLSession].
     * */
    @Throws(Exception::class)
    override fun createSession(): StaticSQLSession {
        val session = StaticSQLSession(this, configuration.toVolatileConfig())
        return session.also { sessions[it.id] = it }
    }

    override fun createSession(settings: PulsarSettings): StaticSQLSession {
        val session = StaticSQLSession(this, configuration.toVolatileConfig())
        settings.label?.let { session.label = it }
        settings.overrideConfiguration(session.sessionConfig)
        return session.also { sessions[it.id] = it }
    }

    init {
        if (autoRefresh) {
            applicationContext.refresh()
        }
//        System.err.println("WARNING: Initialized static application context, " +
//                "this context is designed for test purpose only. " +
//                "Use @Browser4AutoConfiguration in spring-boot application for full functionality in production")
    }
}

open class AnnotationConfigAgenticContext(
    override val applicationContext: AnnotationConfigApplicationContext,
) : AbstractAgenticContext(applicationContext) {

    constructor(vararg componentClasses: Class<*>) : this(AnnotationConfigApplicationContext(*componentClasses))

    /**
     * Create a [BasicPulsarSession].
     *
     * > **NOTE:** The session is not a SQLSession, use [execute], [executeQuery] to access [SQLSession].
     * */
    @Throws(Exception::class)
    override fun createSession(): BasicSQLSession {
        val session = BasicSQLSession(this, configuration.toVolatileConfig())
        return session.also { sessions[it.id] = it }
    }

    override fun createSession(settings: PulsarSettings): BasicSQLSession {
        val session = BasicSQLSession(this, configuration.toVolatileConfig())
        settings.label?.let { session.label = it }
        settings.overrideConfiguration(session.sessionConfig)
        return session.also { sessions[it.id] = it }
    }
}

/**
 * Coordinates creation and lifecycle of agentic contexts and sessions.
 *
 * What an PulsarSession provides:
 * - Agentic/browser-based agents
 * - Full-featured `WebDriver`
 * - Capture of live web pages into a local `WebPage`
 * - Parsing a `WebPage` into a lightweight `Document`
 * - Event handlers across the WebPage lifecycle
 * - One-line scrapers & full crawler (fetching, parsing, scheduling, priorities, browser pool, plugins)
 * - Basic LLM support for interacting with pages or documents
 *
 * Notes:
 * - This object works with the global [PulsarContexts] to manage the active context and shutdown hooks.
 * - Use [createSession] / [getOrCreateSession] for convenient session bootstrap.
 */
@Suppress("unused")
object SQLContexts {
    /**
     * Create or return the active [ai.platon.pulsar.exp.context.SQLContext].
     * If no active context exists, a default classpath XML based context is created.
     *
     * @return The active or newly created [ai.platon.pulsar.exp.context.SQLContext].
     */
    @Synchronized
    fun create(): SQLContext {
        return create(StaticAgenticContext(autoRefresh = true))
    }

    @Synchronized
    fun getOrCreate(): SQLContext {
        return getActivatedContextOrNull() ?: create()
    }

    /**
     * Register and activate the given [context] as the global agentic context.
     *
     * @param context The [SQLContext] to activate.
     * @return The same [SQLContext] for call chaining.
     */
    @Synchronized
    fun create(context: SQLContext): SQLContext {
        return PulsarContexts.create(context) as SQLContext
    }

    @Synchronized
    fun getOrCreate(context: SQLContext): SQLContext {
        return getActivatedContextOrNull() ?: create(context)
    }

    /**
     * Create or reuse an [SQLContext] backed by a Spring [ApplicationContext].
     * If the current active context is a [ai.platon.pulsar.exp.context.GenericSQLContext] with the same application context,
     * it will be reused.
     *
     * @param applicationContext The Spring application context.
     * @return The active or newly created [SQLContext].
     */
    @Synchronized
    fun create(applicationContext: ApplicationContext): SQLContext {
        return when (applicationContext) {
            is AnnotationConfigApplicationContext -> create(AnnotationConfigAgenticContext(applicationContext))
            is StaticApplicationContext -> create(StaticAgenticContext(applicationContext))
            is GenericApplicationContext -> create(GenericAgenticContext(applicationContext))
            else -> create(BasicAgenticContext(applicationContext as AbstractApplicationContext))
        }
    }

    @Synchronized
    fun getOrCreate(applicationContext: ApplicationContext): SQLContext {
        val context = getActivatedContextOrNull()

        if ((context as? AbstractSQLContext)?.applicationContext == applicationContext) {
            return context
        }

        return create(applicationContext)
    }

    /**
     * Create a new [PulsarSession] with the provided [settings].
     *
     * @param settings The session creation settings.
     * @return A newly created [PulsarSession].
     */
    @Synchronized
    fun createSession(settings: PulsarSettings): PulsarSession = create().createSession(settings)

    /**
     * Block the current thread until the context shutdown is triggered.
     *
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    @Throws(InterruptedException::class)
    fun await() = PulsarContexts.await()

    /**
     * Trigger an orderly shutdown of the active context and related resources.
     */
    @Synchronized
    fun shutdown() = PulsarContexts.shutdown()

    /**
     * Close the context (alias of [shutdown]).
     */
    fun close() = shutdown()

    private fun getActivatedContextOrNull(): SQLContext? {
        val activated = PulsarContexts.activeContext
        if (activated is SQLContext && activated.isActive) {
            return activated
        }

        return null
    }
}
