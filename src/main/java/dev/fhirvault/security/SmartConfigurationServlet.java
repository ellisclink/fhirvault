package dev.fhirvault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Serves {@code /fhir/.well-known/smart-configuration}. A separate servlet with an exact mapping,
 * because the path is under /fhir/* but isn't a FHIR interaction.
 */
public class SmartConfigurationServlet extends HttpServlet {

    private final SmartConfiguration configuration;
    private final ObjectMapper objectMapper;

    public SmartConfigurationServlet(SmartConfiguration configuration, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (configuration.issuer().isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No authorization server is configured");
            return;
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), configuration.document());
    }
}
