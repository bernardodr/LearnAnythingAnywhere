package com.laa.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;

/**
 * A learner's first requests often arrive together (the app loads several screens' data at once). Each used to
 * create its own reader settings, so all but one were orphaned and saving them failed.
 */
class ProfileRaceTest extends IntegrationTest {

    @Test
    void parallelFirstRequestsShareOneProfile() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int round = 0; round < 10; round++) {
                String token = login("race-" + round + "-" + System.nanoTime() + "@test.dev");
                String userId = get("/auth/me", token).body().get("id").asString();
                List<Callable<String>> calls = IntStream.range(0, 8)
                        .<Callable<String>>mapToObj(i -> () -> get("/profiles/me", token).body().get("id").asString()).toList();
                Set<String> ids = pool.invokeAll(calls).stream().map(ProfileRaceTest::value).collect(Collectors.toSet());

                assertThat(ids).as("round " + round).hasSize(1);
                int owned = jdbc.sql("SELECT count(*) FROM generation_profiles WHERE owner_user_id = :u::uuid").param("u", userId)
                        .query(Integer.class).single();
                assertThat(owned).as("round " + round).isEqualTo(1);
            }
        }
    }

    private static String value(Future<String> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
