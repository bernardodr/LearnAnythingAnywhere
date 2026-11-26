package com.laa.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.laa.llm.Capabilities;
import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Task;

/** A web-searching call (issue #82): its JSON found among the words around it, its searches priced, and its needs. */
class SearchCostTest {

    private static final ModelInfo SEARCHER = new ModelInfo("m", "M", ModelTier.LARGE, new Capabilities(true, true, true, true, 100_000),
            new BigDecimal("2.00"), new BigDecimal("10.00"), new BigDecimal("10.00"));

    @Test
    void theJsonIsFoundAmongTheWordsAroundIt() {
        assertThat(DefaultLlmGateway.jsonIn("I searched for pages.\n{\"sources\": []}\nHope that helps!")).isEqualTo("{\"sources\": []}");
        assertThat(DefaultLlmGateway.jsonIn("{\"a\": {\"b\": 1}}")).isEqualTo("{\"a\": {\"b\": 1}}");
        assertThat(DefaultLlmGateway.jsonIn("no json here")).isEqualTo("no json here");
    }

    @Test
    void searchesArePricedOnTopOfTokens() {
        // 1,000 tokens in at $2/M and 500 out at $10/M = $0.007; plus 3 searches at $10 per 1,000 = $0.03.
        assertThat(DefaultLlmGateway.cost(SEARCHER, 1000, 500, 3)).isEqualByComparingTo("0.037");
        assertThat(DefaultLlmGateway.cost(SEARCHER, 1000, 500, 0)).isEqualByComparingTo("0.007");
        assertThat(DefaultLlmGateway.cost(SEARCHER, null, null, 2)).as("searches alone").isEqualByComparingTo("0.02");
    }

    @Test
    void onlyAModelThatSearchesCanServeResearch() {
        Capabilities noSearch = new Capabilities(true, true, true, false, 100_000);
        Capabilities oldRow = new Capabilities(true, true, true, null, 100_000); // stored before web search existed
        assertThat(noSearch.shortfallsFor(Task.RESEARCH)).containsExactly("web search");
        assertThat(oldRow.shortfallsFor(Task.RESEARCH)).containsExactly("web search");
        assertThat(SEARCHER.capabilities().shortfallsFor(Task.RESEARCH)).isEmpty();
        assertThat(noSearch.shortfallsFor(Task.LESSON_WRITE)).as("other tasks don't search").isEmpty();
    }
}
