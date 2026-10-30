package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * HTTP client for the Inkomoko Omnichannel API.
 *
 * Every call carries X-API-KEY, X-TIMESTAMP (Unix ms) and
 * X-SIGNATURE = Base64(HMAC-SHA256("{timestamp}\n{sha256hex(body)}", secret)).
 * The server re-serialises the JSON body compactly before hashing, so the body is sent
 * compact and amounts are written in a form that survives a parse/serialise round trip.
 * GET bodies are empty and query strings are not signed.
 *
 * Responses use the envelope {success, message, data, errorCode}; {@link #call} returns {@code data}.
 */
@Slf4j
@Component
public class OmnichannelClient {

    private final InkomokoProperties.Omnichannel cfg;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public OmnichannelClient(InkomokoProperties props) {
        this.cfg = props.getOmnichannel();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .build();
    }

    public String channel() {
        return cfg.getChannel();
    }

    public Object get(String path, Map<String, ?> query) {
        return call("GET", path + queryString(query), null);
    }

    public Object post(String path, Map<String, ?> body) {
        return call("POST", path, body);
    }

    private Object call(String method, String pathAndQuery, Map<String, ?> body) {
        requireConfigured();
        String payload = canonicalBody(body);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = sign(timestamp, payload);
        String endpoint = method + " " + stripSlash(cfg.getBaseUrl()) + pathAndQuery;
        log.info("CBS request  -> {} body={}", endpoint, payload.isEmpty() ? "-" : payload);
        if (log.isDebugEnabled()) {
            log.debug("CBS signing {} bodyHash={} timestamp={} signature={}", endpoint, sha256Hex(payload), timestamp, signature);
        }
        long started = System.nanoTime();

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(stripSlash(cfg.getBaseUrl()) + pathAndQuery))
                .timeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                .header("Accept", "application/json")
                .header("X-API-KEY", cfg.getApiKey())
                .header("X-TIMESTAMP", timestamp)
                .header("X-SIGNATURE", signature);
        if (body == null) {
            req.GET();
        } else {
            req.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }

        HttpResponse<String> res;
        try {
            res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("CBS response <- {} unreachable after {}ms: {}", endpoint, elapsedMs(started), e.toString());
            throw new OmnichannelException("Omnichannel unreachable: " + e.getMessage(), 0, "UNREACHABLE", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OmnichannelException("Omnichannel call interrupted", 0, "INTERRUPTED", e);
        }

        long ms = elapsedMs(started);
        Map<String, Object> envelope = parse(res.body());
        String errorCode = str(envelope.get("errorCode"));
        String message = str(envelope.get("message"));
        boolean ok = res.statusCode() / 100 == 2 && !Boolean.FALSE.equals(envelope.get("success"));
        if (!ok) {
            log.warn("CBS response <- {} http={} ms={} success=false errorCode={} message={} body={}",
                    endpoint, res.statusCode(), ms, errorCode, message, abbreviate(res.body()));
            throw new OmnichannelException(message == null ? "Omnichannel returned HTTP " + res.statusCode() : message,
                    res.statusCode(), errorCode, null);
        }
        log.info("CBS response <- {} http={} ms={} body={}", endpoint, res.statusCode(), ms, abbreviate(res.body()));
        return envelope.get("data");
    }

    /** Compact JSON, identical to JSON.stringify(JSON.parse(body)); "" when there is no body. */
    String canonicalBody(Map<String, ?> body) {
        return body == null ? "" : json.writeValueAsString(body);
    }

    /** Base64(HMAC-SHA256("{timestamp}\n{sha256hex(body)}", secret)). */
    String sign(String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cfg.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((timestamp + "\n" + sha256Hex(body)).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Cannot sign Omnichannel request", e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whole amounts as integers and others as plain decimals, matching the server's re-serialisation. */
    public static Number amount(BigDecimal value) {
        BigDecimal v = value.stripTrailingZeros();
        return v.scale() <= 0 ? (Number) v.longValueExact() : (Number) v.doubleValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try {
            Object v = json.readValue(body, Object.class);
            return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of("data", v);
        } catch (JacksonException e) {
            return Map.of("message", body.length() > 200 ? body.substring(0, 200) : body);
        }
    }

    private void requireConfigured() {
        if (isBlank(cfg.getBaseUrl()) || isBlank(cfg.getApiKey()) || isBlank(cfg.getSecret())) {
            throw new OmnichannelException(
                    "Omnichannel is not configured: set OMNICHANNEL_BASE_URL, OMNICHANNEL_API_KEY and OMNICHANNEL_SECRET",
                    0, "NOT_CONFIGURED", null);
        }
    }

    private static String queryString(Map<String, ?> query) {
        if (query == null || query.isEmpty()) return "";
        StringJoiner q = new StringJoiner("&", "?", "");
        new LinkedHashMap<>(query).forEach((k, v) -> {
            if (v != null) q.add(enc(k) + "=" + enc(v.toString()));
        });
        return q.length() == 1 ? "" : q.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static String abbreviate(String body) {
        if (body == null || body.isBlank()) return "-";
        String oneLine = body.replaceAll("\\s+", " ");
        return oneLine.length() > 1000 ? oneLine.substring(0, 1000) + "...(" + oneLine.length() + " chars)" : oneLine;
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
