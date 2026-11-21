package dev.fhirvault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** A real Keycloak, started once per JVM, importing the Compose realm file with random secrets. */
public final class KeycloakTestSupport {

    public static final String LOADER_SECRET = UUID.randomUUID().toString();
    public static final String DEMO_PASSWORD = UUID.randomUUID().toString();

    @SuppressWarnings("resource")
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.0")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", UUID.randomUUID().toString())
            .withEnv("FHIRVAULT_LOADER_CLIENT_SECRET", LOADER_SECRET)
            .withEnv("FHIRVAULT_DEMO_PASSWORD", DEMO_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("deploy/keycloak/fhirvault-realm.json"),
                    "/opt/keycloak/data/import/fhirvault-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/fhirvault/.well-known/openid-configuration").forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private KeycloakTestSupport() {
    }

    public static synchronized String issuer() {
        if (!KEYCLOAK.isRunning()) {
            KEYCLOAK.start();
        }
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/fhirvault";
    }

    public static String systemToken() {
        return token(Map.of("grant_type", "client_credentials", "client_id", "fhirvault-loader",
                "client_secret", LOADER_SECRET));
    }

    public static String userToken(String username, String scopes) {
        return token(Map.of("grant_type", "password", "client_id", "fhirvault-tester",
                "username", username, "password", DEMO_PASSWORD, "scope", scopes));
    }

    private static String token(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Token request failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode json = JSON.readTree(response.body());
            return json.get("access_token").asText();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
