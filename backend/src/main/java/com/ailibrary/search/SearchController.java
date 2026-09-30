package com.ailibrary.search;

import com.ailibrary.common.security.CurrentUser;
import com.ailibrary.common.web.RequestRateLimiter;
import com.ailibrary.search.SearchDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final HybridSearchService service;
    private final CurrentUser currentUser;
    private final RequestRateLimiter limiter;

    public SearchController(HybridSearchService service, CurrentUser currentUser, RequestRateLimiter limiter) {
        this.service = service;
        this.currentUser = currentUser;
        this.limiter = limiter;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam @NotBlank @Size(max = 200) String q,
            @RequestParam(defaultValue = "HYBRID") SearchMode mode,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            HttpServletRequest http) {
        limiter.checkSearch(http);
        return service.search(q, mode, limit);
    }

    @PostMapping("/discover")
    public DiscoveryResponse discover(
            @Valid @RequestBody DiscoveryRequest request,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            HttpServletRequest http) {
        limiter.checkSearch(http);
        return service.discover(currentUser.id(), request.prompt(), limit);
    }
}
