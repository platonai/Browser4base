package ai.platon.pulsar.skeleton.common.options

import ai.platon.pulsar.common.config.Parameterized
import ai.platon.pulsar.common.options.OptionUtils
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Pattern
import kotlin.system.exitProcess

/**
 * Created by Vincent on 17-4-12.
 * Copyright @ 2013-2023 Platon AI. All rights reserved
 */
open class PulsarOptions(
    /**
     * The argument vector
     * */
    val argv: Array<String>
) : Parameterized {
    protected val logger: Logger = LoggerFactory.getLogger(PulsarOptions::class.java)

    init { normalize(argv) }

    var expandAtSign = true
    var acceptUnknownOptions = true
    var allowParameterOverwriting = true
    // arguments
    val args: String get() = argv.joinToString(DEFAULT_DELIMETER)
    private val registeredObjects: MutableSet<Any> = HashSet()
    protected lateinit var jc: JCommander

    open var isHelp: Boolean = false

    /**
     * True if the last [parse] call had to recover from a malformed argument vector, i.e. at
     * least one option was dropped. Callers must not assume that every requested option took
     * effect when this is true.
     * */
    var hasParseError: Boolean = false
        protected set

    init { addObjects(this) }

    constructor(): this(arrayOf())

    constructor(args: String): this(split(args.trim()))

    constructor(argv: Map<String, String>)
            : this(argv.entries.joinToString(DEFAULT_DELIMETER) { it.key + DEFAULT_DELIMETER + it.value })

    fun setObjects(vararg objects: Any) {
        this.registeredObjects.clear()
        addObjects(objects)
    }

    fun addObjects(vararg objects: Any) {
        objects.toCollection(this.registeredObjects)
    }

    /**
     * Parse the argument vector.
     *
     * A malformed argument vector never degrades silently:
     * 1. the failure and the offending arguments are logged at WARN/ERROR,
     * 2. the malformed option is dropped and the remaining options are parsed again, so that
     *    the options following the malformed one still take effect,
     * 3. [hasParseError] is set so callers can detect the situation programmatically.
     *
     * @return true if the options are applied (possibly after dropping a malformed one),
     *         false if the whole argument vector had to be discarded
     * */
    open fun parse(): Boolean {
        hasParseError = false

        var attempt = argv
        var dropped = 0

        while (true) {
            try {
                doParse(attempt)

                if (dropped > 0) {
                    hasParseError = true
                    logger.warn(
                        "Recovered from a malformed argument vector, {} malformed argument(s) dropped" +
                                " | effective args: {}", dropped, attempt.toList()
                    )
                }

                return true
            } catch (e: ParameterException) {
                logger.warn("Failed to parse options | args: {} | {}", attempt.toList(), e.message)

                val offender = missingValueOption(e)
                if (offender == null || offender !in attempt || dropped >= MAX_DROPPED_ARGS) {
                    hasParseError = true
                    logger.error(
                        "Giving up parsing options, the argument vector is discarded as a whole | args: {}",
                        argv.toList(), e
                    )
                    return false
                }

                ++dropped
                // JCommander stops at the first failure, so without this retry every option
                // after the malformed one would be silently lost.
                logger.warn(
                    "Dropping malformed option '{}' which has no value, the remaining options are kept",
                    offender
                )
                attempt = attempt.filterNot { it == offender }.toTypedArray()
            } catch (e: Throwable) {
                hasParseError = true
                logger.warn("Failed to parse options, all options are discarded | args: {}", attempt.toList(), e)
                return false
            }
        }
    }

    open fun parseOrExit() {
        parseOrExit(mutableSetOf())
    }

    protected open fun parseOrExit(objects: Set<Any>) {
        try {
            addObjects(objects)
            doParse()

            if (isHelp) {
                jc.usage()
                exitProcess(0)
            }
        } catch (e: ParameterException) {
            println(e.toString())
            exitProcess(0)
        }
    }

    private fun doParse(args: Array<String> = argv) {
        registeredObjects.add(this)

        jc = JCommander.newBuilder()
                .acceptUnknownOptions(acceptUnknownOptions)
                .allowParameterOverwriting(allowParameterOverwriting)
                .expandAtSign(expandAtSign).build()
        registeredObjects.forEach { jc.addObject(it) }

        if (args.isNotEmpty()) {
            val prepared = prepareArgs(args)
            validateArgs(prepared)
            jc.parse(*prepared)
        }
    }

    /**
     * Prepare the raw argument vector for JCommander: the surrounding quotes kept by [split] are
     * removed, so that `-requireNotBlank '#id'` really sets `#id` instead of `'#id'`, and
     * `-outLink '#main a'` keeps the space instead of being truncated at the space.
     * */
    private fun prepareArgs(args: Array<String>): Array<String> {
        return Array(args.size) { unquote(args[it]) }
    }

    /**
     * Validate the argument vector before it is handed to JCommander.
     *
     * An implementation can throw a [ParameterException] to reject a malformed argument vector.
     * [parse] then logs the problem, drops the offending option and parses the remaining options
     * again, so the options after the malformed one are never lost.
     *
     * The default implementation accepts everything; a subclass that knows the arity of its
     * options should override it, see `LoadOptions.validateArgs`.
     * */
    protected open fun validateArgs(args: Array<String>) {
    }

    /**
     * Remove the surrounding quotes of a value, both `'` and `"` are supported.
     * An unbalanced quote is left untouched, the value is never dropped.
     * */
    private fun unquote(token: String): String {
        if (token.length >= 2) {
            val quote = token.first()
            if ((quote == '"' || quote == '\'') && token.last() == quote) {
                return token.substring(1, token.length - 1)
            }
        }

        return token
    }

    /**
     * Extract the option JCommander complained about, e.g.
     * `Expected a value after parameter -requireNotBlank` -> `-requireNotBlank`.
     * */
    private fun missingValueOption(e: ParameterException): String? {
        val message = e.message ?: return null
        val matcher = MISSING_VALUE_PATTERN.matcher(message)
        return if (matcher.find()) matcher.group(1) else null
    }

    open fun usage() {
        jc.usage()
    }

    open fun toCmdLine(): String {
        return params.withKVDelimiter(" ").formatAsLine()
                .replace("\\s+".toRegex(), " ")
    }

    open fun toArgsMap(): Map<String, String> {
        return params.asStringMap()
    }

    open fun toMutableArgsMap(): MutableMap<String, String> {
        return params.asStringMap()
    }

    open fun toArgv(): Array<String> {
        return params.withKVDelimiter(" ").formatAsLine()
                .split("\\s+".toRegex())
                .toTypedArray()
    }

    override fun hashCode(): Int {
        return args.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        return other is PulsarOptions && args == other.args
    }

    override fun toString(): String {
        return args
    }

    companion object {
        const val DEFAULT_DELIMETER = " "

        /**
         * The split pattern of a command line: a double quoted value, a single quoted value,
         * or a run of non whitespace characters. The quotes are kept in the token and are
         * removed right before the value is handed to JCommander, see [prepareArgs].
         * */
        val CMD_SPLIT_PATTERN = Pattern.compile("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^']*'|\\S+")

        private val MISSING_VALUE_PATTERN = Pattern.compile("Expected a value after parameter\\s+(\\S+)")

        /**
         * The maximum number of malformed arguments dropped in one [parse] call.
         * */
        private const val MAX_DROPPED_ARGS = 8

        /**
         * Normalize the raw arguments, convert old version args to current version
         *
         * The separators ([seps]) are replaced by spaces, but a separator inside a quoted value
         * is part of the value and is kept as is, otherwise `-requireNotBlank '#a, #b'` would be
         * truncated to `'#a`.
         * */
        @JvmOverloads
        fun normalize(args: String, seps: String = ","): String {
            var args1 = replaceSeparatorsOutsideQuotes(args, seps)
            // in old version, -cacheContent has arity 0, but current version is 1, we need a convert
            args1 = OptionUtils.arity0ToArity1(args1, "-cacheContent")
            args1 = OptionUtils.arity0ToArity1(args1, "-storeContent")

            return args1
        }

        /**
         * Replace every separator in [seps] with a space, except the separators inside a quoted
         * value. A quote opens a quoted value only at the beginning of a token, so an apostrophe
         * in the middle of a word (e.g. `don't`) stays a normal character.
         * */
        private fun replaceSeparatorsOutsideQuotes(args: String, seps: String): String {
            if (seps.isEmpty() || args.none { it in seps }) {
                return args
            }

            val sb = StringBuilder(args.length)
            var quote: Char? = null
            var atTokenStart = true

            for (c in args) {
                if (quote != null) {
                    sb.append(c)
                    if (c == quote) {
                        quote = null
                    }
                    atTokenStart = false
                } else if ((c == '"' || c == '\'') && atTokenStart) {
                    quote = c
                    sb.append(c)
                    atTokenStart = false
                } else if (c in seps) {
                    sb.append(' ')
                    atTokenStart = true
                } else {
                    sb.append(c)
                    atTokenStart = c.isWhitespace()
                }
            }

            return sb.toString()
        }

        /**
         * Since space can not appear in dynamic parameters in command line, we use % instead.
         * */
        fun normalize(argv: Array<String>) {
            for (i in argv.indices) {
                argv[i] = argv[i]
                    .replace("%", " ")
                    .replace("%20", " ")
            }
        }

        /**
         * Split a command line into argument vector (argv).
         *
         * Single quoted values are kept together as well as double quoted ones, so a value
         * containing a space survives the split, e.g. `-outLink '#main a'` produces one value
         * token `'#main a` (the quotes are removed later, see [prepareArgs]).
         *
         * @see {https://stackoverflow.com/questions/36292591/splitting-a-nested-string-keeping-quotation-marks/36292778}
         */
        fun split(args: String): Array<String> {
            val matcher = CMD_SPLIT_PATTERN.matcher(normalize(args))
            matcher.reset()
            val result = ArrayList<String>()
            while (matcher.find()) {
                result.add(matcher.group(0))
            }
            return result.toTypedArray()
        }
    }
}
