package com.laa.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.laa.security.SecretRedactor;

/**
 * Sends LAA's emails (verification and password-reset links), as plain text from {@code laa.mail.from}.
 * <ul>
 *   <li><b>With an SMTP server</b> ({@code SPRING_MAIL_HOST}; ./dev.sh points at Mailpit): sent in the background, so
 *       answering a request never waits on, or shows the timing of, a send. A failed send is logged, never shown.</li>
 *   <li><b>Without one</b>, only if {@code laa.mail.outbox} is on (tests, or a local run without Mailpit): kept in an
 *       in-memory {@link #outbox()}, with a warning at startup. Otherwise the server refuses to start, because nobody
 *       could confirm an email or reset a password.</li>
 * </ul>
 * Messages carry one-use links, which are credentials: nothing here logs a body or an address.
 */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final int OUTBOX_SIZE = 200;

    public record Message(String to, String subject, String body) {}

    private final JavaMailSender smtp;
    private final String from;
    private final List<Message> outbox = new ArrayList<>();
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    Mailer(ObjectProvider<JavaMailSender> smtp, @Value("${laa.mail.from}") String from,
            @Value("${laa.mail.outbox}") boolean outboxAllowed) {
        this.smtp = smtp.getIfAvailable();
        this.from = from;
        if (this.smtp == null) {
            if (!outboxAllowed) {
                throw new IllegalStateException("Email isn't set up: set SPRING_MAIL_HOST (and the other SMTP settings) "
                        + "so people can confirm their email and reset their password. For a local run without an SMTP "
                        + "server, set LAA_MAIL_OUTBOX=true to keep emails in memory instead.");
            }
            log.warn("Email isn't set up (SPRING_MAIL_HOST is empty): verification and reset emails are kept in memory only");
        }
    }

    public void send(String to, String subject, String body) {
        if (smtp == null) {
            synchronized (outbox) {
                outbox.add(new Message(to, subject, body));
                if (outbox.size() > OUTBOX_SIZE) outbox.removeFirst();
            }
            return;
        }
        sender.execute(() -> {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(from);
                m.setTo(to);
                m.setSubject(subject);
                m.setText(body);
                smtp.send(m);
            } catch (RuntimeException e) {
                log.warn("An email couldn't be sent: {}", SecretRedactor.redact(e.getMessage()));
            }
        });
    }

    /** Emails kept because there's no SMTP server, oldest first. */
    public List<Message> outbox() {
        synchronized (outbox) {
            return List.copyOf(outbox);
        }
    }

    public void clearOutbox() {
        synchronized (outbox) {
            outbox.clear();
        }
    }
}
