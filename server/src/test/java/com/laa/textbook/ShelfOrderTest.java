package com.laa.textbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;

/** A learner arranges the books on their shelf by hand; the order is kept for them, on every device. */
class ShelfOrderTest extends IntegrationTest {

    @Test
    void theShelfKeepsTheLearnersOrderAndNewBooksGoAtTheEnd() {
        String token = login("shelf-order@test.dev");
        String a = book(token, "Bouldering"), b = book(token, "Sourdough bread"), c = book(token, "Chess openings");
        assertThat(shelf(token)).containsExactly(a, b, c);

        assertThat(put("/textbooks/order", token, Map.of("ids", List.of(c, a, b))).status()).isEqualTo(204);
        assertThat(shelf(token)).containsExactly(c, a, b);

        String d = book(token, "Watercolour painting");
        assertThat(shelf(token)).containsExactly(c, a, b, d);
    }

    @Test
    void someoneElsesBooksAreLeftAlone() {
        String mine = login("shelf-mine@test.dev"), theirs = login("shelf-theirs@test.dev");
        String m1 = book(mine, "Bouldering"), m2 = book(mine, "Chess openings");
        String t1 = book(theirs, "Sourdough bread"), t2 = book(theirs, "Python");

        assertThat(put("/textbooks/order", mine, Map.of("ids", List.of(t2, m2, t1, m1))).status()).isEqualTo(204);
        assertThat(shelf(mine)).containsExactly(m2, m1);
        assertThat(shelf(theirs)).containsExactly(t1, t2);
    }

    @Test
    void aBookCanOnlyBeInOnePlace() {
        String token = login("shelf-twice@test.dev");
        String a = book(token, "Bouldering");
        assertThat(put("/textbooks/order", token, Map.of("ids", List.of(a, a))).status()).isEqualTo(400);
    }

    private String book(String token, String topic) {
        Resp r = post("/textbooks", token, Map.of("topic", topic));
        assertThat(r.status()).as(r.raw()).isEqualTo(201);
        return r.body().get("textbook").get("id").asString();
    }

    private List<String> shelf(String token) {
        return StreamSupport.stream(get("/textbooks", token).body().spliterator(), false).map(b -> b.get("id").asString()).toList();
    }
}
