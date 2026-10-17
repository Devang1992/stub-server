package com.qahub.stub;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One catch-all mapping. Endpoints come from stubs.yml, not from Java code. */
@RestController
public class StubController {

    private final StubEngine engine;

    public StubController(StubEngine engine) {
        this.engine = engine;
    }

    @RequestMapping("${qa-hub.stub.base-path:/stub}/**")
    public ResponseEntity<Object> handle(HttpServletRequest request,
                                         @RequestBody(required = false) String body) {
        return engine.handle(request, body);
    }
}
