package com.laa.lesson;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The checks an interactive diagram's page must pass before it can be part of a lesson, and the words in it that the
 * safety check reads.
 *
 * <p><b>Why both this and a sealed frame?</b> The app shows a diagram in a frame that has no network, can't reach the
 * app and can't navigate (see {@code app/src/lib/diagramShell.ts}). That frame is what keeps learners safe, whatever
 * the page tries. These rules are the second line: they turn away pages that try to reach out, so a mistake in the
 * frame on some device doesn't become a leak, and they give the diagram writer a clear reason to rewrite. They are
 * deliberately strict and simple: a page that a rule wrongly refuses is rewritten once and otherwise left out, and a
 * lesson without a diagram is still a good lesson.
 *
 * <p>Used by {@link LessonWriter} (to ask for one rewrite) and {@link LessonChecks#withDiagrams} (the final say).
 */
public final class DiagramRules {

    /** The longest page accepted, in characters. A rich interactive diagram fits in well under half of this. */
    public static final int MAX_HTML = 40_000;

    /** One rule: what it looks for, and what to tell the writer when a page breaks it. */
    private record Rule(Pattern pattern, String message) {}

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.MULTILINE;

    private static final List<Rule> RULES = List.of(
            rule("<script[^>]*\\bsrc\\s*=", "Scripts must be written inline: no <script src=…>."),
            rule("<(iframe|frame|frameset|object|embed|applet|form|base|meta|link|portal)\\b",
                    "Leave out <iframe>, <object>, <embed>, <form>, <base>, <meta> and <link>: the page is self-contained."),
            rule("<a\\b[^>]*\\bhref", "No links: the diagram can't open anything. Use a button for anything the learner presses."),
            rule("\\b(src|href|action|formaction|poster|data|srcset|xlink:href)\\s*=\\s*[\"']?\\s*(https?:|//|javascript:)",
                    "No outside addresses: the page has no internet. Draw everything inline (SVG, CSS, or data: images)."),
            rule("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']?(?!data:)[^\"'\\s>]",
                    "Images must be inline SVG or data: URLs; the page can't load files."),
            rule("url\\(\\s*[\"']?\\s*(https?:|//)", "No outside addresses in CSS url(): the page has no internet."),
            rule("@import\\b", "No CSS @import: put all the CSS inline."),
            rule("\\bfetch\\s*\\(|\\b(XMLHttpRequest|WebSocket|EventSource|importScripts|sendBeacon|RTCPeerConnection)\\b",
                    "No network calls (fetch, XMLHttpRequest, WebSocket, …): the page has no internet."),
            rule("\\bimport\\s*\\(|^\\s*import\\s+[\\w{*]", "No JavaScript modules or import: write plain inline script."),
            rule("\\beval\\s*\\(|\\bnew\\s+Function\\b|\\bset(Timeout|Interval)\\s*\\(\\s*[\"'`]",
                    "No code built from strings (eval, new Function, setTimeout with a string)."),
            rule("\\b(localStorage|sessionStorage|indexedDB|document\\.cookie|caches)\\b",
                    "No storage (localStorage, cookies, …): keep everything in variables."),
            rule("\\bwindow\\.(open|top|parent|opener)\\b|\\btop\\.location\\b|\\bparent\\.(postMessage|location|document)\\b",
                    "The page can't reach outside its frame (window.open, window.top, window.parent)."),
            rule("\\blocation\\s*(\\.\\s*(href|assign|replace|reload)\\b|=[^=])", "The page can't navigate (location)."),
            rule("\\bpostMessage\\b", "The page can't send messages: LAA's own frame handles that."),
            rule("\\bnavigator\\.(geolocation|mediaDevices|clipboard|serviceWorker|credentials|share)\\b",
                    "The page can't use the device (location, camera, clipboard, …)."),
            rule("\\b(alert|confirm|prompt)\\s*\\(", "No alert(), confirm() or prompt(): show messages on the page instead."));

    /** Wrappers a writer sometimes adds although the app supplies the document; taken out rather than refused. */
    private static final Pattern WRAPPERS = Pattern.compile(
            "<!doctype[^>]*>|</?html\\b[^>]*>|</?head\\b[^>]*>|</?body\\b[^>]*>|<title\\b[^>]*>.*?</title>", CI | Pattern.DOTALL);
    /** SVG namespace declarations: inline SVG in HTML doesn't need them, and they are the one harmless outside address. */
    private static final Pattern XMLNS = Pattern.compile("\\s+xmlns(:\\w+)?\\s*=\\s*([\"'])[^\"']*\\2", CI);

    private static final Pattern SCRIPT = Pattern.compile("<script\\b[^>]*>(.*?)</script>", CI | Pattern.DOTALL);
    private static final Pattern STYLE = Pattern.compile("<style\\b[^>]*>.*?</style>", CI | Pattern.DOTALL);
    /** Text a learner can read in an attribute: tooltips, labels, and data-* values a script shows later. */
    private static final Pattern READABLE_ATTRIBUTE = Pattern.compile(
            "\\b(title|alt|aria-label|aria-description|placeholder|value|label|data-[\\w-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", CI);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    /** String literals in a script: '…', "…" or `…`. The first two stop at a line end, as JavaScript does. */
    private static final Pattern STRING_LITERAL = Pattern.compile("'((?:\\\\.|[^'\\\\\\n])*)'|\"((?:\\\\.|[^\"\\\\\\n])*)\"|`((?:\\\\.|[^`\\\\])*)`");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private DiagramRules() {}

    private static Rule rule(String regex, String message) {
        return new Rule(Pattern.compile(regex, CI), message);
    }

    /**
     * The page as it will be stored: the writer's markup without any document wrapper ({@code <html>}, {@code <head>},
     * {@code <body>}) or SVG namespace declarations, both of which the app's frame makes unnecessary.
     */
    public static String normalise(String html) {
        String out = WRAPPERS.matcher(html == null ? "" : html).replaceAll("");
        return XMLNS.matcher(out).replaceAll("").strip();
    }

    /**
     * Everything wrong with a page, as messages the diagram writer can act on; empty when the page may be shown.
     * Run it on the {@link #normalise normalised} page.
     */
    public static List<String> problems(String html) {
        List<String> problems = new ArrayList<>();
        if (html == null || html.isBlank()) {
            problems.add("The page is empty.");
            return problems;
        }
        if (html.length() > MAX_HTML) {
            problems.add("The page is " + html.length() + " characters; keep it under " + MAX_HTML + ".");
        }
        for (Rule r : RULES) {
            if (r.pattern().matcher(html).find()) problems.add(r.message());
        }
        return problems;
    }

    /**
     * The words a learner could read on the page, one phrase per line, for the safety check: text in the markup, text
     * in readable attributes, and the string literals in its scripts (which is where a script keeps what it shows
     * later). CSS is left out. The diagram prompt asks for every shown word to be written out whole, so it lands here.
     */
    public static String words(String html) {
        Set<String> phrases = new LinkedHashSet<>();
        Matcher scripts = SCRIPT.matcher(html);
        while (scripts.find()) literals(scripts.group(1), phrases);
        String markup = STYLE.matcher(SCRIPT.matcher(html).replaceAll(" ")).replaceAll(" ");
        Matcher attributes = READABLE_ATTRIBUTE.matcher(markup);
        while (attributes.find()) {
            add(attributes.group(3) != null ? attributes.group(3) : attributes.group(4), phrases);
        }
        for (String line : TAG.matcher(markup).replaceAll("\n").split("\n")) add(line, phrases);
        return String.join("\n", phrases);
    }

    private static void literals(String script, Set<String> phrases) {
        Matcher m = STRING_LITERAL.matcher(script);
        while (m.find()) {
            String s = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            // Template literals hold markup too, so their tags are taken out the same way as the page's.
            for (String line : TAG.matcher(s.replaceAll("\\$\\{[^}]*}", " ")).replaceAll("\n").split("\n")) add(line, phrases);
        }
    }

    private static void add(String raw, Set<String> phrases) {
        if (raw == null) return;
        String s = decode(raw).replaceAll("\\s+", " ").strip();
        // Everything with a letter in it is checked, ids and class names included: leaving out what looks like code
        // would also leave out a word that only looks like code, and checking a few extra tokens costs nothing.
        if (s.length() < 2 || !LETTER.matcher(s).find()) return;
        phrases.add(s);
    }

    private static String decode(String s) {
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
                .replace("\\n", " ").replace("\\'", "'").replace("\\\"", "\"");
    }
}
