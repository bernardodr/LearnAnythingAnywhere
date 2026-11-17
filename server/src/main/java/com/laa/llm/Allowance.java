package com.laa.llm;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.laa.llm.config.LlmProperties;
import com.laa.llm.runs.Origin;
import com.laa.web.ApiException;

/**
 * Who may spend on AI, checked by the gateway before every model call, in this order:
 * <ol>
 *   <li><b>A verified email</b> for a learner's own requests: a script can make accounts, but each one needs an inbox
 *       before it costs anything. (403, {@value #UNVERIFIED})</li>
 *   <li><b>The learner's daily cap</b> on their own requests ({@code laa.llm.limits.learner-daily-usd}). (429,
 *       {@value #LEARNER_LIMIT})</li>
 *   <li><b>The total daily cap</b> on every request, the owner's included ({@code laa.llm.limits.total-daily-usd}).
 *       (503, {@value #TOTAL_LIMIT})</li>
 * </ol>
 * Spend is the run log's cost estimate since midnight UTC, so a call already running can take a learner slightly
 * past their cap. The owner's Compare and the dev tools count toward the total only.
 */
@Component
public class Allowance {

    public static final String UNVERIFIED = "email-unverified";
    public static final String LEARNER_LIMIT = "learner-daily-limit";
    public static final String TOTAL_LIMIT = "total-daily-limit";

    private final JdbcClient jdbc;
    private final LlmProperties.Limits limits;

    Allowance(JdbcClient jdbc, LlmProperties props) {
        this.jdbc = jdbc;
        this.limits = props.limits();
    }

    /** Throws if this call may not run now. */
    public void check(UUID userId, Origin origin) {
        Instant since = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (userId != null && origin == Origin.APP) {
            if (!verified(userId)) {
                throw ApiException.withCode(HttpStatus.FORBIDDEN, UNVERIFIED,
                        "Confirm your email address first: open the link we sent you. You can send it again from your shelf.");
            }
            BigDecimal mine = spent("user_id = :u AND origin = 'APP' AND", since, userId);
            if (mine.compareTo(limits.learnerDailyUsd()) >= 0) {
                throw ApiException.withCode(HttpStatus.TOO_MANY_REQUESTS, LEARNER_LIMIT,
                        "You’ve used today’s AI allowance. It starts again at midnight UTC.");
            }
        }
        if (spent("", since, null).compareTo(limits.totalDailyUsd()) >= 0) {
            throw ApiException.withCode(HttpStatus.SERVICE_UNAVAILABLE, TOTAL_LIMIT,
                    "LAA has reached today’s limit for AI use, so this can’t run until midnight UTC. Nothing was saved.");
        }
    }

    private boolean verified(UUID userId) {
        return jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE id = :u")
                .param("u", userId).query(Boolean.class).optional().orElse(false);
    }

    private BigDecimal spent(String filter, Instant since, UUID userId) {
        var spec = jdbc.sql("SELECT COALESCE(SUM(cost_estimate), 0) FROM model_runs WHERE " + filter + " created_at >= :since")
                .param("since", java.sql.Timestamp.from(since));
        if (userId != null) spec = spec.param("u", userId);
        return spec.query(BigDecimal.class).single();
    }
}
