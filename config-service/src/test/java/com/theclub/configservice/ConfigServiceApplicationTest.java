package com.theclub.configservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ConfigServiceApplicationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void servesNativeConfig() {
        String body = rest.getForObject("/user-service/default", String.class);
        assertThat(body).contains("Asia/Kolkata");
    }
}
