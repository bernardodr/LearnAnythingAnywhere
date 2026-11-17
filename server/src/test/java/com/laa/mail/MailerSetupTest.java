package com.laa.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Email must be set up in production: without an SMTP server nobody could confirm an email or reset a password, so
 * the server refuses to start unless the in-memory outbox is asked for (tests, and local runs without Mailpit).
 */
class MailerSetupTest {

    private static final String FROM = "LAA <no-reply@test.dev>";

    @Test
    void noSmtpServerAndNoOutboxRefusesToStart() {
        assertThatThrownBy(() -> new Mailer(noSmtp(), FROM, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST")
                .hasMessageContaining("LAA_MAIL_OUTBOX");
    }

    @Test
    void theOutboxKeepsEmailsWhenAskedFor() {
        Mailer mailer = new Mailer(noSmtp(), FROM, true);
        mailer.send("someone@test.dev", "Hello", "A body");
        assertThat(mailer.outbox()).containsExactly(new Mailer.Message("someone@test.dev", "Hello", "A body"));
    }

    @Test
    void anSmtpServerIsEnoughOnItsOwn() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("smtp", new JavaMailSenderImpl());
        Mailer mailer = new Mailer(factory.getBeanProvider(JavaMailSender.class), FROM, false);
        assertThat(mailer.outbox()).isEmpty();
    }

    private static ObjectProvider<JavaMailSender> noSmtp() {
        return new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class);
    }
}
