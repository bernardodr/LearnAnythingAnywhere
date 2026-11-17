package com.laa.llm.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.laa.llm.Provider;

/** A new database's default AI service is the configured one, never a silent practice stand-in. */
class FirstDefaultTest {

    @Test
    void theConfiguredServiceWhenItCanBeUsed() {
        assertThat(RoutingSeeder.firstDefault(Provider.ANTHROPIC, true, null)).isEqualTo(Provider.ANTHROPIC);
    }

    @Test
    void noneConfiguredRefusesToStart() {
        assertThatThrownBy(() -> RoutingSeeder.firstDefault(null, false, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LAA_DEFAULT_PROVIDER");
    }

    @Test
    void anUnusableOneRefusesToStartRatherThanFallingBackToPractice() {
        assertThatThrownBy(() -> RoutingSeeder.firstDefault(Provider.FAKE, false, "practice mode is off"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("practice mode is off");
    }
}
