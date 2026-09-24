package com.inkomoko.mobilebanking.controllers.portal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React admin portal (built into classpath:/static/portal by the Docker build).
 * The portal uses hash routing (/portal/#/customers), so only the entry points need mapping.
 */
@Controller
public class PortalController {

    @GetMapping("/")
    public String root() {
        return "redirect:/portal/";
    }

    @GetMapping({"/portal", "/portal/"})
    public String portal() {
        return "forward:/portal/index.html";
    }
}
