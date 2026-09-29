package com.ailibrary.search;

import com.ailibrary.common.security.CurrentUser;
import com.ailibrary.search.SearchDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final HybridSearchService service; private final CurrentUser currentUser;
    public SearchController(HybridSearchService service, CurrentUser currentUser){ this.service=service; this.currentUser=currentUser; }
    @GetMapping public List<SearchHit> search(@RequestParam @NotBlank String q,@RequestParam(defaultValue="HYBRID") SearchMode mode,@RequestParam(defaultValue="20") @Min(1) @Max(50) int limit){ return service.search(q,mode,limit); }
    @PostMapping("/discover") public DiscoveryResponse discover(@Valid @RequestBody DiscoveryRequest request,@RequestParam(defaultValue="20") int limit){ return service.discover(currentUser.id(),request.prompt(),limit); }
}
