package com.laa.lesson;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.laa.llm.config.LlmProperties;

/**
 * Checks a source a model found before any learner sees it (BRIEF section 6.3: "verify each URL resolves before saving
 * it"; issue #82). A source passes only if its address really opens, as the page it claims to be:
 * <ol>
 *   <li><b>The address is safe to open from the server:</b> {@code https} on the standard port, no user name in it,
 *       and every address its host resolves to is a public one (not this machine, the private network, link-local
 *       addresses such as a cloud's metadata service, or other reserved ranges). So a crafted address can never make
 *       the server reach inside its own network.</li>
 *   <li><b>It opens:</b> fetched with a short timeout, following at most three redirects, each checked the same way;
 *       the final answer must be 2xx and a web page, a PDF or plain text.</li>
 *   <li><b>It's the page it claims to be:</b> a web page's {@code <title>} shares at least one real word with the
 *       title the model gave (or the site's name); a page with no title passes on the first two checks.</li>
 * </ol>
 * In practice mode only (local development and tests), addresses on {@code example.com}, {@code example.org} and
 * {@code example.net}, which are reserved for examples and never a real source, pass without going online, so the
 * practice provider's sources can be shown offline. Never thrown at: any failure is just "no".
 */
@Component
public class SourceCheck {

    private static final Logger log = LoggerFactory.getLogger(SourceCheck.class);

    /** A source that passed: the address it finally opened at (after any redirects), and the page's own title. */
    public record Passed(String url, String pageTitle) {}

    private static final Set<String> PRACTICE_HOSTS = Set.of("example.com", "example.org", "example.net");
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 64 * 1024;
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> STOP = Set.of("about", "from", "with", "your", "what", "when", "this", "that", "into",
            "guide", "home", "page", "introduction", "learn", "learning", "basics", "official", "website");

    private final boolean practice;
    private final HttpClient http;

    SourceCheck(LlmProperties props) {
        this.practice = props.practiceProviders();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /**
     * Whether this source can be shown: see the class comment for the three checks.
     *
     * @param url          the address the model gave
     * @param claimedTitle the title the model gave for it
     * @param site         who the model says publishes it (its words count for the title check too)
     * @return the page as it opened, or empty if any check fails (logged, with the reason)
     */
    public Optional<Passed> check(String url, String claimedTitle, String site) {
        URI uri = safeAddress(url).orElse(null);
        if (uri == null) return reject(url, "not a public https address");
        if (practice && isPractice(uri.getHost())) return Optional.of(new Passed(uri.toString(), claimedTitle));
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!resolvesPublic(uri.getHost())) return reject(url, "its host isn't a public address");
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET()
                        .header("User-Agent", "LearnAnythingAnywhere/1.0 (checking a source for a lesson; +https://learnanythinganywhere.app)")
                        .header("Accept", "text/html,application/xhtml+xml,application/pdf,text/plain;q=0.8,*/*;q=0.1")
                        .build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        String location = response.headers().firstValue("Location").orElse(null);
                        if (location == null) return reject(url, "a redirect without a location");
                        uri = safeAddress(uri.resolve(location.strip()).toString()).orElse(null);
                        if (uri == null) return reject(url, "it redirects somewhere that isn't a public https address");
                        continue;
                    }
                    if (status < 200 || status >= 300) return reject(url, "it answered " + status);
                    String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    boolean html = type.contains("html");
                    if (!html && !type.contains("pdf") && !type.startsWith("text/plain")) return reject(url, "it isn't a page (" + type + ")");
                    String pageTitle = html ? pageTitle(new String(body.readNBytes(MAX_BYTES), StandardCharsets.UTF_8)) : null;
                    if (pageTitle != null && !sameSubject(claimedTitle + " " + site, pageTitle)) {
                        return reject(url, "its page title \"" + pageTitle + "\" doesn't match \"" + claimedTitle + "\"");
                    }
                    return Optional.of(new Passed(uri.toString(), pageTitle));
                }
            }
            return reject(url, "too many redirects");
        } catch (IOException | IllegalArgumentException e) {
            return reject(url, e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return reject(url, "interrupted");
        }
    }

    /** The address, if it's https on the standard port with a host name and no user name in it; else empty. */
    static Optional<URI> safeAddress(String url) {
        try {
            URI uri = URI.create(url.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) return Optional.empty();
            if (uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return Optional.empty();
            return Optional.of(uri.normalize());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /** Whether every address the host resolves to is public (see {@link #isPublic}); false if it doesn't resolve. */
    static boolean resolvesPublic(String host) {
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            return all.length > 0 && Arrays.stream(all).allMatch(SourceCheck::isPublic);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Whether an address is on the public internet: not this machine, not unspecified, not private, link-local (the
     * cloud metadata service lives there), carrier-grade NAT, benchmarking, multicast or reserved, nor an IPv6
     * unique-local or IPv4-mapped one of those.
     */
    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff, second = b[1] & 0xff;
            if (first == 0 || first >= 240) return false;                             // "this network", reserved, broadcast
            if (first == 100 && second >= 64 && second <= 127) return false;          // carrier-grade NAT, 100.64.0.0/10
            if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return false;      // IETF protocol assignments
            if (first == 198 && (second == 18 || second == 19)) return false;         // benchmarking, 198.18.0.0/15
            return true;
        }
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return false;                                  // unique local, fc00::/7
            boolean mapped = true;                                                    // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The page's {@code <title>}, tidied, or null if it has none. */
    static String pageTitle(String html) {
        Matcher m = TITLE.matcher(html);
        if (!m.find()) return null;
        String title = m.group(1).replaceAll("<[^>]+>", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").strip();
        return title.isEmpty() ? null : title;
    }

    /** Whether the claimed title (and site) and the page's title share a real word: four letters or more, not a filler. */
    static boolean sameSubject(String claimed, String pageTitle) {
        Set<String> claimedWords = words(claimed);
        if (claimedWords.isEmpty()) return true;
        return words(pageTitle).stream().anyMatch(claimedWords::contains);
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> w.length() >= 4 && !STOP.contains(w))
                .map(w -> w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w)
                .collect(Collectors.toSet());
    }

    /** True for the practice hosts and their subdomains. */
    private static boolean isPractice(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return PRACTICE_HOSTS.stream().anyMatch(p -> h.equals(p) || h.endsWith("." + p));
    }

    /** Whether a host is one of these sites or a subdomain of one ("www.nasa.gov" is on "nasa.gov"). */
    public static boolean onSites(String url, List<String> sites) {
        String host = safeAddress(url).map(URI::getHost).map(h -> h.toLowerCase(Locale.ROOT)).orElse(null);
        if (host == null) return false;
        return sites.stream().map(s -> s.toLowerCase(Locale.ROOT).replaceAll("^https?://", "").replaceAll("/.*$", ""))
                .anyMatch(s -> host.equals(s) || host.endsWith("." + s));
    }

    private static Optional<Passed> reject(String url, String why) {
        log.info("Source left out ({}): {}", why, url);
        return Optional.empty();
    }
}
