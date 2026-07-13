package ai.platon.pulsar.persist;

import ai.platon.pulsar.common.DateTimes;
import ai.platon.pulsar.common.HttpHeaders;
import ai.platon.pulsar.common.SParser;
import java.util.Collection;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProtocolHeaders implements HttpHeaders {

    private static final Pattern[] FILENAME_PATTERNS = {
            Pattern.compile("\\bfilename=['\"](.+?)['\"]"),
            Pattern.compile("\\bfilename=(\\S+)\\b")
    };

    private final Map<CharSequence, CharSequence> headers;

    private ProtocolHeaders(Map<CharSequence, CharSequence> headers) {
        this.headers = headers;
    }

    public static ProtocolHeaders box(Map<CharSequence, CharSequence> headers) {
        return new ProtocolHeaders(headers);
    }

    public Map<CharSequence, CharSequence> unbox() {
        return headers;
    }

    public String get(String name) {
        CharSequence value = headers.get(JPersistUtils.u8(name));
        return value == null ? null : value.toString();
    }

    public String getOrDefault(String name, String defaultValue) {
        CharSequence value = headers.get(JPersistUtils.u8(name));
        return value == null ? defaultValue : value.toString();
    }

    public void put(String name, String value) {
        headers.put(JPersistUtils.u8(name), JPersistUtils.u8(value));
    }

    public void putAll(Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Puts all entries from a multi-valued map (map of key to collection of values).
     */
    public void putAllMulti(Map<String, ? extends Collection<String>> map) {
        for (Map.Entry<String, ? extends Collection<String>> entry : map.entrySet()) {
            for (String value : entry.getValue()) {
                put(entry.getKey(), value);
            }
        }
    }

    public void remove(String name) {
        headers.remove(JPersistUtils.u8(name));
    }

    public Instant getLastModified() {
        String lastModified = get(HttpHeaders.LAST_MODIFIED);
        if (lastModified != null) {
            return DateTimes.parseHttpDateTime(lastModified, Instant.EPOCH);
        }

        return Instant.EPOCH;
    }

    public long getContentLength() {
        String length = get(HttpHeaders.CONTENT_LENGTH);
        if (length == null) {
            return -1L;
        }

        return SParser.wrap(length.trim()).getLong(-1L);
    }

    public String getDispositionFilename() {
        CharSequence contentDisposition = get(HttpHeaders.CONTENT_DISPOSITION);
        if (contentDisposition == null) {
            return null;
        }

        String dispositionStr = contentDisposition.toString();
        for (Pattern pattern : FILENAME_PATTERNS) {
            Matcher matcher = pattern.matcher(dispositionStr);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    public String getDecodedDispositionFilename() {
        return getDecodedDispositionFilename(StandardCharsets.UTF_8);
    }

    public String getDecodedDispositionFilename(Charset charset) {
        String filename = getDispositionFilename();

        if (filename != null) {
            return URLDecoder.decode(filename, charset);
        }

        return null;
    }

    public void clear() {
        headers.clear();
    }

    public Map<String, String> asStringMap() {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString(), (e, e2) -> e));
    }

    @Override
    public String toString() {
        return headers.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }
}
