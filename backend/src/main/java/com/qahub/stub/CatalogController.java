package com.qahub.stub;

import com.qahub.config.QaHubProperties;
import com.qahub.config.QaHubProperties.Endpoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The frontend reads the stub YAML through here, so nothing is copied by hand. */
@RestController
public class CatalogController {

    private final QaHubProperties props;
    private final StubRegistry registry;

    public CatalogController(QaHubProperties props, StubRegistry registry) {
        this.props = props;
        this.registry = registry;
    }

    @GetMapping("/api/catalog")
    public Map<String, Object> catalog() {
        QaHubProperties.Stub stub = props.stub();
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (CompiledEndpoint ce : registry.all()) {
            Endpoint e = ce.def();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("name", e.name());
            m.put("description", e.description());
            m.put("method", e.method());
            m.put("path", e.path());
            m.put("url", stub.basePath() + e.path());
            m.put("auth", e.auth() != null ? e.auth() : stub.auth());
            m.put("headers", e.headers());
            m.put("params", e.params());
            m.put("result", e.result());
            m.put("responseField", e.responseField());
            m.put("supportedCodes", e.supportedCodes());
            m.put("customErrorBodies", ce.errorBodies().keySet());
            m.put("sampleRequest", ce.sampleRequest());
            m.put("sampleResponse", ce.sampleResponse());
            m.put("dataSource", e.data());
            m.put("data", ce.data());
            endpoints.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("basePath", stub.basePath());
        out.put("statusHeader", stub.statusHeader());
        out.put("delayHeader", stub.delayHeader());
        out.put("endpoints", endpoints);
        return out;
    }
}
