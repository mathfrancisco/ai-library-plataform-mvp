package com.ailibrary.catalog;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.common.web.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalog;
    private final RequestRateLimiter limiter;

    public CatalogController(CatalogService catalog, RequestRateLimiter limiter) {
        this.catalog = catalog;
        this.limiter = limiter;
    }

    @GetMapping("/search")
    public CatalogPage search(
            @RequestParam @NotBlank @Size(max = 200) String q,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            HttpServletRequest http) {
        limiter.checkSearch(http);
        return catalog.search(q, page, size);
    }

    public record ImportRequest(
            @NotBlank @Size(max = 40) String provider, @NotBlank @Size(max = 200) String externalId) {}

    @PostMapping("/import")
    public BookView importBook(@Valid @RequestBody ImportRequest request) {
        return catalog.importBook(request.provider(), request.externalId());
    }
}
