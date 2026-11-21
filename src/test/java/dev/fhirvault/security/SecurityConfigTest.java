package dev.fhirvault.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests only the HTTP security decision. The FHIR servlet isn't loaded, so a request that passes ends
 * in 404.
 */
class SecurityConfigTest {

    @Nested
    @WebMvcTest
    @Import(SecurityConfig.class)
    class DefaultProfile {

        @MockitoBean
        JwtDecoder jwtDecoder;

        @Autowired
        MockMvc mvc;

        @Test
        void rejectsRequestWithoutTokenWithOperationOutcome() throws Exception {
            mvc.perform(get("/fhir/Patient"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"OperationOutcome\"")));
        }

        @Test
        void acceptsRequestWithJwt() throws Exception {
            mvc.perform(get("/fhir/Patient").with(jwt())).andExpect(status().isNotFound());
        }

        @Test
        void discoveryEndpointsArePublic() throws Exception {
            mvc.perform(get("/fhir/metadata")).andExpect(status().isNotFound());
            mvc.perform(get("/fhir/.well-known/smart-configuration")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @WebMvcTest
    @Import(SecurityConfig.class)
    @ActiveProfiles("noauth")
    class NoAuthProfile {

        @Autowired
        MockMvc mvc;

        @Test
        void allowsRequestWithoutToken() throws Exception {
            mvc.perform(get("/fhir/Patient")).andExpect(status().isNotFound());
        }
    }
}
