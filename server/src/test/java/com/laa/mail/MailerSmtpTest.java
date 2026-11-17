package com.laa.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SMTP path end to end: {@link Mailer} sends through a real SMTP server (Mailpit, as {@code ./dev.sh} runs it), and
 * the email is read back from Mailpit's API. The other email tests use the in-memory outbox.
 */
class MailerSmtpTest {

    private static final GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.33")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));
    private static RestClient api;

    @BeforeAll
    static void start() {
        mailpit.start();
        api = RestClient.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
    }

    @AfterAll
    static void stop() {
        mailpit.stop();
    }

    @Test
    void anEmailReachesTheSmtpServerAsWritten() throws InterruptedException {
        var smtp = new JavaMailSenderImpl();
        smtp.setHost(mailpit.getHost());
        smtp.setPort(mailpit.getMappedPort(1025));
        var factory = new StaticListableBeanFactory();
        factory.addBean("smtp", smtp);
        var mailer = new Mailer(factory.getBeanProvider(JavaMailSender.class),
                "Learn Anything Anywhere <support@test.dev>", false);

        mailer.send("learner@test.dev", "Confirm your email", "Open this link: http://localhost:8081/verify-email?token=abc");

        JsonNode message = firstMessage(Duration.ofSeconds(10)); // sent in the background
        assertThat(message.get("Subject").asString()).isEqualTo("Confirm your email");
        assertThat(message.get("From").get("Name").asString()).isEqualTo("Learn Anything Anywhere");
        assertThat(message.get("From").get("Address").asString()).isEqualTo("support@test.dev");
        assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo("learner@test.dev");
        assertThat(message.get("Snippet").asString()).contains("verify-email?token=abc");
        assertThat(mailer.outbox()).as("nothing kept in memory when SMTP is set up").isEmpty();
    }

    private static JsonNode firstMessage(Duration within) throws InterruptedException {
        Instant end = Instant.now().plus(within);
        while (Instant.now().isBefore(end)) {
            JsonNode list = JsonMapper.builder().build().readTree(api.get().uri("/api/v1/messages").retrieve().body(String.class));
            if (list.get("messages").size() > 0) return list.get("messages").get(0);
            Thread.sleep(100);
        }
        throw new AssertionError("No email reached Mailpit within " + within);
    }
}
