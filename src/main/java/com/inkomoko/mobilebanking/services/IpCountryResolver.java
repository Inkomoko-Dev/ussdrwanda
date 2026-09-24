package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.utils.ClientIp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort country for a public IP. Local and private addresses are not looked up. */
@Slf4j
@Component
public class IpCountryResolver {

    private static final Pattern COUNTRY = Pattern.compile("\"country\"\\s*:\\s*\"([^\"]+)\"");
    private static final String UNKNOWN = "";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(600)).build();
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String countryOf(String ip) {
        if (ip == null || ip.isBlank() || ClientIp.isLocalOrPrivate(ip)) return null;
        String cached = cache.get(ip);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String country = lookup(ip);
        cache.put(ip, country == null ? UNKNOWN : country);
        return country;
    }

    private String lookup(String ip) {
        try {
            String encoded = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://ipwho.is/" + encoded))
                    .timeout(Duration.ofMillis(800))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || !response.body().contains("\"success\":true")) {
                return null;
            }
            Matcher matcher = COUNTRY.matcher(response.body());
            if (!matcher.find()) return null;
            String country = matcher.group(1).trim();
            return country.isEmpty() ? null : country;
        } catch (Exception e) {
            log.debug("Country lookup failed for {}: {}", ip, e.getMessage());
            return null;
        }
    }
}
