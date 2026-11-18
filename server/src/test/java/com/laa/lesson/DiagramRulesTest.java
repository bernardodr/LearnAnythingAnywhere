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
            // The same, in other spellings and places.
            "<SCRIPT SRC='d3.js'></SCRIPT>",
            "<img src=//evil.example/pixel.gif>",
            "<svg><image href=\"https://example.com/foot.png\"/></svg>",
            "<svg><a xlink:href=\"javascript:alert(1)\"><text>Tap</text></a></svg>",
            "<img srcset=\"https://example.com/a.png 2x\">",
            "<video poster=\"https://example.com/p.png\"></video>",
            "<style>@font-face { font-family: x; src: url(https://example.com/f.woff) }</style>",
            "<div style=\"background:URL( 'HTTPS://example.com/x.png' )\"></div>",
            "<meta http-equiv=\"refresh\" content=\"0;url=https://example.com\">",
            "<base href=\"https://example.com/\">",
            "<object data=\"x.swf\"></object>",
            "<embed src=\"x.swf\">",
            "<button onclick=fetch('/x')>Go</button>",
            "<script>navigator.sendBeacon('/x', 'y')</script>",
            "<script>new EventSource('/x')</script>",
            "<script>importScripts('x.js')</script>",
            "<script>import { a } from './a.js'</script>",
            "<script>window.top.location = 'x'</script>",
            "<script>document.location.href = 'x'</script>",
            "<script>location.reload()</script>",
            "<script>const f = Function('return 1')</script>",
            "<script>setInterval('tick()', 1000)</script>",
            "<script>sessionStorage.x = 1</script>",
            "<script>const w = opener</script>",
            "<script>document.write('<p>x</p>')</script>",
            "<script>navigator.geolocation.getCurrentPosition(() => {})</script>",
            // An unclosed script still runs to the end of the page, so it's still read.
            "<p>Tap</p><script>fetch('/x')",
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

    @ParameterizedTest
    @ValueSource(strings = {
            "<p>Please confirm (once) that your feet are placed.</p>",
            "<p>Import duties\nImport taxes rise.</p>",
            "<p>A dog will fetch (and carry) a stick.</p>",
            "<script>const stages = ['Try to fetch (bring back) the rope', \"Confirm (check) your knot\", `Then location = top`];</script>",
            "<script>// fetch (later) and confirm (maybe)\n/* window.open would go here */ const a = 1;</script>",
            "<script>function show(i) { return i; } const f = function (x) { return x; };</script>",
            "<script>const cookies = ['oat', 'ginger']; const promptText = 'Ready?';</script>",
    })
    void proseAndOrdinaryCodeThatLookLikeRulesStillPass(String page) {
        assertThat(DiagramRules.problems(page)).isEmpty();
    }

    @Test
    void codeInsideATemplateLiteralIsStillRead() {
        assertThat(DiagramRules.problems("<script>panel.innerHTML = `${fetch('/x')}`;</script>")).isNotEmpty();
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
    void wordsShownByCssAreFoundToo() {
        // content: "…" puts words on screen without any markup or script.
        assertThat(DiagramRules.words("<style>.toe::after { content: 'shown by CSS'; }</style><p>Tap</p>")).contains("shown by CSS");
    }

    @Test
    void wordsInEveryReadableAttributeAreFound() {
        String words = DiagramRules.words("<input type=\"range\" aria-label=\"Angle of the foot\" aria-description=\"Slide to tilt\" "
                + "placeholder=\"Type a grade\" value=\"Start here\"><img alt=\"A shoe\" src=\"data:,\"><g data-info='Grips small edges'></g>"
                + "<span title=\"More about heels\">Heel</span>");
        assertThat(words).contains("Angle of the foot", "Slide to tilt", "Type a grade", "Start here", "A shoe", "Grips small edges",
                "More about heels", "Heel");
    }

    @Test
    void wordsAreDecodedAndEachPhraseAppearsOnce() {
        String words = DiagramRules.words("<p>Toes &amp; heels</p><p>Toes &amp; heels</p><script>const s = \"Say \\\"hold\\\" now\";</script><p>42</p>");
        assertThat(words.lines()).containsOnlyOnce("Toes & heels");
        assertThat(words).contains("Say \"hold\" now");
        assertThat(words.lines()).doesNotContain("42"); // no letters, nothing to check
    }

    @Test
    void normalisingTwiceChangesNothing() {
        String once = DiagramRules.normalise("<html><body><svg xmlns=\"http://www.w3.org/2000/svg\"><circle/></svg></body></html>");
        assertThat(DiagramRules.normalise(once)).isEqualTo(once);
    }

    @Test
    void anEmptyPageIsTurnedAway() {
        assertThat(DiagramRules.problems(DiagramRules.normalise("<html><body>  </body></html>"))).containsExactly("The page is empty.");
    }

    @Test
    void wordsInsideTemplateLiteralsAreFoundWithoutTheirTags() {
        String words = DiagramRules.words("<script>panel.innerHTML = `<strong>${name}</strong> grips the hold`;</script>");
        assertThat(words).contains("grips the hold");
        assertThat(words).doesNotContain("<strong>", "${name}");
    }
}
