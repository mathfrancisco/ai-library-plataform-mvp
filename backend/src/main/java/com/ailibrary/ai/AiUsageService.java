package com.ailibrary.ai;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiUsageService {
    private final JdbcTemplate jdbc;

    public AiUsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Usage(long requests, long successful, long inputTokens, long outputTokens, double averageLatencyMs) {}

    public Usage forUser(UUID userId) {
        return jdbc.queryForObject(
                """
            SELECT count(*), count(*) FILTER (WHERE success), coalesce(sum(input_tokens),0),
                   coalesce(sum(output_tokens),0), coalesce(avg(latency_ms),0)
            FROM ai_request_logs WHERE user_id = ?
            """,
                (rs, n) -> new Usage(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getDouble(5)),
                userId);
    }
}
