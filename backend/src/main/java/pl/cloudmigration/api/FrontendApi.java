package pl.cloudmigration.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only known SPA routes are forwarded; missing API endpoints and assets stay 404. */
@Controller
public class FrontendApi {
    @GetMapping({"/", "/applications", "/evaluations", "/funding", "/decisions", "/reports", "/competitions"})
    public String page() { return "forward:/200.html"; }
}
