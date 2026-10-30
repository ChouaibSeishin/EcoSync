package io.ecosync.web;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component
public class HttpErrors {
    private final ObjectMapper mapper;
    public HttpErrors(ObjectMapper mapper) { this.mapper = mapper; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        Object id = request.getAttribute("requestId");
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), new ApiError(status, code, message,
            id == null ? UUID.randomUUID().toString() : id.toString()));
    }
}
