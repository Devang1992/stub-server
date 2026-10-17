package com.qahub.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under "qa-hub:" in stubs.yml, monitor.yml and samples.yml binds here.
 * Defaults live in the compact constructors so the YAML can stay short.
 */
@ConfigurationProperties(prefix = "qa-hub")
public record QaHubProperties(Stub stub, Monitor monitor, List<Sample> samples) {

    public QaHubProperties {
        stub = stub != null ? stub : new Stub(null, null, null, null, null);
        monitor = monitor != null ? monitor : new Monitor(null, null, null);
        samples = samples != null ? samples : List.of();
    }

    /** Stub server settings. */
    public record Stub(String basePath, String statusHeader, String delayHeader,
                       Auth auth, List<Endpoint> endpoints) {
        public Stub {
            basePath = basePath != null ? basePath : "/stub";
            statusHeader = statusHeader != null ? statusHeader : "x-http";
            delayHeader = delayHeader != null ? delayHeader : "x-delay-ms";
            auth = auth != null ? auth : new Auth(null, null, null, null);
            endpoints = endpoints != null ? endpoints : List.of();
        }
    }

    /**
     * type: none | bearer | basic | api-key.
     * tokens: accepted bearer tokens / api keys (empty = any value accepted, presence only).
     * users: accepted "user:password" pairs for basic (empty = any).
     */
    public record Auth(String type, String header, List<String> tokens, List<String> users) {
        public Auth {
            type = type != null ? type.toLowerCase() : "none";
            header = header != null ? header : ("api-key".equals(type) ? "X-API-Key" : "Authorization");
            tokens = tokens != null ? tokens : List.of();
            users = users != null ? users : List.of();
        }
    }

    public record Endpoint(String id, String name, String description, String method, String path,
                           Auth auth, List<HeaderSpec> headers, List<ParamSpec> params,
                           String data, String result, String responseField,
                           String sampleRequest, String sampleResponse, String errors,
                           List<Integer> supportedCodes) {
        public Endpoint {
            method = method != null ? method.toUpperCase() : "GET";
            headers = headers != null ? headers : List.of();
            params = params != null ? params : List.of();
            result = result != null ? result.toLowerCase() : "single";
            supportedCodes = supportedCodes != null ? supportedCodes : List.of(200, 400, 401, 404, 500);
        }
    }

    public record HeaderSpec(String name, Boolean required, String description,
                             List<String> allowed, String example) {
        public HeaderSpec {
            required = required != null && required;
            allowed = allowed != null ? allowed : List.of();
        }
    }

    /** in: query | path | header | body. field: the key in the data file this param matches on. */
    public record ParamSpec(String name, String in, Boolean required, String field,
                            String description, String example) {
        public ParamSpec {
            in = in != null ? in.toLowerCase() : "query";
            required = required != null && required;
            field = field != null ? field : name;
        }
    }

    public record Monitor(Long intervalMs, Long timeoutMs, List<App> apps) {
        public Monitor {
            intervalMs = intervalMs != null ? intervalMs : 30_000L;
            timeoutMs = timeoutMs != null ? timeoutMs : 5_000L;
            apps = apps != null ? apps : List.of();
        }
    }

    public record App(String name, String url, String description, Map<String, String> headers) {
        public App {
            headers = headers != null ? headers : Map.of();
        }
    }

    public record Sample(String name, String app, String description, String path) {}
}
