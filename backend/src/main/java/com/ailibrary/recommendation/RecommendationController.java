package com.ailibrary.recommendation;

import com.ailibrary.common.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendations;
    private final CurrentUser currentUser;

    public RecommendationController(RecommendationService recommendations, CurrentUser currentUser) {
        this.recommendations = recommendations;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<RecommendationService.Recommendation> list(@RequestParam(defaultValue = "12") int limit) {
        return recommendations.forUser(currentUser.id(), Math.max(1, Math.min(limit, 50)));
    }
}
