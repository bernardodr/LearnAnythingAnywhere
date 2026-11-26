package com.laa.safety;

import java.util.regex.Pattern;

/**
 * The em dash filter: no learner ever reads an em dash. The owner (2026-11-26): "make sure there are no em dashes in
 * the app screen as well as on text that is generated from the llms. We want a hard filter on it" (issue #80).
 *
 * <p>Models use em dashes freely, whatever their prompt says, so this is a filter, not a request. {@link ReaderView}
 * runs it on every string it touches: AI text as it's written (replies, structured results, lessons), and every
 * learner-facing response on its way out, so text stored before the filter existed is clean too. Lesson writing runs it
 * on a diagram's page as well, and {@code Jobs} on its progress messages.
 *
 * <p>What it does, in this order:
 * <ol>
 *   <li>Escapes for a dash ({@code &mdash;}, {@code &#8212;}, {@code \u2014} in a diagram's script, …) become the dash
 *       itself, so the rules below catch them.</li>
 *   <li>A dash between two numbers is a range: {@code 1990—2000} becomes {@code 1990–2000} (an en dash).</li>
 *   <li>A dash standing alone for "nothing" (a whole line, or an empty table cell) becomes a hyphen.</li>
 *   <li>A dash starting a line is a list marker: it becomes {@code - }, which Markdown reads as a list.</li>
 *   <li>Any other em dash (also the horizontal bar and the two- and three-em dashes), and an en dash with spaces round
 *       it (used the same way), becomes a comma: {@code small steps — they add up} reads {@code small steps, they add
 *       up}.</li>
 *   <li>The commas that leaves in odd places are tidied: before other punctuation, after an opening bracket, at the end
 *       of a line, or doubled.</li>
 * </ol>
 * An en dash with no spaces (ranges like {@code 5–10}) and plain hyphens are left alone. Pure and thread-safe.
 */
public final class Dashes {

    /** Every em-dash-like character: em dash, horizontal bar, two-em and three-em dashes. */
    private static final String EM = "\u2014\u2015\u2E3A\u2E3B";

    private static final Pattern ESCAPES = Pattern.compile(
            "&(mdash|horbar);|&#(8212|8213|x2014|x2015);|\\\\u2014|\\\\u2015", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE = Pattern.compile("(\\d)[ \\t]*[" + EM + "][ \\t]*(\\d)");
    private static final Pattern ALONE = Pattern.compile("(?m)(^|\\|)([ \\t]*)[" + EM + "]+([ \\t]*)(?=\\||$)");
    private static final Pattern LINE_START = Pattern.compile("(?m)^([ \\t]*)[" + EM + "][ \\t]*");
    private static final Pattern DASH = Pattern.compile("[ \\t]*[" + EM + "]+[ \\t]*|[ \\t]+\u2013[ \\t]+");
    private static final Pattern COMMA_BEFORE_PUNCTUATION = Pattern.compile(",\\s*([,.;:!?)\\]])");
    private static final Pattern COMMA_AFTER_OPENING = Pattern.compile("([(\\[])\\s*,\\s*");
    private static final Pattern COMMA_AT_LINE_END = Pattern.compile("(?m)[ \\t]*,[ \\t]*$");

    private Dashes() {}

    /**
     * The text with no em dashes, written the way a careful editor would without them.
     *
     * @param text any text a learner will read (Markdown, a diagram's HTML and script, plain text); null stays null
     * @return the same text, with every em dash replaced (see the class comment); unchanged if it had none
     */
    public static String replace(String text) {
        if (text == null || !mightHaveDash(text)) return text;
        String s = ESCAPES.matcher(text).replaceAll("\u2014");
        s = RANGE.matcher(s).replaceAll("$1\u2013$2");
        s = ALONE.matcher(s).replaceAll("$1$2-$3");
        s = LINE_START.matcher(s).replaceAll("$1- ");
        s = DASH.matcher(s).replaceAll(", ");
        s = COMMA_BEFORE_PUNCTUATION.matcher(s).replaceAll("$1");
        s = COMMA_AFTER_OPENING.matcher(s).replaceAll("$1");
        s = COMMA_AT_LINE_END.matcher(s).replaceAll("");
        return s;
    }

    /** A quick test first: almost all text has no dash at all, and is then returned without any regex work. */
    private static boolean mightHaveDash(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (EM.indexOf(c) >= 0 || c == '\u2013' || c == '&' || c == '\\') return true;
        }
        return false;
    }
}
