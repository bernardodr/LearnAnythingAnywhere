package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The page rules for interactive diagrams: pages that try to reach outside their frame are turned away with a reason
 * the writer can act on, ordinary self-contained pages pass, and every word a learner could read reaches the safety
 * check, including text a script shows later.
 */
class DiagramRulesTest {

    private static final String GOOD = """
            <style>.part { fill: var(--slate); } @media (prefers-reduced-motion: reduce) { * { transition: none; } }</style>
            <svg viewBox="0 0 100 100" width="100%"><g class="part" role="button" tabindex="0" aria-label="The toe box">
              <rect x="10" y="10" width="40" height="40"/></g></svg>
            <div aria-live="polite" id="panel">Tap a part of the shoe</div>
            <button class="laa-button" id="next">Next</button>
            <script>
              const parts = [{ label: "Toe box", explanation: "Where your toes press on small holds." }];
              document.getElementById("next").addEventListener("click", () => {
                document.getElementById("panel").textContent = parts[0].explanation;
                setTimeout(() => {}, 100);
              });
            </script>
            """;

    @Test
    void aSelfContainedInteractivePagePasses() {
        assertThat(DiagramRules.problems(DiagramRules.normalise(GOOD))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script src=\"https://cdn.example.com/d3.js\"></script>",
            "<img src=\"https://example.com/foot.png\">",
            "<img src=\"foot.png\">",
            "<div style=\"background: url('https://example.com/x.png')\"></div>",
            "<style>@import 'theme.css';</style>",
            "<a href=\"https://example.com\">More</a>",
            "<iframe srcdoc=\"x\"></iframe>",
            "<form><input name=\"q\"></form>",
            "<script>fetch('/api/me')</script>",
            "<script>new XMLHttpRequest()</script>",
            "<script>const s = new WebSocket('wss://x')</script>",
            "<script>import('./x.js')</script>",
            "<script>eval('1 + 1')</script>",
            "<script>setTimeout(\"go()\", 10)</script>",
            "<script>localStorage.setItem('a', 1)</script>",
            "<script>document.cookie</script>",
            "<script>window.open('x')</script>",
            "<script>window.parent.document.body</script>",
            "<script>top.location = 'x'</script>",
            "<script>location.href = 'x'</script>",
            "<script>postMessage('hi', '*')</script>",
            "<script>navigator.clipboard.writeText('x')</script>",
            "<script>alert('Well done!')</script>",
            "<button onclick=\"location.assign('x')\">Go</button>",
    })
    void aPageThatReachesOutsideItsFrameIsTurnedAway(String page) {
        assertThat(DiagramRules.problems(DiagramRules.normalise(page))).isNotEmpty();
    }

    @Test
    void ordinaryWordsThatLookLikeRulesStillPass() {
        // "fetch", "location", "prompt" and "import" as words, and .parentNode, are all fine.
        String page = "<p>Dogs fetch sticks. The location of the heart. Use the prompt card. An important import. </p>"
                + "<script>const a = document.body.parentNode; const rect = a.getBoundingClientRect(); rect.top;</script>";
        assertThat(DiagramRules.problems(page)).isEmpty();
    }

    @Test
    void aDocumentWrapperAndSvgNamespacesAreTakenOutNotRefused() {
        String page = "<!DOCTYPE html><html><head><title>x</title></head><body><svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 1 1\"></svg></body></html>";
        String normal = DiagramRules.normalise(page);
        assertThat(normal).isEqualTo("<svg viewBox=\"0 0 1 1\"></svg>");
        assertThat(DiagramRules.problems(normal)).isEmpty();
    }

    @Test
    void aPageOverTheSizeLimitIsTurnedAway() {
        String page = "<p>" + "x".repeat(DiagramRules.MAX_HTML) + "</p>";
        assertThat(DiagramRules.problems(page)).anyMatch(p -> p.contains("characters"));
    }

    @Test
    void theWordsIncludeMarkupReadableAttributesAndScriptStrings() {
        String words = DiagramRules.words(GOOD);
        assertThat(words).contains("Tap a part of the shoe", "The toe box", "Next", "Toe box",
                "Where your toes press on small holds.");
        // CSS isn't text a learner reads.
        assertThat(words).doesNotContain("prefers-reduced-motion", "var(--slate)");
    }

    @Test
    void wordsInsideTemplateLiteralsAreFoundWithoutTheirTags() {
        String words = DiagramRules.words("<script>panel.innerHTML = `<strong>${name}</strong> grips the hold`;</script>");
        assertThat(words).contains("grips the hold");
        assertThat(words).doesNotContain("<strong>", "${name}");
    }
}
