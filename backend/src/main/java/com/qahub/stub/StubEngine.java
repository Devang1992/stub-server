package com.qahub.stub;

import com.qahub.config.QaHubProperties;
import com.qahub.config.QaHubProperties.Auth;
import com.qahub.config.QaHubProperties.Endpoint;
import com.qahub.config.QaHubProperties.HeaderSpec;
import com.qahub.config.QaHubProperties.ParamSpec;
import com.qahub.config.ResourceReader;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Request pipeline for every call under the stub base path:
 * find endpoint -> x-delay-ms -> x-http override -> auth -> headers -> params -> data lookup -> record override -> response.
 */
@Component
public class StubEngine {

    private static final Logger log = LoggerFactory.getLogger(StubEngine.class);

    private final QaHubProperties.Stub config;
    private final StubRegistry registry;
    private final ResourceReader reader;

    public StubEngine(QaHubProperties props, StubRegistry registry, ResourceReader reader) {
        this.config = props.stub();
        this.registry = registry;
        this.reader = reader;
    }

    public ResponseEntity<Object> handle(HttpServletRequest req, String body) {
        String uri = req.getRequestURI().substring(req.getContextPath().length());
        String path = uri.length() > config.basePath().length() ? uri.substring(config.basePath().length()) : "/";
        String method = req.getMethod();

        StubRegistry.Lookup lookup = registry.find(method, path);
        CompiledEndpoint ce = lookup.endpoint();
        if (ce == null) {
            int code = lookup.pathExists() ? 405 : 404;
            String msg = lookup.pathExists()
                    ? method + " is not supported on " + path
                    : "No stub endpoint for " + method + " " + path + ". Check the Stubs page for available endpoints.";
            return error(null, code, msg, uri);
        }
        Endpoint def = ce.def();
        log.info("Stub hit: {} {} [{}]", method, uri, def.id());

        // 1. Optional artificial delay from header
        String delay = req.getHeader(config.delayHeader());
        if (delay != null && !delay.isBlank()) {
            try {
                sleep(Long.parseLong(delay.trim()));
            } catch (NumberFormatException e) {
                return error(ce, 400, config.delayHeader() + " must be a number of milliseconds", uri);
            }
        }

        // 2. Forced status code: x-http wins over everything else
        Integer forced = null;
        String forcedRaw = req.getHeader(config.statusHeader());
        if (forcedRaw != null && !forcedRaw.isBlank()) {
            try {
                forced = Integer.parseInt(forcedRaw.trim());
            } catch (NumberFormatException e) {
                return error(ce, 400, config.statusHeader() + " must be an HTTP status code like 503", uri);
            }
            if (forced < 200 || forced > 599) {
                return error(ce, 400, config.statusHeader() + " must be between 200 and 599", uri);
            }
            if (forced >= 300) {
                return error(ce, forced, "Forced by " + config.statusHeader() + " header", uri);
            }
        }

        // 3. Auth
        Auth auth = def.auth() != null ? def.auth() : config.auth();
        ResponseEntity<Object> authFailure = checkAuth(ce, auth, req, uri);
        if (authFailure != null) return authFailure;

        // 4. Headers
        for (HeaderSpec h : def.headers()) {
            String value = req.getHeader(h.name());
            if ((value == null || value.isBlank()) && h.required()) {
                return error(ce, 400, "Missing required header " + h.name(), uri);
            }
            if (value != null && !h.allowed().isEmpty() && !h.allowed().contains(value)) {
                return error(ce, 400, "Header " + h.name() + " must be one of " + h.allowed(), uri);
            }
        }

        // 5. Params
        Map<String, Object> jsonBody = null;
        if (def.params().stream().anyMatch(p -> "body".equals(p.in()))) {
            if (body == null || body.isBlank()) {
                jsonBody = Map.of();
            } else {
                try {
                    Object parsed = reader.mapper().readValue(body, Object.class);
                    if (!(parsed instanceof Map<?, ?>)) return error(ce, 400, "Request body must be a JSON object", uri);
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) parsed;
                    jsonBody = m;
                } catch (RuntimeException e) {
                    return error(ce, 400, "Request body is not valid JSON", uri);
                }
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (ParamSpec p : def.params()) {
            String v = switch (p.in()) {
                case "path" -> lookup.pathVariables().get(p.name());
                case "header" -> req.getHeader(p.name());
                case "body" -> jsonBody.get(p.name()) == null ? null : String.valueOf(jsonBody.get(p.name()));
                default -> req.getParameter(p.name());
            };
            if (v == null || v.isBlank()) {
                if (p.required()) missing.add(p.name());
            } else {
                values.put(p.name(), v);
            }
        }
        if (!missing.isEmpty()) {
            return error(ce, 400, "Missing required parameter(s): " + String.join(", ", missing), uri);
        }

        int okStatus = forced != null ? forced : 200;

        // 6. No data file: just return the sample response
        if (ce.data().isEmpty()) {
            return success(okStatus, ce.sampleResponse());
        }

        // 7. Data lookup
        List<Map<String, Object>> matches = ce.data().stream()
                .filter(r -> def.params().stream().allMatch(p -> {
                    String v = values.get(p.name());
                    return v == null || v.equals(String.valueOf(r.get(p.field())));
                }))
                .toList();

        if ("list".equals(def.result())) {
            return success(okStatus, matches.stream().map(r -> shape(def, r)).toList());
        }
        if (matches.isEmpty()) {
            return error(ce, 404, "No record found for " + values, uri);
        }

        // 8. Per-record behaviour (_delayMs, _status) from the data file
        Map<String, Object> record = matches.getFirst();
        if (record.get("_delayMs") instanceof Number n) {
            sleep(n.longValue());
        }
        if (forced == null && record.get("_status") instanceof Number n && n.intValue() >= 300) {
            return error(ce, n.intValue(), "Forced by test data record " + values, uri);
        }
        return success(okStatus, shape(def, record));
    }

    private ResponseEntity<Object> checkAuth(CompiledEndpoint ce, Auth auth, HttpServletRequest req, String uri) {
        if ("none".equals(auth.type())) return null;
        String value = req.getHeader(auth.header());
        if (value == null || value.isBlank()) {
            return error(ce, 401, "Missing " + auth.header() + " header (" + auth.type() + " auth)", uri);
        }
        switch (auth.type()) {
            case "bearer" -> {
                if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    return error(ce, 401, auth.header() + " must start with 'Bearer '", uri);
                }
                String token = value.substring(7).trim();
                if (!auth.tokens().isEmpty() && !auth.tokens().contains(token)) {
                    return error(ce, 403, "Token not accepted", uri);
                }
            }
            case "basic" -> {
                if (!value.regionMatches(true, 0, "Basic ", 0, 6)) {
                    return error(ce, 401, auth.header() + " must start with 'Basic '", uri);
                }
                String decoded;
                try {
                    decoded = new String(Base64.getDecoder().decode(value.substring(6).trim()), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    return error(ce, 401, "Basic credentials are not valid base64", uri);
                }
                if (!auth.users().isEmpty() && !auth.users().contains(decoded)) {
                    return error(ce, 403, "Credentials not accepted", uri);
                }
            }
            case "api-key" -> {
                if (!auth.tokens().isEmpty() && !auth.tokens().contains(value.trim())) {
                    return error(ce, 403, "API key not accepted", uri);
                }
            }
            default -> log.warn("Unknown auth type '{}', allowing request", auth.type());
        }
        return null;
    }

    /** Strips "_" keys, or returns only responseField when configured. */
    private Object shape(Endpoint def, Map<String, Object> record) {
        if (def.responseField() != null) return record.get(def.responseField());
        Map<String, Object> copy = new LinkedHashMap<>();
        record.forEach((k, v) -> { if (!k.startsWith("_")) copy.put(k, v); });
        return copy;
    }

    private ResponseEntity<Object> success(int status, Object body) {
        if (status == 204 || body == null) return ResponseEntity.status(status).build();
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private ResponseEntity<Object> error(CompiledEndpoint ce, int status, String message, String path) {
        String template = ce != null ? ce.errorBodies().get(status) : null;
        if (template != null) {
            String body = template
                    .replace("{{status}}", String.valueOf(status))
                    .replace("{{message}}", jsonEscape(message))
                    .replace("{{path}}", jsonEscape(path))
                    .replace("{{timestamp}}", Instant.now().toString());
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }
        HttpStatus known = HttpStatus.resolve(status);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status);
        m.put("error", known != null ? known.getReasonPhrase() : "Status " + status);
        m.put("message", message);
        m.put("path", path);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(m);
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(Math.min(ms, 120_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
