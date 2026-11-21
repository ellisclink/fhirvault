package dev.fhirvault;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for HTTP tests of FHIR behaviour, under the noauth profile. SMART has its own tests against a
 * real Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("noauth")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractApiIT {

    protected static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected FhirContext fhirContext;

    protected ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, new HttpHeaders());
    }

    protected ResponseEntity<String> post(String path, IBaseResource body) {
        return exchange(HttpMethod.POST, path, body, new HttpHeaders());
    }

    protected ResponseEntity<String> put(String path, IBaseResource body) {
        return exchange(HttpMethod.PUT, path, body, new HttpHeaders());
    }

    protected ResponseEntity<String> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, new HttpHeaders());
    }

    protected ResponseEntity<String> exchange(HttpMethod method, String path, IBaseResource body, HttpHeaders headers) {
        headers.setAccept(java.util.List.of(FHIR_JSON));
        String json = null;
        if (body != null) {
            headers.setContentType(FHIR_JSON);
            json = fhirContext.newJsonParser().encodeResourceToString(body);
        }
        return rest.exchange("/fhir/" + path, method, new HttpEntity<>(json, headers), String.class);
    }

    protected <T extends IBaseResource> T parse(Class<T> type, ResponseEntity<String> response) {
        return fhirContext.newJsonParser().parseResource(type, response.getBody());
    }
}
