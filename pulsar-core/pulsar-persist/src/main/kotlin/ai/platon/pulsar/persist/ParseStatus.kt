package ai.platon.pulsar.persist

import ai.platon.pulsar.persist.metadata.ParseStatusCodes
import ai.platon.pulsar.persist.model.ParseStatusRecord

open class ParseStatus : ParseStatusCodes {
    val parseStatus: ParseStatusRecord

    constructor(majorCode: Short, minorCode: Int, message: String?) {
        this.parseStatus = ParseStatusRecord()
        this.majorCode = majorCode.toInt()
        setMinorCode(minorCode, message)
    }

    constructor(parseStatus: ParseStatusRecord?) {
        this.parseStatus = parseStatus?.copy() ?: ParseStatusRecord()
    }

    val isParsed: Boolean
        get() = majorCode != ParseStatusCodes.NOTPARSED.toInt()

    val isSuccess: Boolean
        get() = majorCode == ParseStatusCodes.SUCCESS.toInt()

    val isFailed: Boolean
        get() = majorCode == ParseStatusCodes.FAILED.toInt()

    val isRedirect: Boolean
        get() = isSuccess && minorCode == ParseStatusCodes.SUCCESS_REDIRECT

    val minorName: String
        get() = getMinorName(minorCode)

    var majorCode: Int
        get() = parseStatus.majorCode
        set(value) {
            parseStatus.majorCode = value
        }

    var minorCode: Int
        get() = parseStatus.minorCode
        set(value) {
            parseStatus.minorCode = value
        }

    fun setMinorCode(minorCode: Int, message: String?) {
        this.minorCode = minorCode
        if (message != null) {
            args[minorName] = message
        }
    }

    val args: MutableMap<String, String>
        get() = parseStatus.args

    fun setArgs(args: Map<String, String>) {
        parseStatus.args.putAll(args)
    }

    val name: String
        get() = (majorCodes.getOrDefault(majorCode.toShort(), "unknown") + "/"
                + minorCodes.getOrDefault(minorCode, "unknown"))

    fun unbox(): ParseStatusRecord {
        return parseStatus
    }

    override fun toString(): String {
        val argsStr = args.entries.joinToString(", ") { (k, v) -> "$k: ${v ?: "(null)"}" }
        return "$name ($majorCode/$minorCode), args=[$argsStr]"
    }

    companion object {
        // Expose commonly-used status codes from ParseStatusCodes interface for Kotlin callers.
        // Java interface constants are not inherited unqualified in Kotlin.
        const val NOTPARSED: Short = ParseStatusCodes.NOTPARSED
        const val SUCCESS: Short = ParseStatusCodes.SUCCESS
        const val FAILED: Short = ParseStatusCodes.FAILED

        const val SC_OK: Int = ParseStatusCodes.SC_OK
        const val SUCCESS_REDIRECT: Int = ParseStatusCodes.SUCCESS_REDIRECT

        const val FAILED_EXCEPTION: Int = ParseStatusCodes.FAILED_EXCEPTION
        const val FAILED_NOT_SPECIFIED: Int = ParseStatusCodes.FAILED_NOT_SPECIFIED
        const val FAILED_TRUNCATED: Int = ParseStatusCodes.FAILED_TRUNCATED
        const val FAILED_INVALID_FORMAT: Int = ParseStatusCodes.FAILED_INVALID_FORMAT
        const val FAILED_MISSING_PARTS: Int = ParseStatusCodes.FAILED_MISSING_PARTS
        const val FAILED_MISSING_CONTENT: Int = ParseStatusCodes.FAILED_MISSING_CONTENT
        const val FAILED_NO_PARSER: Int = ParseStatusCodes.FAILED_NO_PARSER
        const val FAILED_MALFORMED_URL: Int = ParseStatusCodes.FAILED_MALFORMED_URL
        const val FAILED_UNKNOWN_ENCODING: Int = ParseStatusCodes.FAILED_UNKNOWN_ENCODING

        val majorCodes: MutableMap<Short, String> = HashMap()
        val minorCodes: MutableMap<Int, String> = HashMap()

        init {
            majorCodes[ParseStatusCodes.NOTPARSED] = "notparsed"
            majorCodes[ParseStatusCodes.SUCCESS] = "success"
            majorCodes[ParseStatusCodes.FAILED] = "failed"

            minorCodes[ParseStatusCodes.SC_OK] = "ok"
            minorCodes[ParseStatusCodes.SUCCESS_REDIRECT] = "redirect"

            minorCodes[ParseStatusCodes.FAILED_EXCEPTION] = "exception"
            minorCodes[ParseStatusCodes.FAILED_NOT_SPECIFIED] = "not_specified"
            minorCodes[ParseStatusCodes.FAILED_TRUNCATED] = "truncated"
            minorCodes[ParseStatusCodes.FAILED_INVALID_FORMAT] = "invalid_format"
            minorCodes[ParseStatusCodes.FAILED_MISSING_PARTS] = "missing_parts"
            minorCodes[ParseStatusCodes.FAILED_MISSING_CONTENT] = "missing_content"
            minorCodes[ParseStatusCodes.FAILED_NO_PARSER] = "no_parser"
            minorCodes[ParseStatusCodes.FAILED_MALFORMED_URL] = "malformed_url"
            minorCodes[ParseStatusCodes.FAILED_UNKNOWN_ENCODING] = "unknown_encoding"
        }

        @JvmStatic
        fun box(parseStatus: ParseStatusRecord): ParseStatus {
            return ParseStatus(parseStatus)
        }

        @JvmStatic
        fun getMajorName(code: Short): String {
            return majorCodes.getOrDefault(code, "unknown")
        }

        @JvmStatic
        fun getMinorName(code: Int): String {
            return minorCodes.getOrDefault(code, "unknown")
        }
    }
}
