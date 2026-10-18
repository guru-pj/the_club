package com.theclub.notificationservice.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theclub.notificationservice.config.MailConfig;
import com.theclub.notificationservice.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailConfigTest {

    private final StaticListableBeanFactory beans = new StaticListableBeanFactory();

    MailConfigTest() {
        beans.addBean("mailSender", new JavaMailSenderImpl());
    }

    private EmailProvider provider(String provider, boolean enabled, String token, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        MailProperties props = new MailProperties(provider, enabled, null, null, null,
                new MailProperties.ZeptoMail(null, token, null, null), null, null);
        return new MailConfig().emailProvider(props, env, beans.getBeanProvider(JavaMailSender.class),
                beans.getBeanProvider(RestClient.Builder.class), new ObjectMapper());
    }

    @Test
    void selectsTheConfiguredProvider() {
        assertThat(provider("zeptomail", false, "tok").name()).isEqualTo("zeptomail");
        assertThat(provider("smtp", false, null).name()).isEqualTo("smtp");
        assertThat(provider("log", false, null).name()).isEqualTo("log");
    }

    @Test
    void blankProviderFallsBackToTheLegacyEnabledFlag() {
        assertThat(provider("", true, null).name()).isEqualTo("smtp");
        assertThat(provider(null, false, null).name()).isEqualTo("log");
    }

    @Test
    void refusesUnsafeOrBrokenSetups() {
        assertThatThrownBy(() -> provider("zeptomail", false, "")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider("log", false, null, "prod")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        assertThatThrownBy(() -> provider("sendgrid", false, null)).isInstanceOf(IllegalStateException.class);
        assertThat(provider("smtp", false, null, "prod").name()).isEqualTo("smtp");
    }
}
