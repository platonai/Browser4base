package ai.platon.pulsar.persist;

import ai.platon.pulsar.common.DateTimes;
import ai.platon.pulsar.common.HttpHeaders;
import ai.platon.pulsar.common.SParser;
import com.google.common.collect.Multimap;

import java.io.UnsupportedEncodingException;
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
            Pattern.compile("\\bfilename=['\"](.+)['\"]"),
            Pattern.compile("\\bfilename=(\\S+)\\b")
    };

    private final Map<String, String> headers;

    private ProtocolHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public static ProtocolHeaders box(Map<String, String> headers) {
        return new ProtocolHeaders(headers);
    }

    public Map<String, String> unbox() {
        return headers;
    }

    public String get(String name) {
        return headers.get(name);
    }

    public void put(String name, String value) {
        headers.put(name, value);
    }

    public void putAll(Multimap<String, String> map) {
        for (Map.Entry<String, String> entry : map.entries()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public void remove(String name) {
        headers.remove(name);
    }

    public Instant getLastModified() {
        String lastModified = get(HttpHeaders.LAST_MODIFIED);
        if (lastModified != null) {
            return DateTimes.parseHttpDateTime(lastModified, Instant.EPOCH);
        }

        return Instant.EPOCH;
    }

    public int getContentLength() {
        String length = get(HttpHeaders.CONTENT_LENGTH);
        if (length == null) {
            return -1;
        }

        return SParser.wrap(length.trim()).getInt(-1);
    }

    public String getDispositionFilename() {
        String contentDisposition = get(HttpHeaders.CONTENT_DISPOSITION);
        if (contentDisposition == null) {
            return null;
        }

        String dispositionStr = contentDisposition;
        for (Pattern pattern : FILENAME_PATTERNS) {
            Matcher matcher = pattern.matcher(dispositionStr);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    public String getDecodedDispositionFilename() {
        try {
            return getDecodedDispositionFilename(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unexpected unsupported encoding `UTF-8`");
        }
    }

    public String getDecodedDispositionFilename(Charset charset) throws UnsupportedEncodingException {
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
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString(), (e, e2) -> e));
    }

    @Override
    public String toString() {
        return headers.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }
}
