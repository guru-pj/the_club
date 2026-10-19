package com.theclub.notificationservice.config;

import tools.jackson.databind.ObjectMapper;
import com.theclub.notificationservice.email.EmailProvider;
import com.theclub.notificationservice.email.LogEmailProvider;
import com.theclub.notificationservice.email.SmtpEmailProvider;
import com.theclub.notificationservice.email.ZeptoMailEmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * Picks the delivery provider from MAIL_PROVIDER and refuses unsafe production setups at startup.
     */
    @Bean
    public EmailProvider emailProvider(MailProperties properties, Environment environment,
                                       ObjectProvider<JavaMailSender> mailSender,
                                       ObjectProvider<RestClient.Builder> restClients, ObjectMapper json) {
        String provider = properties.resolvedProvider();
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        EmailProvider selected = switch (provider) {
            case MailProperties.ZEPTOMAIL -> {
                var factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(properties.zeptomail().connectTimeout());
                factory.setReadTimeout(properties.zeptomail().readTimeout());
                RestClient client = restClients.getIfAvailable(RestClient::builder).requestFactory(factory).build();
                yield new ZeptoMailEmailProvider(client, properties.zeptomail(), json);
            }
            case MailProperties.SMTP -> new SmtpEmailProvider(mailSender.getObject());
            case MailProperties.LOG -> {
                if (production) {
                    throw new IllegalStateException("MAIL_PROVIDER=log would silently drop every email; "
                            + "use zeptomail (or smtp) with the prod profile. See docs/EMAIL_SETUP.md");
                }
                yield new LogEmailProvider();
            }
            default -> throw new IllegalStateException("Unknown MAIL_PROVIDER '" + provider
                    + "' (expected zeptomail, smtp or log)");
        };
        log.info("Email provider: {}{}", selected.name(),
                properties.redirecting() ? " (all emails redirected to the configured MAIL_REDIRECT_ALL_TO address)" : "");
        return selected;
    }
}
