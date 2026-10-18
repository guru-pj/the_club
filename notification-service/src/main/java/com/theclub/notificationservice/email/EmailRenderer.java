package com.theclub.notificationservice.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders a template into a subject, an HTML body and a plain-text body. Variables are escaped by
 * Thymeleaf ({@code th:text}), so caller-supplied values can never inject markup.
 */
@Component
public class EmailRenderer {

    public record Rendered(String subject, String html, String text) {
    }

    private final TemplateEngine engine = new TemplateEngine();

    public EmailRenderer() {
        engine.addTemplateResolver(resolver("html/*", TemplateMode.HTML, ".html", 1));
        engine.addTemplateResolver(resolver("text/*", TemplateMode.TEXT, ".txt", 2));
    }

    private static ClassLoaderTemplateResolver resolver(String pattern, TemplateMode mode, String suffix, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);
        resolver.setOrder(order);
        return resolver;
    }

    public Rendered render(EmailTemplate template, Map<String, Object> variables) {
        Map<String, Object> values = new HashMap<>(variables);
        values.put("footerReason", template.footer());
        Context context = new Context(Locale.ENGLISH, values);
        String html = engine.process("html/" + template.id(), context);
        String text = engine.process("text/" + template.id(), context);
        return new Rendered(subject(template.subject(), variables), html, text.strip() + "\n");
    }

    static String subject(String pattern, Map<String, Object> variables) {
        String subject = pattern;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            subject = subject.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return subject;
    }
}
