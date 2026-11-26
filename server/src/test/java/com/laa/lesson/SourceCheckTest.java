package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.laa.llm.config.LlmProperties;

/**
 * The source check's rules (issue #82), without going online: which addresses may be opened at all, which hosts count
 * as public, how a page's title is read and matched, and the child-safe site list. Every address here is refused
 * before any connection, or is a practice host that never connects.
 */
class SourceCheckTest {

    private final SourceCheck practice = new SourceCheck(new LlmProperties(null, true, null, null, null, null, null, null));

    @Test
    void onlyHttpsOnTheStandardPortWithNoUserName() {
        assertThat(SourceCheck.safeAddress("https://www.nasa.gov/moon")).isPresent();
        assertThat(SourceCheck.safeAddress("https://www.nasa.gov:443/moon")).isPresent();
        assertThat(SourceCheck.safeAddress("http://www.nasa.gov/moon")).as("plain http").isEmpty();
        assertThat(SourceCheck.safeAddress("https://www.nasa.gov:8443/moon")).as("another port").isEmpty();
        assertThat(SourceCheck.safeAddress("https://user:pass@www.nasa.gov/")).as("a user name").isEmpty();
        assertThat(SourceCheck.safeAddress("javascript:alert(1)")).isEmpty();
        assertThat(SourceCheck.safeAddress("not an address")).isEmpty();
    }

    @Test
    void privateAndReservedAddressesAreNeverOpened() throws Exception {
        for (String inside : List.of("127.0.0.1", "10.1.2.3", "172.16.0.5", "192.168.1.88", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "198.18.0.1", "224.0.0.1", "::1", "fd00::1", "fe80::1", "::ffff:10.0.0.1")) {
            assertThat(SourceCheck.isPublic(InetAddress.getByName(inside))).as(inside).isFalse();
            assertThat(practice.check("https://" + (inside.contains(":") ? "[" + inside + "]" : inside) + "/page", "A page", "Site"))
                    .as("check refuses " + inside).isEmpty();
        }
        for (String outside : List.of("8.8.8.8", "93.184.215.14", "2606:4700::1111")) {
            assertThat(SourceCheck.isPublic(InetAddress.getByName(outside))).as(outside).isTrue();
        }
    }

    @Test
    void aPageMustBeWhatItClaimsToBe() {
        assertThat(SourceCheck.pageTitle("<html><head><title>  The Moon &amp; its Phases | NASA </title>")).isEqualTo("The Moon & its Phases | NASA");
        assertThat(SourceCheck.pageTitle("<html><body>No title</body></html>")).isNull();
        assertThat(SourceCheck.sameSubject("Phases of the Moon NASA", "The Moon & its Phases | NASA Science")).isTrue();
        assertThat(SourceCheck.sameSubject("Stars and constellations Royal Museums Greenwich", "Constellation guide")).as("plurals").isTrue();
        assertThat(SourceCheck.sameSubject("Phases of the Moon", "Page not found")).isFalse();
        assertThat(SourceCheck.sameSubject("A guide", "Anything at all")).as("no real words to compare: passes").isTrue();
    }

    @Test
    void practiceHostsPassOfflineOnlyInPracticeMode() {
        assertThat(practice.check("https://example.org/guides/moon", "A guide to the Moon", "Example")).get()
                .extracting(SourceCheck.Passed::url).isEqualTo("https://example.org/guides/moon");
        assertThat(practice.check("https://www.example.com/learn", "Going further", "Example")).isPresent();
        assertThat(practice.check("http://example.org/guides/moon", "A guide", "Example")).as("still https only").isEmpty();
    }

    @Test
    void theChildSafeListMatchesSitesAndTheirSubdomains() {
        List<String> sites = List.of("nasa.gov", "britannica.com");
        assertThat(SourceCheck.onSites("https://www.nasa.gov/moon", sites)).isTrue();
        assertThat(SourceCheck.onSites("https://science.nasa.gov/moon", sites)).isTrue();
        assertThat(SourceCheck.onSites("https://kids.britannica.com/x", sites)).isTrue();
        assertThat(SourceCheck.onSites("https://notnasa.gov/moon", sites)).as("a lookalike").isFalse();
        assertThat(SourceCheck.onSites("https://nasa.gov.example.com/", sites)).isFalse();
        assertThat(SourceCheck.onSites("https://example.org/guides", sites)).isFalse();
    }
}
