package com.theclub.notificationservice.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.notificationservice.config.MailProperties;
import com.theclub.notificationservice.dto.EmailAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ZeptoMailEmailProviderTest {

    private static final String URL = "https://api.zeptomail.in/v1.1/email";

    MockRestServiceServer server;
    ZeptoMailEmailProvider provider;

    private final OutgoingEmail email = new OutgoingEmail("no-reply@theclub.example", "The Club", "help@theclub.example",
            "runner@example.com", "Your ticket: Sunday Run", "<p>hi</p>", "hi\n",
            List.of(new EmailAttachment("ticket.png", "image/png", "QUJD", true, "ticket-qr"),
                    new EmailAttachment("terms.pdf", "application/pdf", "UERG", false, null)),
            "0192aa00-0000-7000-8000-000000000001");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ZeptoMailEmailProvider(builder.build(),
                new MailProperties.ZeptoMail(URL, "Zoho-enczapikey secret-token", null, null), new ObjectMapper());
    }

    @Test
    void postsTheDocumentedPayloadWithTheSendMailToken() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                // a token pasted with its prefix is not doubled
                .andExpect(header("Authorization", "Zoho-enczapikey secret-token"))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from.address").value("no-reply@theclub.example"))
                .andExpect(jsonPath("$.from.name").value("The Club"))
                .andExpect(jsonPath("$.to[0].email_address.address").value("runner@example.com"))
                .andExpect(jsonPath("$.reply_to[0].address").value("help@theclub.example"))
                .andExpect(jsonPath("$.subject").value("Your ticket: Sunday Run"))
                .andExpect(jsonPath("$.htmlbody").value("<p>hi</p>"))
                .andExpect(jsonPath("$.textbody").value("hi\n"))
                .andExpect(jsonPath("$.inline_images[0].cid").value("ticket-qr"))
                .andExpect(jsonPath("$.inline_images[0].content").value("QUJD"))
                .andExpect(jsonPath("$.inline_images[0].mime_type").value("image/png"))
                .andExpect(jsonPath("$.attachments[0].name").value("terms.pdf"))
                .andExpect(jsonPath("$.attachments[0].mime_type").value("application/pdf"))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.client_reference").value("0192aa00-0000-7000-8000-000000000001"))
                .andExpect(jsonPath("$.track_clicks").value(false))
                .andExpect(jsonPath("$.track_opens").value(false))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"data":[{"code":"EM_104","additional_info":[],"message":"Email request received"}],
                         "message":"OK","request_id":"2d6f.7b2a"}"""));

        assertThat(provider.send(email)).isEqualTo("2d6f.7b2a");
        server.verify();
    }

    @Test
    void clientErrorsArePermanentAndReportTheZeptoMailCodeNotTheToken() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"code":"TM_3501","details":[{"code":"SERR_157","message":"Invalid API Token found","target":"Authorization"}],
                          "message":"Invalid API Token found","request_id":"x"}}"""));

        assertThatThrownBy(() -> provider.send(email))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> {
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("HTTP 401", "TM_3501", "SERR_157").doesNotContain("secret-token");
                });
    }

    @Test
    void rateLimitsAndServerErrorsAreRetryable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> provider.send(email))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> assertThat(e.isRetryable()).isTrue());

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> provider.send(email))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void networkFailuresAreRetryable() {
        server.expect(requestTo(URL)).andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> provider.send(email))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void refusesToStartWithoutAToken() {
        assertThatThrownBy(() -> new ZeptoMailEmailProvider(RestClient.create(),
                new MailProperties.ZeptoMail(URL, " ", null, null), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zeptomail-token");
    }
}
