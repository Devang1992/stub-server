package com.qahub.samples;

import com.qahub.config.QaHubProperties;
import com.qahub.config.QaHubProperties.Sample;
import com.qahub.config.ResourceReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Lists samples from samples.yml; content is read only when someone expands one. */
@RestController
public class SamplesController {

    private final List<Sample> samples;
    private final ResourceReader reader;

    public SamplesController(QaHubProperties props, ResourceReader reader) {
        this.samples = props.samples();
        this.reader = reader;
    }

    @GetMapping("/api/samples")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i);
            m.put("name", s.name());
            m.put("app", s.app());
            m.put("description", s.description());
            m.put("path", s.path());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/api/samples/{id}")
    public ResponseEntity<Map<String, Object>> content(@PathVariable int id) {
        if (id < 0 || id >= samples.size()) return ResponseEntity.notFound().build();
        Sample s = samples.get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", s.name());
        try {
            m.put("content", reader.readText(s.path()));
        } catch (RuntimeException e) {
            m.put("error", e.getMessage());
        }
        return ResponseEntity.ok(m);
    }
}
