package com.qahub.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets the browser refresh on a React route without a 404. */
@Controller
public class SpaController {

    @GetMapping({"/stubs", "/stubs/{id}", "/status"})
    public String forward() {
        return "forward:/index.html";
    }
}
