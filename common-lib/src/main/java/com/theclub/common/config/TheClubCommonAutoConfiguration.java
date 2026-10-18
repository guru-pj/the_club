package com.theclub.common.config;

import com.theclub.common.security.InternalApiKeyFilter;
import com.theclub.common.security.JwtAuthenticationFilter;
import com.theclub.common.security.JwtProperties;
import com.theclub.common.security.JwtService;
import com.theclub.common.security.ProblemAuthenticationEntryPoint;
import com.theclub.common.security.Roles;
import com.theclub.common.security.SecurityProperties;
import com.theclub.common.web.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Stateless JWT security, internal API key auth, problem-detail errors and a zone-aware Clock
 * for every servlet-based The Club service.
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
@EnableMethodSecurity
@Import(GlobalExceptionHandler.class)
public class TheClubCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock(@Value("${theclub.time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties properties, Clock clock) {
        return new JwtService(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain theClubSecurityFilterChain(HttpSecurity http, JwtService jwtService,
                                                          SecurityProperties properties) throws Exception {
        ProblemAuthenticationEntryPoint problems = new ProblemAuthenticationEntryPoint();
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/internal/**").hasRole(Roles.INTERNAL);
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    properties.publicPaths().forEach(path -> auth.requestMatchers(path).permitAll());
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new InternalApiKeyFilter(properties.internalApiKey()), JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Adds the internal API key to every outgoing Feign call so downstream /internal/** endpoints accept it,
     * and maps unhandled Feign errors to 502/503.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignInternalKeyConfiguration {

        @Bean
        public feign.RequestInterceptor internalApiKeyRequestInterceptor(SecurityProperties properties) {
            return template -> {
                if (properties.internalApiKey() != null) {
                    template.header(InternalApiKeyFilter.HEADER, properties.internalApiKey());
                }
            };
        }

        @Bean
        public com.theclub.common.web.FeignExceptionHandler feignExceptionHandler() {
            return new com.theclub.common.web.FeignExceptionHandler();
        }
    }
}
