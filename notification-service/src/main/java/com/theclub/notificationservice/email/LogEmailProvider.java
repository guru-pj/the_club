package com.theclub.notificationservice.email;

import com.theclub.notificationservice.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development provider: records that an email would have been sent (recipient and subject only).
 */
public class LogEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(LogEmailProvider.class);

    @Override
    public String name() {
        return MailProperties.LOG;
    }

    @Override
    public String send(OutgoingEmail email) {
        log.info("Email delivery is set to 'log'; would send '{}' to {} ({} attachment(s))",
                email.subject(), email.to(), email.attachments().size());
        return null;
    }
}
