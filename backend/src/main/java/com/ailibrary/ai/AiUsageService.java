package com.ailibrary.ai;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** AI usage for the last {@value #WINDOW_DAYS} days, with breakdowns by model and operation. */
@Service
public class AiUsageService {
    static final int WINDOW_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final AiProperties properties;

    public AiUsageService(JdbcTemplate jdbc, AiProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public record Breakdown(String key, long requests, long failures, long inputTokens, long outputTokens) {}

    public record Usage(
            boolean aiEnabled,
            int windowDays,
            long requests,
            long successful,
            long failed,
            long inputTokens,
            long outputTokens,
            double averageLatencyMs,
            List<Breakdown> byModel,
            List<Breakdown> byOperation) {}

    public Usage forUser(UUID userId) {
        Timestamp since = Timestamp.from(Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS));
        return jdbc.queryForObject(
                """
                SELECT count(*), count(*) FILTER (WHERE success), coalesce(sum(input_tokens), 0),
                       coalesce(sum(output_tokens), 0), coalesce(avg(latency_ms), 0)
                FROM ai_request_logs WHERE user_id = ? AND created_at >= ?
                """,
                (rs, n) -> new Usage(
                        properties.enabled(),
                        WINDOW_DAYS,
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(1) - rs.getLong(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getDouble(5),
                        breakdown("model", userId, since),
                        breakdown("operation", userId, since)),
                userId,
                since);
    }

    private List<Breakdown> breakdown(String column, UUID userId, Timestamp since) {
        // column is one of two constants above, never user input.
        return jdbc.query(
                "SELECT coalesce(" + column + ", 'unknown'), count(*), count(*) FILTER (WHERE NOT success), "
                        + "coalesce(sum(input_tokens), 0), coalesce(sum(output_tokens), 0) "
                        + "FROM ai_request_logs WHERE user_id = ? AND created_at >= ? "
                        + "GROUP BY 1 ORDER BY 2 DESC",
                (rs, n) -> new Breakdown(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
                userId,
                since);
    }
}
