package com.qahub.stub;

import com.qahub.config.QaHubProperties.Endpoint;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.pattern.PathPattern;

/** An endpoint from YAML with its files already loaded into memory. */
public record CompiledEndpoint(Endpoint def,
                               PathPattern pattern,
                               List<Map<String, Object>> data,
                               Object sampleRequest,
                               Object sampleResponse,
                               Map<Integer, String> errorBodies) {}
