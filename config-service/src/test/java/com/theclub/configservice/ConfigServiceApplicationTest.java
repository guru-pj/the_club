package com.theclub.configservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServiceApplicationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void servesNativeConfig() {
        String body = rest.getForObject("/user-service/default", String.class);
        assertThat(body).contains("Asia/Kolkata");
    }
}
