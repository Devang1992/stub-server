package com.qahub.monitor;

import com.qahub.config.QaHubProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final HealthMonitor monitor;
    private final long intervalMs;

    public StatusController(HealthMonitor monitor, QaHubProperties props) {
        this.monitor = monitor;
        this.intervalMs = props.monitor().intervalMs();
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of("intervalMs", intervalMs, "apps", monitor.snapshot());
    }

    /** "Check now" button: ping immediately instead of waiting for the next cycle. */
    @PostMapping("/api/status/refresh")
    public Map<String, Object> refresh() {
        monitor.pingAll();
        return status();
    }
}
