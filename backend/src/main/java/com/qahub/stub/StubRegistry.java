package com.qahub.stub;

import com.qahub.config.QaHubProperties;
import com.qahub.config.QaHubProperties.Endpoint;
import com.qahub.config.ResourceReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Loads every endpoint from YAML once at startup. Change YAML/JSON, restart, done. */
@Component
public class StubRegistry {

    private static final Logger log = LoggerFactory.getLogger(StubRegistry.class);

    private final List<CompiledEndpoint> endpoints;

    public StubRegistry(QaHubProperties props, ResourceReader reader) {
        List<CompiledEndpoint> list = new ArrayList<>();
        for (Endpoint e : props.stub().endpoints()) {
            PathPattern pattern = PathPatternParser.defaultInstance.parse(e.path());
            List<Map<String, Object>> data = e.data() != null ? reader.readRecords(e.data()) : List.of();
            CompiledEndpoint ce = new CompiledEndpoint(
                    e, pattern, data,
                    reader.readJsonOrNull(e.sampleRequest()),
                    reader.readJsonOrNull(e.sampleResponse()),
                    reader.readErrorBodies(e.errors()));
            list.add(ce);
            log.info("Stub ready: {} {}{} ({} records, error bodies for {})",
                    e.method(), props.stub().basePath(), e.path(), data.size(), ce.errorBodies().keySet());
        }
        this.endpoints = Collections.unmodifiableList(list);
    }

    public List<CompiledEndpoint> all() {
        return endpoints;
    }

    /**
     * @return the endpoint for method+path, or a Lookup with endpoint=null.
     * pathExists=true in that case means the path is known but the method is wrong (405).
     */
    public Lookup find(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        boolean pathExists = false;
        for (CompiledEndpoint ce : endpoints) {
            PathPattern.PathMatchInfo info = ce.pattern().matchAndExtract(container);
            if (info == null) continue;
            pathExists = true;
            if (ce.def().method().equalsIgnoreCase(method)) {
                return new Lookup(ce, info.getUriVariables(), true);
            }
        }
        return new Lookup(null, Map.of(), pathExists);
    }

    public record Lookup(CompiledEndpoint endpoint, Map<String, String> pathVariables, boolean pathExists) {}
}
