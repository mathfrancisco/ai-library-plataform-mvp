package com.ailibrary.catalog;

import com.ailibrary.book.dto.BookView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search")
    public CatalogPage search(@RequestParam @NotBlank String q,
                              @RequestParam(defaultValue = "1") @Min(1) int page,
                              @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return catalog.search(q, page, size);
    }

    public record ImportRequest(@NotBlank String provider, @NotBlank String externalId) {}

    @PostMapping("/import")
    public BookView importBook(@RequestBody ImportRequest request) {
        return catalog.importBook(request.provider(), request.externalId());
    }
}
