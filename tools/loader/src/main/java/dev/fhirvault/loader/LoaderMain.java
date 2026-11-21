package dev.fhirvault.loader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.Bundle;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command line: loads every *.json transaction bundle in the given files or directories.
 *
 * <pre>
 * java -jar fhirvault-loader.jar [--server URL] [--token-url URL] [--client-id ID] [--no-auth] PATH...
 * </pre>
 * The client secret comes from FHIRVAULT_LOADER_CLIENT_SECRET so it never shows up in shell history
 * or process lists.
 */
public final class LoaderMain {

    private LoaderMain() {
    }

    public static void main(String[] args) throws IOException {
        String server = "http://localhost:8080/fhir";
        String tokenUrl = "http://localhost:8081/realms/fhirvault/protocol/openid-connect/token";
        String clientId = "fhirvault-loader";
        boolean auth = true;
        List<Path> inputs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server" -> server = args[++i];
                case "--token-url" -> tokenUrl = args[++i];
                case "--client-id" -> clientId = args[++i];
                case "--no-auth" -> auth = false;
                case "--help", "-h" -> {
                    usage();
                    return;
                }
                default -> inputs.add(Path.of(args[i]));
            }
        }
        if (inputs.isEmpty()) {
            usage();
            System.exit(2);
        }

        FhirContext fhir = FhirContext.forR4();
        fhir.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        fhir.getRestfulClientFactory().setSocketTimeout(300_000);
        IGenericClient client = fhir.newRestfulGenericClient(server);
        if (auth) {
            String secret = System.getenv("FHIRVAULT_LOADER_CLIENT_SECRET");
            if (secret == null || secret.isBlank()) {
                System.err.println("Set FHIRVAULT_LOADER_CLIENT_SECRET, or use --no-auth against a noauth server");
                System.exit(2);
            }
            client.registerInterceptor(new BearerFromTokenClient(new TokenClient(URI.create(tokenUrl), clientId, secret, null)));
        }

        SyntheaBundleTransformer transformer = new SyntheaBundleTransformer(fhir);
        int ok = 0;
        int failed = 0;
        for (Path file : bundleFiles(inputs)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Bundle source = fhir.newJsonParser().parseResource(Bundle.class, reader);
                SyntheaBundleTransformer.Result result = transformer.transform(source);
                long start = System.nanoTime();
                client.transaction().withBundle(result.bundle()).execute();
                System.out.printf("OK   %s: %d entries (%d dropped, %d references flattened, %d medications inlined) in %d ms%n",
                        file.getFileName(), result.kept(), result.dropped(), result.referencesFlattened(),
                        result.medicationsInlined(), (System.nanoTime() - start) / 1_000_000);
                ok++;
            } catch (BaseServerResponseException e) {
                // The OperationOutcome names the failing entry; print only the summary line.
                System.out.printf("FAIL %s: HTTP %d %s%n", file.getFileName(), e.getStatusCode(), firstLine(e.getMessage()));
                failed++;
            }
        }
        System.out.printf("Loaded %d bundle(s), %d failed%n", ok, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static List<Path> bundleFiles(List<Path> inputs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                try (Stream<Path> stream = Files.list(input)) {
                    stream.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
                }
            } else {
                files.add(input);
            }
        }
        return files;
    }

    private static String firstLine(String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }

    private static void usage() {
        System.out.println("Usage: java -jar fhirvault-loader.jar [--server URL] [--token-url URL] [--client-id ID] [--no-auth] PATH...");
        System.out.println("  PATH: Synthea transaction bundle files or directories of them (*.json)");
        System.out.println("  Secret: environment variable FHIRVAULT_LOADER_CLIENT_SECRET");
    }

    /** Adds a fresh bearer token to every request. */
    private record BearerFromTokenClient(TokenClient tokens) implements IClientInterceptor {

        @Override
        public void interceptRequest(IHttpRequest request) {
            request.addHeader("Authorization", "Bearer " + tokens.token());
        }

        @Override
        public void interceptResponse(IHttpResponse response) {
        }
    }
}
