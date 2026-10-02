@file:Suppress("Unused")

package ai.platon.pulsar.common.urls

import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.AppConstants.INTERNAL_URL_PREFIX
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.StringUtils
import org.apache.hc.core5.net.URIBuilder
import java.net.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*

object URLUtils {
    /**
     * The prefix of allowed urls
     */
    val INTERNAL_URL_PREFIXES = listOf("chrome://", "edge://", "brave://")

    /**
     * The urls of all allowed internal urls
     */
    val INTERNAL_URLS = listOf("about:blank")

    /** A run of `/` separators — collapsed into one by the path canonicalization of [normalize]. */
    private val MULTIPLE_SEPARATORS = Regex("/{2,}")

    /**
     * RFC 3986 §2.3: the characters that never need an escape, so an escape of one of them is the same
     * resource as the character itself, and [normalize] writes the character.
     */
    private const val UNRESERVED_CHARACTERS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** The largest value a `%XX` escape can stand for in a url this library handles. */
    private const val MAX_ASCII = 127

    /**
     * Test if the url is an internal URL. Internal URLs are URLs that are used to identify internal resources and
     * will never be fetched from the internet.
     *
     * @param  url   The url to test
     * @return true if the given str is an internal URL, false otherwise
     * */
    @JvmStatic
    fun isInternal(url: String): Boolean {
        return url.startsWith(INTERNAL_URL_PREFIX)
    }

    /**
     * Test if the url is not an internal URL. Internal URLs are URLs that are used to identify internal resources and
     * will never be fetched from the internet.
     *
     * @param  url   The url to test
     * @return true if the given str is not an internal URL, false otherwise
     * */
    @JvmStatic
    fun isNotInternal(url: String) = !isInternal(url)

    /**
     * Check if the given url is a local file url, which is a url that starts with {@link AppConstants#LOCAL_FILE_SERVE_PREFIX}
     * */
    @JvmStatic
    fun isLocalFile(url: String): Boolean {
        return url.startsWith(AppConstants.LOCAL_FILE_BASE_URL)
    }

    /**
     * Convert a path to a URL, the path will be encoded to base64 and appended to the {@link AppConstants#LOCAL_FILE_FAKE_SERVER_HOME}
     *
     * For example:
     *
     * `C:\Users\pereg\AppData\Local\Temp\pulsar\test.txt`
     * will be converted to:
     * `http://localfile.org?path=QzpcVXNlcnNccGVyZWdcQXBwRGF0YVxMb2NhbFxUZW1wXHB1bHNhclx0ZXN0LnR4dA==`
     *
     * @param path The path to convert
     *
     * TODO: consider just use path.toUri() in the system
     * */
    @JvmStatic
    fun pathToLocalURL(path: Path): String {
        val base64 = Base64.getUrlEncoder().encode(path.toString().toByteArray()).toString(Charsets.UTF_8)
        val prefix = AppConstants.LOCAL_FILE_BASE_URL
        return "$prefix?path=$base64"
    }

    /**
     * Convert a URL to a path, the path is decoded from base64 and the prefix {@link AppConstants#LOCAL_FILE_SERVE_PREFIX} is removed
     * */
    @JvmStatic
    fun localURLToPath(url: String): Path {
        val encodedPath = kotlin.runCatching {
            URIBuilder(url).queryParams?.firstOrNull { it.name == "path" }?.value
        }.getOrNull()

        require(!encodedPath.isNullOrBlank()) { "Missing query parameter 'path' in local file url: $url" }

        val decoded = Base64.getUrlDecoder().decode(encodedPath).toString(Charsets.UTF_8)
        return Path.of(decoded)
    }

    /**
     * Checks if the given string is a browser-specific url.
     *
     * This function determines whether the string is a browser-specific url
     * by checking if it exists in the internal URL list (INTERNAL_URLS),
     * or if it starts with any of the internal URL prefixes (INTERNAL_URL_PREFIXES).
     *
     * @param str The string to be checked.
     * @return Returns true if the string is a browser-specific url; otherwise, returns false.
     */
    @JvmStatic
    fun isBrowserURL(str: String): Boolean {
        return INTERNAL_URLS.contains(str) || INTERNAL_URL_PREFIXES.any { str.startsWith(it) }
    }

    /**
     * Checks if the given URL is a browser-specific URL by verifying if it starts with a predefined prefix.
     *
     * @param url The URL to check.
     * @return Returns true if the URL starts with the browser-specific url prefix, otherwise false.
     */
    @JvmStatic
    fun isMappedBrowserURL(url: String): Boolean {
        return url.startsWith(AppConstants.BROWSER_INTERNAL_BASE_URL)
    }

    /**
     * Converts a browser url string into a complete URL.
     * The function URL-encodes the url string and appends it to a predefined prefix to form the final URL.
     *
     * @param url The browser url string to be converted. This string will be URL-encoded.
     * @return Returns the complete URL string containing the prefix and the encoded url parameter.
     */
    @JvmStatic
    fun browserURLToStandardURL(url: String): String {
        val encoded = URLEncoder.encode(url, Charsets.UTF_8)
        val prefix = AppConstants.BROWSER_INTERNAL_BASE_URL
        return "$prefix?url=$encoded"
    }

    /**
     * Extracts the browser url from a given URL and re-encodes it.
     * The function retrieves the url parameter from the URL, re-encodes it, and reconstructs the URL.
     *
     * @param url The URL containing the browser url.
     * @return Returns the reconstructed URL with the re-encoded url parameter.
     */
    @JvmStatic
    fun standardURLToBrowserURL(url: String): String? {
        val encoded = kotlin.runCatching {
            URIBuilder(url).queryParams?.firstOrNull { it.name == "url" }?.value
        }.getOrNull() ?: return null

        if (encoded.isBlank()) {
            return null
        }

        return URLDecoder.decode(encoded, Charsets.UTF_8)
    }

    @Deprecated("Use getURLOrNull2 instead", ReplaceWith("getURLOrNull2(spec)"))
    @JvmStatic
    fun getURLOrNull(spec: String?): URL? {
        if (spec.isNullOrBlank()) {
            return null
        }

        return kotlin.runCatching { URI.create(spec).toURL() }.getOrNull()
    }

    /**
     * Creates a {@code URL} object from the {@code String}
     * representation.
     *
     * @param      spec   the {@code String} to parse as a URL.
     * @return     the URL parsed from [spec],
     *             or null if no protocol is specified, or an
     *               unknown protocol is found, or {@code spec} is {@code null},
     *               or the parsed URL fails to comply with the specific syntax
     *               of the associated protocol.
     * @see        java.net.URL#URI.create(java.net.URL)
     */
    @JvmStatic
    fun getURLOrNull2(spec: String?): URL? {
        return spec?.toHttpUrlOrNull()?.toUrl()
    }

    /**
     * Test if the str is a standard URL.
     *
     * A url is standard when it can be **normalized** — that is the one definition the gates of this
     * codebase have to agree on: [isStandard] decides what may be accepted (a discovered href, a
     * crawl seed, a command), and [normalize] decides what the page store and the page cache are
     * keyed by.  They used to answer from two different parsers — okhttp for this one, httpcore5 for
     * the normalization — so 9 of 23 measured inputs got *opposite* verdicts: a url could pass this
     * gate and then normalize to null, which the load path turns into a NIL page, and a url could be
     * refused here while it normalized perfectly well.
     *
     * @param  str   The string to test
     * @return true if the given str is a standard URL, false otherwise
     * */
    @JvmStatic
    fun isStandard(str: String?): Boolean {
        return normalizeOrNull(str) != null
    }

    /**
     * Test if the str is an allowed URL.
     *
     * @param  str   The string to test
     * @return true if the given str is a standard URL, false otherwise
     * */
    @JvmStatic
    fun isAllowed(str: String?): Boolean {
        return str != null && (isInternal(str) || isStandard(str))
    }

    /**
     * Normalize a url spec.
     *
     * **Normalization produces an identity, never an address.**  Its result is what the page store,
     * the page cache and every url-keyed lookup are keyed by, and it must not be the url a browser is
     * sent to: this method drops the fragment (so a same-document jump such as `…#section` is gone),
     * drops the trailing argument list, and — with [ignoreQuery] — the query as well.  Keep the
     * spelling the caller gave (a link's `href`, or what a user typed) and navigate to that; use this
     * result to look the page up.
     *
     * Concretely: `NormURL` carries exactly that pair — `url` for the key and `href` for the address,
     * with `href` preferred for navigation — `NavigateEntry` spells it `pageUrl` and `userTypedUrl`,
     * and `InteractiveBrowserEmulator` resolves it as `fetchTask.href ?: fetchTask.url`.
     *
     * A URL may have appended to it a "fragment", also known as a "ref" or a "reference".
     * The fragment is indicated by the sharp sign character "#" followed by more characters.
     * For example: http://java.sun.com/index.html#chapter1
     *
     * The fragment will be removed after the normalization.
     * If ignoreQuery is true, the query string will be removed.
     *
     * @param url
     *        The url to normalize, a tailing argument list is allowed and will be removed
     *
     * @param ignoreQuery
     *        If true, the result url does not contain a query string
     *
     * @return The normalized URL, an identity to look a page up by — not an address to navigate to
     * @throws URISyntaxException
     *         If the given string violates RFC&nbsp;2396
     * @throws MalformedURLException
     * @throws IllegalArgumentException
     * */
    @JvmStatic
    @Throws(URISyntaxException::class, IllegalArgumentException::class, MalformedURLException::class)
    fun normalize(url: String, ignoreQuery: Boolean = false): URL {
        // The url and its argument list are split first, so the fragment is removed from the
        // url token only, a `#` inside an option value is never touched.
        val (url0, _) = splitUrlArgs(url)

        // The fragment is discarded by this method, so it must not be able to reject the url.
        // A bare `%` or a second `#` inside it (`...#100%`, `...#x#y`) is rejected by `URI`,
        // which turned a url every browser loads happily into a normalization failure — the
        // fragment is removed before the uri is parsed, so only the part that survives the
        // normalization has to be well formed.
        val withoutFragment = url0.substringBefore('#')

        // Only the schemes that identify a *document* this library handles: the two the pipeline
        // fetches over the network, plus a local file (which it has always normalized — see
        // testNormalize_WindowsFileURI).  `mailto:`, `ftp:` and `data:` parse too, but they are not
        // documents this pipeline fetches, and admitting them would turn every `mailto:` anchor into
        // a link a crawl tries to follow.
        val scheme = withoutFragment.substringBefore(':').lowercase(Locale.getDefault())
        require(scheme == "http" || scheme == "https" || scheme == "file") {
            "Not a fetchable url: <$url>"
        }

        val uriBuilder = URIBuilder(canonicalize(withoutFragment))
        if (ignoreQuery) {
            uriBuilder.removeQuery()
        }

        return uriBuilder.build().toURL()
    }

    /**
     * The canonical spelling of [url].
     *
     * [normalize] is what the page store, the page cache and every url-keyed lookup are keyed by, so
     * "the same normalized string" **is** the definition of "the same resource" for the whole
     * pipeline.  Five spelling differences are folded here because the RFC says they are the same
     * resource (§6.2.2, §6.2.3):
     *
     *  * the scheme and the host are case insensitive (`HTTP://Example.com`),
     *  * a default port is the same as no port (`:80` on http, `:443` on https),
     *  * an empty path is `/`,
     *  * `.` and `..` segments resolve away (`/a/./b/../c` is `/a/c`),
     *  * an escape of an unreserved character is that character (`%7E` is `~`, in either case).
     *
     * Three more are folded **by policy**: they are the same resource on the servers this pipeline
     * meets, and keeping them apart splits one page across several store rows, so the second visit
     * misses the first one's copy.
     *
     *  * a trailing slash on a non-root path (`/p/` is `/p`),
     *  * repeated separators (`/a//b` is `/a/b`),
     *  * the order of the query parameters (`?b=2&a=1` is `?a=1&b=2`; a *repeated* name keeps its
     *    relative order, so `?a=1&a=2` stays distinct from `?a=2&a=1` — the server may care which
     *    comes first, while the set of parameters is the same either way).
     *
     * **Folding a spelling changes the key, never the address.**  This result is not what a browser is
     * sent to: `NormURL` carries the caller's spelling beside it as `href`, `NavigateEntry` spells it
     * `userTypedUrl`, and `InteractiveBrowserEmulator` resolves the address as
     * `fetchTask.href ?: fetchTask.url`.  A page reached as `/p/` is therefore still *fetched* as
     * `/p/` — only the row it is stored under is `/p`.
     *
     * The crawl keeps its own, coarser identity for the "is this href worth queueing" question
     * (`normalizeForVisit`); the difference between the two is deliberate.
     */
    private fun canonicalize(url: String): String {
        val uri = URI(url)
        val scheme = (uri.scheme ?: return url).lowercase(Locale.getDefault())
        val host = (uri.host ?: return url).lowercase(Locale.getDefault())
        val port = uri.port.takeUnless { it == defaultPortOf(scheme) } ?: -1
        val userInfo = uri.rawUserInfo?.let { "$it@" } ?: ""
        val authority = if (port < 0) "$userInfo$host" else "$userInfo$host:$port"
        val path = canonicalPath(resolveDotSegments(uri.rawPath ?: "").ifEmpty { "/" })

        return buildString {
            append(scheme).append("://").append(authority).append(path)
            canonicalQuery(uri.rawQuery)?.let { append('?').append(it) }
        }
    }

    /**
     * The canonical spelling of a path: an escape of an unreserved character becomes that character,
     * repeated separators become one, and a trailing slash on a non-root path goes away.
     *
     * The root stays `/`: a single separator is a path, no path at all, and — for the schemes this
     * library fetches — the server's own default document.
     */
    private fun canonicalPath(path: String): String {
        val decoded = decodeUnreservedEscapes(path)
        val collapsed = decoded.replace(MULTIPLE_SEPARATORS, "/")
        return if (collapsed.length > 1) collapsed.trimEnd('/').ifEmpty { "/" } else collapsed
    }

    /**
     * The canonical spelling of a query string: an escape of an unreserved character becomes that
     * character (in the name and in the value), and the parameters are ordered by name — a stable
     * sort, so parameters that share a name keep the order they were written in.
     *
     * Nothing else is normalized: `?debug` (a bare name) and `?param=` (an empty value) are different
     * spellings and reach the server as they are, and so does the case of an escape of a *reserved*
     * character (`%2F` stays `%2F`, because decoding it would change how the url is parsed).
     */
    private fun canonicalQuery(query: String?): String? {
        if (query.isNullOrEmpty()) {
            return query
        }

        return query.split('&')
            .map { param ->
                val eq = param.indexOf('=')
                val name = decodeUnreservedEscapes(if (eq < 0) param else param.substring(0, eq))
                val value = if (eq < 0) null else decodeUnreservedEscapes(param.substring(eq + 1))
                name to value
            }
            .sortedBy { it.first }
            .joinToString("&") { (name, value) -> if (value == null) name else "$name=$value" }
    }

    /**
     * Decode every escape of an unreserved character: `%7E` is `~`, `%41` is `A` (RFC 3986 §6.2.2.2).
     *
     * Nothing else is touched — an escape of a reserved character (`%2F`, `%3F`) stays escaped, in the
     * case it was written in, and so does a `%` that does not start a valid escape.
     */
    private fun decodeUnreservedEscapes(text: String): String {
        if (!text.contains('%')) {
            return text
        }

        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%' && i + 2 < text.length) {
                val decoded = hexValue(text[i + 1]) * 16 + hexValue(text[i + 2])
                if (decoded in 0..MAX_ASCII && UNRESERVED_CHARACTERS.contains(decoded.toChar())) {
                    out.append(decoded.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            ++i
        }

        return out.toString()
    }

    /** The value of a hex digit, or -1 when [c] is not one. */
    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /** The port a scheme implies when the url names none. */
    private fun defaultPortOf(scheme: String): Int = when (scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }

    /**
     * Resolve the `.` and `..` segments of [path].
     *
     * Only those two segments are touched here: the separators stay as they are and so does every
     * escape.  Collapsing `//` and decoding `%7E` happen later, in [canonicalPath], so each step of
     * the canonical form stays readable on its own.
     */
    private fun resolveDotSegments(path: String): String {
        if (!path.contains('.')) {
            return path
        }

        val out = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "." -> Unit
                // Never pop the root marker (the empty leading segment), or `..` would climb out of
                // an absolute path.
                ".." -> if (out.isNotEmpty() && out.last().isNotEmpty()) out.removeLast()
                else -> out.addLast(part)
            }
        }

        return out.joinToString("/")
    }

    /**
     * Normalize a url spec.
     *
     * A URL may have appended to it a "fragment", also known as a "ref" or a "reference".
     * The fragment is indicated by the sharp sign character "#" followed by more characters.
     * For example: http://java.sun.com/index.html#chapter1
     *
     * The fragment will be removed after the normalization.
     * If ignoreQuery is true, the query string will be removed.
     *
     * @param url
     *        The url to normalize, a tailing argument list is allowed and will be removed
     *
     * @param ignoreQuery
     *        If true, the result url does not contain a query string
     *
     * @return The normalized url,
     *         or an empty string ("") if the given string violates RFC&nbsp;2396
     * */
    @JvmStatic
    fun normalizeOrEmpty(url: String, ignoreQuery: Boolean = false): String {
        return try {
            normalize(url, ignoreQuery).toString()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Normalize a url spec.
     *
     * A URL may have appended to it a "fragment", also known as a "ref" or a "reference".
     * The fragment is indicated by the sharp sign character "#" followed by more characters.
     * For example: http://java.sun.com/index.html#chapter1
     *
     * The fragment will be removed after the normalization.
     * If ignoreQuery is true, the query string will be removed.
     *
     * @param url
     *        The url to normalize, a tailing argument list is allowed and will be removed
     *
     * @param ignoreQuery
     *        If true, the result url does not contain a query string
     *
     * @return The normalized url,
     *         or null if the given string violates RFC&nbsp;2396
     * */
    @JvmStatic
    fun normalizeOrNull(url: String?, ignoreQuery: Boolean = false): String? {
        if (url == null) {
            return null
        }

        return try {
            normalize(url, ignoreQuery).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalize a url spec.
     *
     * A URL may have appended to it a "fragment", also known as a "ref" or a "reference".
     * The fragment is indicated by the sharp sign character "#" followed by more characters.
     * For example: http://java.sun.com/index.html#chapter1
     *
     * The fragment will be removed after the normalization.
     * If ignoreQuery is true, the query string will be removed.
     *
     * @param urls
     *        The urls to normalize, a tailing argument list is allowed and will be removed
     *
     * @param ignoreQuery
     *        If true, the result url does not contain a query string
     *
     * @return The normalized URLs
     * */
    @JvmStatic
    fun normalizeUrls(urls: Iterable<String>, ignoreQuery: Boolean = false): List<String> {
        return urls.mapNotNull { normalizeOrNull(it, ignoreQuery) }
    }

    /**
     * Normalizes a URL by removing redundant slashes and normalizing the URI.
     * @param url The URL to normalize.
     * @return The normalized URL as a string.
     * */
    fun removeRedundantSlashes(url: String): String {
        val uri = URI(url).normalize()

        val normalizedPath = uri.path?.replace(Regex("/+"), "/") ?: "/"

        return URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            normalizedPath,
            uri.query,
            uri.fragment
        ).toString()
    }

    /**
     * Builds a server URL from the given hostname, port, context path, and path.
     * @param hostname The hostname of the server.
     * @param port The port number of the server.
     * @param contextPath The context path of the server.
     * @param path The specific path to append to the context path.
     * @return The constructed server URL as a string.
     * */
    @Throws(URISyntaxException::class)
    fun buildServerUrl(hostname: String, port: Int, contextPath: String, path: String = ""): String {
        val combinedPath = listOf(contextPath, path)
            .filter { it.isNotBlank() }
            .joinToString("/")
            .replace(Regex("/+"), "/")
            .let { if (!it.startsWith("/")) "/$it" else it }
        val url = "http://$hostname:$port$combinedPath"
        return removeRedundantSlashes(url)
    }

    /**
     * Split the query parameters of a url.
     *
     * @param url The url to split
     * @return The query parameters of the url
     * */
    @Throws(URISyntaxException::class)
    fun splitQueryParameters(url: String): Map<String, String> {
        return URIBuilder(url).queryParams?.associate { it.name to it.value } ?: mapOf()
    }

    /**
     * Get the query parameter of a url.
     *
     * @param url The url to split
     * @param parameterName The name of the query parameter
     * @return The query parameter of the url
     * */
    @Throws(URISyntaxException::class)
    fun getQueryParameters(url: String, parameterName: String): String? {
        return URIBuilder(url).queryParams?.firstOrNull { it.name == parameterName }?.value
    }

    /**
     * Remove the query parameters of a url.
     *
     * @param url The url to split
     * @param parameterNames The names of the query parameters
     * @return The url without the query parameters
     * */
    @Throws(URISyntaxException::class)
    fun removeQueryParameters(url: String, vararg parameterNames: String): String {
        val uriBuilder = URIBuilder(url)
        uriBuilder.setParameters(uriBuilder.queryParams.apply { removeIf { it.name in parameterNames } })
        return uriBuilder.build().toString()
    }

    /**
     * Keep the query parameters of a url, and remove the others.
     *
     * @param url The url to split
     * @param parameterNames The names of the query parameters
     * @return The url with only the query parameters
     * */
    @Throws(URISyntaxException::class)
    fun keepQueryParameters(url: String, vararg parameterNames: String): String {
        val uriBuilder = URIBuilder(url)
        uriBuilder.setParameters(uriBuilder.queryParams.apply { removeIf { it.name !in parameterNames } })
        return uriBuilder.build().toString()
    }

    /**
     * Split url and args
     *
     * The configured url is `$url $args`, the url is the first whitespace separated token and
     * the args are the rest of the string, returned verbatim.
     *
     * Note: this method never strips a fragment. A `#` inside the args part is a normal
     * character of an option value, e.g. `-requireNotBlank '#productTitle'`, and must not be
     * treated as the start of a fragment. A fragment is removed from the url token only, by
     * [normalize].
     *
     * @param configuredUrl url and args in `$url $args` format
     * @return url and args pair
     */
    @JvmStatic
    fun splitUrlArgs(configuredUrl: String): Pair<String, String> {
        var url = configuredUrl.trim().replace("[\\r\\n\\t]".toRegex(), StringUtils.SPACE)
        val pos = url.indexOfFirst { it.isWhitespace() }

        var args = ""
        if (pos >= 0) {
            args = url.substring(pos)
            url = url.substring(0, pos)
        }

        return url.trim() to args.trim()
    }

    /**
     * Merge url and args
     *
     * @param url  url
     * @param args args
     * @return url and args in `$url $args` format
     */
    @JvmStatic
    fun mergeUrlArgs(url: String, args: String? = null): String {
        return if (args.isNullOrBlank()) url.trim() else "${url.trim()} ${args.trim()}"
    }

    /**
     * Get the url without parameters
     *
     * @param url url
     * @return url without parameters
     */
    @JvmStatic
    fun getUrlWithoutParameters(url: String): String {
        try {
            var uri = URI(url)
            uri = URI(
                uri.scheme,
                uri.authority,
                uri.path,
                null, // Ignore the query part of the input url
                uri.fragment
            )
            return uri.toString()
        } catch (ignored: Throwable) {
        }

        return ""
    }

    /**
     * Returns the normalized url and key
     *
     * @param originalUrl
     * @param norm
     * @return normalized url and key
     */
    @JvmStatic
    fun normalizedUrlAndKey(originalUrl: String, norm: Boolean = false): Pair<String, String> {
        val url = if (norm) (normalizeOrNull(originalUrl) ?: "") else originalUrl
        val key = reverseUrlOrEmpty(url)
        return url to key
    }

    /**
     * URL-encodes a string for safe use in URL paths.
     *
     * Note: Uses URLEncoder for form encoding, then converts '+' to '%20'
     * for proper path encoding.
     */
    fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }

    /**
     * Reverses a url's domain. This form is better for storing in hbase. Because
     * scans within the same domain are faster.
     *
     * E.g. "http://bar.foo.com:8983/to/index.html?a=b" becomes
     * "com.foo.bar:8983:http/to/index.html?a=b".
     *
     * @param url url to be reversed
     * @return Reversed url
     * @throws MalformedURLException
     */
    @JvmStatic
    fun reverseUrl(url: String): String {
        return reverseUrl(URI.create(url).toURL())
    }

    /**
     * Reverses a url's domain. This form is better for storing in hbase. Because
     * scans within the same domain are faster.
     *
     * E.g. "http://bar.foo.com:8983/to/index.html?a=b" becomes
     * "com.foo.bar:8983:http/to/index.html?a=b".
     *
     * @param url url to be reversed
     * @return Reversed url or empty string if the url is invalid
     */
    @JvmStatic
    fun reverseUrlOrEmpty(url: String): String {
        return try {
            reverseUrl(URI.create(url).toURL())
        } catch (e: MalformedURLException) {
            ""
        }
    }

    /**
     * Reverses a url's domain. This form is better for storing in hbase. Because
     * scans within the same domain are faster.
     *
     * E.g. "http://bar.foo.com:8983/to/index.html?a=b" becomes
     * "com.foo.bar:8983:http/to/index.html?a=b".
     *
     * @param url url to be reversed
     * @return Reversed url or null if the url is invalid
     */
    @JvmStatic
    fun reverseUrlOrNull(url: String): String? {
        return try {
            reverseUrl(URI.create(url).toURL())
        } catch (e: MalformedURLException) {
            null
        }
    }

    /**
     * Reverses a url's domain. This form is better for storing in hbase. Because scans within the same domain are
     * faster.
     *
     * E.g. "http://bar.foo.com:8983/to/index.html?a=b" becomes "com.foo.bar:http:8983/to/index.html?a=b".
     *
     * @param url url to be reversed
     * @return Reversed url
     */
    @JvmStatic
    fun reverseUrl(url: URL): String {
        val host = url.host
        val file = url.file
        val protocol = url.protocol
        val port = url.port

        val buf = StringBuilder()

        /* reverse host */
        reverseAppendSplits(host, buf)

        /* put protocol */
        buf.append(':')
        buf.append(protocol)

        /* put port if necessary */
        if (port != -1) {
            buf.append(':')
            buf.append(port)
        }

        /* put path */
        if (file.isNotEmpty() && '/' != file[0]) {
            buf.append('/')
        }
        buf.append(file)

        return buf.toString()
    }

    /**
     * Get the reversed and tenanted format of unreversedUrl, unreversedUrl can be both tenanted or not tenanted
     * This method might change the tenant id of the original url
     *
     * Zero tenant id means no tenant
     *
     * @param unreversedUrl the unreversed url, can be both tenanted or not tenanted
     * @return the tenanted and reversed url of unreversedUrl
     */
    @JvmStatic
    fun reverseUrl(tenantId: Int, unreversedUrl: String): String {
        val tenantedUrl = TenantedUrl.split(unreversedUrl)
        return TenantedUrl.combine(tenantId, reverseUrl(tenantedUrl.url))
    }

    /**
     * Get the unreversed url of a reversed url.
     *
     * @param reversedUrl
     * @return the unreversed url of reversedUrl
     */
    @JvmStatic
    fun unreverseUrl(reversedUrl: String): String {
        val buf = StringBuilder(reversedUrl.length + 2)

        var pathBegin = reversedUrl.indexOf('/')
        if (pathBegin == -1) {
            pathBegin = reversedUrl.length
        }
        val sub = reversedUrl.substring(0, pathBegin)

        val splits = StringUtils.splitPreserveAllTokens(sub, ':') // {<reversed host>, <port>, <protocol>}

        buf.append(splits[1]) // put protocol
        buf.append("://")
        reverseAppendSplits(splits[0], buf) // splits[0] is reversed
        // host
        if (splits.size == 3) { // has a port
            buf.append(':')
            buf.append(splits[2])
        }

        buf.append(reversedUrl.substring(pathBegin))

        return buf.toString()
    }

    /**
     * Get the unreversed url of a reversed url.
     *
     * @param reversedUrl
     * @return the unreversed url of reversedUrl or null if the url is invalid
     */
    @JvmStatic
    fun unreverseUrlOrNull(reversedUrl: String) = kotlin.runCatching { unreverseUrl(reversedUrl) }.getOrNull()

    /**
     * Get unreversed and tenanted url of reversedUrl, reversedUrl can be both tenanted or not tenanted,
     * This method might change the tenant id of the original url
     *
     * @param tenantId    the expected tenant id of the reversedUrl
     * @param reversedUrl the reversed url, can be both tenanted or not tenanted
     * @return the unreversed url of reversedTenantedUrl
     * @throws MalformedURLException
     */
    @JvmStatic
    fun unreverseUrl(tenantId: Int, reversedUrl: String): String {
        val tenantedUrl = TenantedUrl.split(reversedUrl)
        return TenantedUrl.combine(tenantId, unreverseUrl(tenantedUrl.url))
    }

    /**
     * Get start key for tenanted table
     *
     * @param unreversedUrl unreversed key, which is the original url
     * @return reverse and tenanted key
     */
    @JvmStatic
    fun getStartKey(tenantId: Int, unreversedUrl: String?): String? {
        if (unreversedUrl == null) {
            // restricted within tenant space
            return if (tenantId == 0) null else tenantId.toString()
        }

        //    if (StringUtils.countMatches(unreversedUrl, "0001") > 1) {
        //      return null;
        //    }

        val startKey = decodeKeyLowerBound(unreversedUrl)
        return reverseUrl(tenantId, startKey)
    }

    /**
     * Get start key for non-tenanted table
     *
     * @param unreversedUrl unreversed key, which is the original url
     * @return reverse key
     */
    @JvmStatic
    fun getStartKey(unreversedUrl: String?): String? {
        if (unreversedUrl == null) {
            return null
        }

        //    if (StringUtils.countMatches(unreversedUrl, "0001") > 1) {
        //      return null;
        //    }

        val startKey = decodeKeyLowerBound(unreversedUrl)
        return reverseUrl(startKey)
    }

    /**
     * Get end key for non-tenanted tables
     *
     * @param unreversedUrl unreversed key, which is the original url
     * @return reverse, key bound decoded key
     */
    @JvmStatic
    fun getEndKey(unreversedUrl: String?): String? {
        if (unreversedUrl == null) {
            return null
        }

        //    if (StringUtils.countMatches(unreversedUrl, "FFFF") > 1) {
        //      return null;
        //    }

        val endKey = decodeKeyUpperBound(unreversedUrl)
        return reverseUrl(endKey)
    }

    /**
     * Get end key for tenanted tables
     *
     * @param unreversedUrl unreversed key, which is the original url
     * @return reverse, tenanted and key bound decoded key
     */
    @JvmStatic
    fun getEndKey(tenantId: Int, unreversedUrl: String?): String? {
        if (unreversedUrl == null) {
            // restricted within tenant space
            return if (tenantId == 0) null else (tenantId + 1).toString()
        }

        //    if (StringUtils.countMatches(unreversedUrl, "FFFF") > 1) {
        //      return null;
        //    }

        val endKey = decodeKeyUpperBound(unreversedUrl)
        return reverseUrl(tenantId, endKey)
    }

    /**
     * We use unicode character \u0001 to be the lower key bound, but the client usally
     * encode the character to be a string "\\u0001" or "\\\\u0001", so we should decode
     * them to be the right one
     *
     * Note, the character is displayed as <U></U>+0001> in some output system
     *
     * Now, we consider all the three character/string \u0001, "\\u0001" and "\\\\u0001"
     * are the lower key bound
     */
    @JvmStatic
    fun decodeKeyLowerBound(startKey: String): String {
        var startKey1 = startKey
        startKey1 = startKey1.replace("\\\\u0001".toRegex(), "\u0001")
        startKey1 = startKey1.replace("\\u0001".toRegex(), "\u0001")

        return startKey1
    }

    /**
     * We use unicode character \uFFFF to be the upper key bound, but the client usally
     * encode the character to be a string "\\uFFFF" or "\\\\uFFFF", so we should decode
     * them to be the right one
     *
     *
     * Note, the character may display as <U></U>+FFFF> in some output system
     *
     *
     * Now, we consider all the three character/string \uFFFF, "\\uFFFF" and "\\\\uFFFF"
     * are the upper key bound
     */
    @JvmStatic
    fun decodeKeyUpperBound(endKey: String): String {
        var endKey1 = endKey
        // Character lastChar = Character.MAX_VALUE;
        endKey1 = endKey1.replace("\\\\uFFFF".toRegex(), "\uFFFF")
        endKey1 = endKey1.replace("\\uFFFF".toRegex(), "\uFFFF")

        return endKey1
    }

    /**
     * Given a reversed url, returns the reversed host E.g
     * "com.foo.bar:http:8983/to/index.html?a=b" -> "com.foo.bar"
     *
     * @param reversedUrl Reversed url
     * @return Reversed host
     */
    @JvmStatic
    fun getReversedHost(reversedUrl: String): String {
        return reversedUrl.substring(0, reversedUrl.indexOf(':'))
    }

    /**
     * Reverse the host name.
     *
     * @param hostName host name
     * @return reversed host name
     */
    @JvmStatic
    fun reverseHost(hostName: String): String {
        val buf = StringBuilder()
        reverseAppendSplits(hostName, buf)
        return buf.toString()
    }

    /**
     * Unreverse the host name.
     *
     * @param reversedHostName reversed host name
     * @return host name
     */
    @JvmStatic
    fun unreverseHost(reversedHostName: String): String {
        return reverseHost(reversedHostName) // Reversible
    }


    /**
     * Indicates whether this domain name represents a *public suffix*, as defined by the Mozilla
     * Foundation's [Public Suffix List](http://publicsuffix.org/) (PSL). A public suffix
     * is one under which Internet users can directly register names, such as `com`, `co.uk` or `pvt.k12.wy.us`. Examples of domain names that are *not* public suffixes
     * include `google.com`, `foo.co.uk`, and `myblog.blogspot.com`.
     *
     *
     * Public suffixes are a proper superset of [registry suffixes][.isRegistrySuffix].
     * The list of public suffixes additionally contains privately owned domain names under which
     * Internet users can register subdomains. An example of a public suffix that is not a registry
     * suffix is `blogspot.com`. Note that it is true that all public suffixes *have*
     * registry suffixes, since domain name registries collectively control all internet domain names.
     *
     *
     * For considerations on whether the public suffix or registry suffix designation is more
     * suitable for your application, see [this article](https://github.com/google/guava/wiki/InternetDomainNameExplained).
     *
     * @return `true` if this domain name appears exactly on the public suffix list
     */
    fun isPublicSuffix(domain: String): Boolean {
        // A domain is a public suffix if a test subdomain produces a valid top private domain
        // that includes the test subdomain itself (meaning domain is the public suffix part)
        val testUrl = "http://test.$domain"
        val httpUrl = testUrl.toHttpUrlOrNull() ?: return false
        val topPrivate = httpUrl.topPrivateDomain() ?: return true
        return topPrivate == domain
    }

    /**
     * Get the host's public suffix. For example, co.uk, com, etc.
     *
     * @since 6.0
     */
    fun getPublicSuffix(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val host = httpUrl.host
        val topPrivate = getTopPrivateDomainOrNull(url) ?: return null
        if (host == topPrivate) return null
        return host.removePrefix("$topPrivate.").substringAfterLast(".")
    }

    /**
     * Get the host's public suffix. For example, co.uk, com, etc.
     */
    fun getPublicSuffix(url: URL): String? {
        return getPublicSuffix(url.toString())
    }

    /**
     * Indicates whether this domain name is composed of exactly one subdomain component followed by a
     * {@linkplain #isPublicSuffix() public suffix}. For example, returns {@code true} for {@code
     * google.com} {@code foo.co.uk}, and {@code myblog.blogspot.com}, but not for {@code
     * www.google.com}, {@code co.uk}, or {@code blogspot.com}.
     *
     * <p>This method can be used to determine whether a domain is probably the highest level for
     * which cookies may be set, though even that depends on individual browsers' implementations of
     * cookie controls. See <a href="http://www.ietf.org/rfc/rfc2109.txt">RFC 2109</a> for details.
     */
    fun isTopPrivateDomain(url: URL): Boolean {
        return url.host == getTopPrivateDomain(url)
    }

    /**
     * Returns the portion of this domain name that is one level beneath the [isPublicSuffix] public suffix.
     * For example, for `x.adwords.google.co.uk` it returns `google.co.uk`, since `co.uk` is a public suffix.
     * Similarly, for `myblog.blogspot.com` it returns the same domain, `myblog.blogspot.com`, since `blogspot.com` is a public suffix.
     *
     * If [isTopPrivateDomain] is true, the current domain name instance is returned.
     *
     * This method can be used to determine the probable highest level parent domain for which cookies may be set,
     * though even that depends on individual browsers' implementations of cookie controls.
     *
     * @throws IllegalStateException if this domain does not end with a public suffix
     */
    @Throws(IllegalStateException::class)
    fun getTopPrivateDomain(url: URL): String {
        return url.toString().toHttpUrl().topPrivateDomain()
            ?: throw IllegalStateException("Not under a public suffix: ${url.host}")
    }

    /**
     * Returns the portion of this domain name that is one level beneath the [isPublicSuffix] public suffix.
     * For example, for `x.adwords.google.co.uk` it returns `google.co.uk`, since `co.uk` is a public suffix.
     * Similarly, for `myblog.blogspot.com` it returns the same domain, `myblog.blogspot.com`, since `blogspot.com` is a public suffix.
     *
     * If [isTopPrivateDomain] is true, the current domain name instance is returned.
     *
     * This method can be used to determine the probable highest level parent domain for which cookies may be set,
     * though even that depends on individual browsers' implementations of cookie controls.
     *
     * @throws IllegalStateException if this domain does not end with a public suffix
     */
    @Throws(IllegalStateException::class, MalformedURLException::class)
    fun getTopPrivateDomain(url: String) = getTopPrivateDomain(URI.create(url).toURL())

    /**
     * Returns the portion of this domain name that is one level beneath the [isPublicSuffix] public suffix.
     * For example, for `x.adwords.google.co.uk` it returns `google.co.uk`, since `co.uk` is a public suffix.
     * Similarly, for `myblog.blogspot.com` it returns the same domain, `myblog.blogspot.com`, since `blogspot.com` is a public suffix.
     *
     * If [isTopPrivateDomain] is true, the current domain name instance is returned.
     *
     * This method can be used to determine the probable highest level parent domain for which cookies may be set,
     * though even that depends on individual browsers' implementations of cookie controls.
     *
     * @throws IllegalStateException if this domain does not end with a public suffix
     */
    fun getTopPrivateDomainOrNull(url: String) = kotlin.runCatching { getTopPrivateDomain(url) }.getOrNull()

    /**
     * Returns the lowercase origin for the url.
     *
     * @param url The url to check.
     * @return String The hostname for the url.
     */
    @Throws(MalformedURLException::class)
    fun getOrigin(url: String): String {
        val u = URI.create(url).toURL()

        val defaultPort = when (u.protocol.lowercase(Locale.getDefault())) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }

        val port = u.port
        return if (port == -1 || (defaultPort != -1 && port == defaultPort)) {
            "${u.protocol}://${u.host}"
        } else {
            "${u.protocol}://${u.host}:$port"
        }
    }

    /**
     * Returns the lowercase origin for the url or null if the url is not well-formed.
     *
     * @param url The url to check.
     * @return String The hostname for the url.
     */
    fun getOriginOrNull(url: String?): String? {
        if (url == null) {
            return null
        }

        return try {
            return getOrigin(url)
        } catch (t: Throwable) {
            null
        }
    }


    /**
     * Returns the lowercase hostname for the url.
     *
     * @param url The url to check.
     * @return String The hostname for the url.
     */
    @Throws(MalformedURLException::class)
    fun getHostName(url: String): String {
        val host = URI.create(url).host
        require(!host.isNullOrBlank()) { "Missing host in url: $url" }
        return host.lowercase(Locale.getDefault())
    }

    /**
     * Returns the lowercase hostname for the url or null if the url is not well-formed.
     *
     * @param url The url to check.
     * @return String The hostname for the url.
     */
    fun getHostNameOrNull(url: String?): String? {
        if (url == null) {
            return null
        }

        return kotlin.runCatching {
            val host = URI.create(url).host ?: return@runCatching null
            host.lowercase(Locale.getDefault())
        }.getOrNull()
    }

    fun getHostName(url: String?, defaultValue: String): String {
        if (url == null) {
            return defaultValue
        }

        return kotlin.runCatching {
            val host = URI.create(url).host
            if (host.isNullOrBlank()) defaultValue else host.lowercase(Locale.getDefault())
        }.getOrDefault(defaultValue)
    }


    private fun reverseAppendSplits(string: String, buf: StringBuilder) {
        val splits = StringUtils.split(string, '.')
        if (splits.isNotEmpty()) {
            for (i in splits.size - 1 downTo 1) {
                buf.append(splits[i])
                buf.append('.')
            }
            buf.append(splits[0])
        } else {
            buf.append(string)
        }
    }
}
