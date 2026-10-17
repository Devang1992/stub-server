package com.qahub.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Reads JSON/text from "classpath:..." or "file:..." locations. */
@Component
public class ResourceReader {

    private static final Logger log = LoggerFactory.getLogger(ResourceReader.class);

    private final ResourcePatternResolver resolver;
    private final ObjectMapper mapper;

    public ResourceReader(ResourceLoader loader, ObjectMapper mapper) {
        this.resolver = new PathMatchingResourcePatternResolver(loader);
        this.mapper = mapper;
    }

    public String readText(String location) {
        Resource r = resolver.getResource(location);
        if (!r.exists()) {
            throw new IllegalStateException("File not found: " + location);
        }
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + location, e);
        }
    }

    public Object readJson(String location) {
        return mapper.readValue(readText(location), Object.class);
    }

    public Object readJsonOrNull(String location) {
        if (location == null || location.isBlank()) return null;
        try {
            return readJson(location);
        } catch (RuntimeException e) {
            log.warn("Skipping {}: {}", location, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readRecords(String location) {
        Object value = readJson(location);
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(location + " must contain a JSON array of records");
        }
        return (List<Map<String, Object>>) list;
    }

    /** Loads {dir}/{code}.json files, e.g. errors/404.json, keyed by status code. */
    public Map<Integer, String> readErrorBodies(String dir) {
        Map<Integer, String> bodies = new TreeMap<>();
        if (dir == null || dir.isBlank()) return bodies;
        String pattern = dir.endsWith("/") ? dir + "*.json" : dir + "/*.json";
        try {
            for (Resource r : resolver.getResources(pattern)) {
                String file = r.getFilename();
                if (file == null) continue;
                try {
                    int code = Integer.parseInt(file.substring(0, file.length() - ".json".length()));
                    try (InputStream in = r.getInputStream()) {
                        bodies.put(code, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("Ignoring {} in {}: file name must be a status code like 404.json", file, dir);
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan error bodies in {}: {}", dir, e.getMessage());
        }
        return bodies;
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
