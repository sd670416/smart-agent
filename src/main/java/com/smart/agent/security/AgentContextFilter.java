package com.smart.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.api.ApiError;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.common.trace.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AgentContextFilter extends OncePerRequestFilter {
    public static final String CONTEXT_HEADER = "X-Agent-Context";

    private final ContextTokenVerifier verifier;
    private final ObjectMapper objectMapper;

    public AgentContextFilter(ContextTokenVerifier verifier, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/agent") || path.startsWith("/agent/"));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = request.getHeader(CONTEXT_HEADER);
            if (token == null || token.isBlank()) {
                writeUnauthorized(request, response);
                return;
            }
            AgentUserContext context;
            try {
                context = verifier.verify(token);
                if (context == null) {
                    throw AgentException.unauthorized();
                }
            } catch (AgentException exception) {
                // Do not expose verifier-specific status or message details at the trust boundary.
                writeUnauthorized(request, response);
                return;
            } catch (RuntimeException exception) {
                writeUnauthorized(request, response);
                return;
            }
            request.setAttribute(AgentUserContext.class.getName(), context);
            AgentContextHolder.set(context);
            filterChain.doFilter(request, response);
        } finally {
            AgentContextHolder.clear();
            request.removeAttribute(AgentUserContext.class.getName());
        }
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeError(request, response, AgentException.unauthorized());
    }

    private void writeError(
            HttpServletRequest request, HttpServletResponse response, AgentException exception) throws IOException {
        response.setStatus(exception.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiError(exception.code(), exception.getMessage(), traceId));
    }
}
