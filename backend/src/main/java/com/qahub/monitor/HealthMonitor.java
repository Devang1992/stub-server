package com.qahub.monitor;

import com.qahub.config.QaHubProperties;
import com.qahub.config.QaHubProperties.App;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Pings every app's actuator health URL on a fixed interval, all apps in parallel. */
@Component
public class HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitor.class);
    private static final int HISTORY = 20;

    private final List<App> apps;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public HealthMonitor(QaHubProperties props, ObjectMapper mapper) {
        this.apps = props.monitor().apps();
        this.mapper = mapper;
        Duration timeout = Duration.ofMillis(props.monitor().timeoutMs());
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(factory).build();
        apps.forEach(a -> entries.put(a.name(), new Entry()));
    }

    @Scheduled(fixedRateString = "${qa-hub.monitor.interval-ms:30000}", initialDelay = 1000)
    public void pingAll() {
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (App app : apps) {
                ex.submit(() -> ping(app));
            }
        }
    }

    private void ping(App app) {
        long start = System.nanoTime();
        Entry entry = entries.get(app.name());
        try {
            Result r = client.get()
                    .uri(URI.create(app.url()))
                    .headers(h -> app.headers().forEach(h::set))
                    .exchange((request, response) -> new Result(
                            response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
            long ms = (System.nanoTime() - start) / 1_000_000;
            String reported = readStatus(r.body());
            boolean up = r.code() >= 200 && r.code() < 300 && (reported == null || "UP".equalsIgnoreCase(reported));
            String detail = up ? "UP"
                    : reported != null ? "Health reported " + reported + " (HTTP " + r.code() + ")"
                    : "HTTP " + r.code();
            entry.record(up, r.code(), ms, detail);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            entry.record(false, null, ms, "Unreachable: " + msg);
            log.debug("Ping failed for {}: {}", app.name(), msg);
        }
    }

    private String readStatus(String body) {
        try {
            Object v = mapper.readValue(body, Object.class);
            if (v instanceof Map<?, ?> m && m.get("status") != null) return m.get("status").toString();
        } catch (RuntimeException ignored) {
            // not JSON, treat by HTTP code only
        }
        return null;
    }

    public List<AppStatus> snapshot() {
        List<AppStatus> out = new ArrayList<>();
        for (App app : apps) {
            out.add(entries.get(app.name()).view(app));
        }
        return out;
    }

    private record Result(int code, String body) {}

    public record AppStatus(String name, String url, String description, String state,
                            Integer httpStatus, Long latencyMs, Instant lastChecked,
                            Instant lastUp, String detail, List<Boolean> history) {}

    private static final class Entry {
        private String state = "UNKNOWN";
        private Integer httpStatus;
        private Long latencyMs;
        private Instant lastChecked;
        private Instant lastUp;
        private String detail = "Waiting for first check";
        private final Deque<Boolean> history = new ArrayDeque<>();

        synchronized void record(boolean up, Integer code, long ms, String detail) {
            this.state = up ? "UP" : "DOWN";
            this.httpStatus = code;
            this.latencyMs = ms;
            this.lastChecked = Instant.now();
            if (up) this.lastUp = this.lastChecked;
            this.detail = detail;
            history.addLast(up);
            while (history.size() > HISTORY) history.removeFirst();
        }

        synchronized AppStatus view(App app) {
            return new AppStatus(app.name(), app.url(), app.description(), state, httpStatus,
                    latencyMs, lastChecked, lastUp, detail, List.copyOf(history));
        }
    }
}
