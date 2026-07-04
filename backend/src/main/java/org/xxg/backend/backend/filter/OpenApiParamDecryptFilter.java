package org.xxg.backend.backend.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.xxg.backend.backend.service.OpenApiParamEncryptionService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OpenApiParamDecryptFilter extends OncePerRequestFilter {

    private final OpenApiParamEncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenApiParamDecryptFilter(OpenApiParamEncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !matchesProtectedPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpServletRequest effectiveRequest = request;
        ContentCachingRequestWrapper cachingRequest = null;

        if (encryptionService.isRequestEncryptionEnabled()) {
            cachingRequest = new ContentCachingRequestWrapper(request);
            effectiveRequest = cachingRequest;

            String encryptedPayload = extractEncryptedPayload(cachingRequest);
            boolean hasPlainParams = hasPlainQueryParams(cachingRequest);
            boolean hasPlainBody = hasPlainJsonBody(cachingRequest);

            if (encryptedPayload == null || encryptedPayload.isBlank()) {
                if (hasPlainParams || hasPlainBody) {
                    writeError(response, 400, "已开启入参加密，请使用 encrypted_payload 传递 AES-256-CBC 加密后的 JSON 参数");
                    return;
                }
            } else {
                try {
                    Map<String, String> decryptedParams = encryptionService.decryptPayloadToParams(encryptedPayload);
                    byte[] decryptedBody = buildJsonBody(decryptedParams);
                    effectiveRequest = new OpenApiDecryptedRequestWrapper(
                            cachingRequest,
                            decryptedParams,
                            isBodyMethod(cachingRequest) ? decryptedBody : new byte[0]
                    );
                } catch (IllegalArgumentException e) {
                    writeError(response, 400, e.getMessage());
                    return;
                }
            }
        }

        if (!encryptionService.isResponseEncryptionEnabled()) {
            filterChain.doFilter(effectiveRequest, response);
            return;
        }

        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(effectiveRequest, cachingResponse);
        encryptResponseIfNeeded(cachingResponse, response);
    }

    private void encryptResponseIfNeeded(ContentCachingResponseWrapper cachingResponse, HttpServletResponse response)
            throws IOException {
        byte[] content = cachingResponse.getContentAsByteArray();
        if (content.length == 0) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        String contentType = cachingResponse.getContentType();
        if (contentType == null || !contentType.contains("json")) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        String originalBody = new String(content, StandardCharsets.UTF_8).trim();
        if (originalBody.isEmpty()) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(originalBody, new TypeReference<>() {});
            if (Boolean.TRUE.equals(parsed.get(OpenApiParamEncryptionService.RESPONSE_ENCRYPTED_FLAG))) {
                cachingResponse.copyBodyToResponse();
                return;
            }
        } catch (Exception ignored) {
            cachingResponse.copyBodyToResponse();
            return;
        }

        Map<String, Object> encryptedBody = encryptionService.wrapEncryptedResponse(originalBody);
        byte[] encryptedBytes = objectMapper.writeValueAsBytes(encryptedBody);

        response.resetBuffer();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(encryptedBytes.length);
        response.getOutputStream().write(encryptedBytes);
        response.flushBuffer();
    }

    private boolean matchesProtectedPath(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.contains("/v1/")
                || uri.contains("/custom/")
                || uri.matches(".*/cards/use/?$");
    }

    private String extractEncryptedPayload(HttpServletRequest request) throws IOException {
        String fromQuery = request.getParameter(OpenApiParamEncryptionService.PAYLOAD_FIELD);
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery.trim();
        }

        byte[] body = readBodyBytes(request);
        if (body.length == 0) {
            return null;
        }

        String bodyText = new String(body, StandardCharsets.UTF_8).trim();
        if (bodyText.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> json = objectMapper.readValue(bodyText, new TypeReference<>() {});
            Object payload = json.get(OpenApiParamEncryptionService.PAYLOAD_FIELD);
            return payload != null ? String.valueOf(payload).trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readBodyBytes(HttpServletRequest request) throws IOException {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                return content;
            }
            wrapper.getInputStream().readAllBytes();
            return wrapper.getContentAsByteArray();
        }
        return request.getInputStream().readAllBytes();
    }

    private boolean hasPlainQueryParams(HttpServletRequest request) {
        for (String name : request.getParameterMap().keySet()) {
            if (!OpenApiParamEncryptionService.PAYLOAD_FIELD.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPlainJsonBody(HttpServletRequest request) throws IOException {
        if (!isBodyMethod(request)) {
            return false;
        }
        byte[] body = readBodyBytes(request);
        if (body.length == 0) {
            return false;
        }
        String bodyText = new String(body, StandardCharsets.UTF_8).trim();
        if (bodyText.isEmpty() || "{}".equals(bodyText)) {
            return false;
        }
        try {
            Map<String, Object> json = objectMapper.readValue(bodyText, new TypeReference<>() {});
            if (json.isEmpty()) {
                return false;
            }
            return !(json.size() == 1 && json.containsKey(OpenApiParamEncryptionService.PAYLOAD_FIELD));
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isBodyMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private byte[] buildJsonBody(Map<String, String> params) throws IOException {
        Map<String, String> bodyMap = new LinkedHashMap<>(params);
        return objectMapper.writeValueAsBytes(bodyMap);
    }

    private void writePlainError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "success", false,
                "code", status,
                "message", message
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        if (!encryptionService.isResponseEncryptionEnabled()) {
            writePlainError(response, status, message);
            return;
        }
        Map<String, Object> body = Map.of(
                "success", false,
                "code", status,
                "message", message
        );
        String json = objectMapper.writeValueAsString(body);
        Map<String, Object> encryptedBody = encryptionService.wrapEncryptedResponse(json);
        byte[] encryptedBytes = objectMapper.writeValueAsBytes(encryptedBody);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(encryptedBytes.length);
        response.getOutputStream().write(encryptedBytes);
        response.flushBuffer();
    }
}
