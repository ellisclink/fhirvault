package dev.fhirvault.config;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fhirvault.fhir.FhirRestfulServer;
import dev.fhirvault.security.SmartConfiguration;
import dev.fhirvault.security.SmartConfigurationServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class FhirConfig {

    // Building a FhirContext scans every R4 model class, so build one and share it. It is thread-safe.
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    /** A bean so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ServletRegistrationBean<FhirRestfulServer> fhirServletRegistration(FhirRestfulServer server) {
        ServletRegistrationBean<FhirRestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("fhirServlet");
        // Start the servlet at boot so configuration errors fail fast.
        registration.setLoadOnStartup(1);
        return registration;
    }

    /** An exact mapping beats HAPI's /fhir/* prefix mapping. */
    @Bean
    public ServletRegistrationBean<SmartConfigurationServlet> smartConfigurationServlet(
            SmartConfiguration configuration, ObjectMapper objectMapper) {
        ServletRegistrationBean<SmartConfigurationServlet> registration = new ServletRegistrationBean<>(
                new SmartConfigurationServlet(configuration, objectMapper), "/fhir/.well-known/smart-configuration");
        registration.setName("smartConfigurationServlet");
        return registration;
    }
}
