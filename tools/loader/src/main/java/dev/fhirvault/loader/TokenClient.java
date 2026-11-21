package dev.fhirvault.loader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth 2.0 client-credentials grant with a client secret. Caches the token and refreshes it shortly
 * before expiry.
 */
public class TokenClient {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");

    private final HttpClient http = HttpClient.newHttpClient();
    private final URI tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public TokenClient(URI tokenUrl, String clientId, String clientSecret, String scope) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    public synchronized String token() {
        if (token == null || Instant.now().isAfter(expiresAt.minusSeconds(30))) {
            refresh();
        }
        return token;
    }

    private void refresh() {
        String form = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + (scope == null || scope.isBlank() ? "" : "&scope=" + encode(scope));
        HttpRequest request = HttpRequest.newBuilder(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // The body can echo request details, so report only the status.
                throw new IllegalStateException("Token request failed with HTTP " + response.statusCode());
            }
            Matcher tokenMatch = ACCESS_TOKEN.matcher(response.body());
            if (!tokenMatch.find()) {
                throw new IllegalStateException("Token response has no access_token");
            }
            token = tokenMatch.group(1);
            Matcher expiry = EXPIRES_IN.matcher(response.body());
            expiresAt = Instant.now().plusSeconds(expiry.find() ? Long.parseLong(expiry.group(1)) : 60);
        } catch (IOException e) {
            throw new IllegalStateException("Token request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
