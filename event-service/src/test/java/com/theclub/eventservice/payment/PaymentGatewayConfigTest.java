package com.theclub.eventservice.payment;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayConfigTest {

    private static PaymentProperties props(String provider, PaymentProperties.Razorpay razorpay) {
        return new PaymentProperties(provider, null, razorpay, null, null, null, null);
    }

    private static PaymentGateway create(PaymentProperties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return PaymentGatewayConfig.create(properties, RestClient.builder(), environment);
    }

    @Test
    void providerIsRequired() {
        assertThatThrownBy(() -> create(props(null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PAYMENT_PROVIDER");
        assertThatThrownBy(() -> create(props(" ", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PAYMENT_PROVIDER");
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(() -> create(props("stripe", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown payment provider 'stripe'");
    }

    @Test
    void razorpayNeedsKeysAndWebhookSecret() {
        assertThatThrownBy(() -> create(props("razorpay", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RAZORPAY_KEY_ID");
        assertThatThrownBy(() -> create(props("razorpay", new PaymentProperties.Razorpay("rzp_key", "secret", null, null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RAZORPAY_WEBHOOK_SECRET");
        assertThat(create(props("razorpay", new PaymentProperties.Razorpay("rzp_key", "secret", "whsec", null))))
                .isInstanceOf(RazorpayPaymentGateway.class);
    }

    @Test
    void mockIsRefusedInProduction() {
        assertThatThrownBy(() -> create(props("mock", null), "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The mock payment provider cannot be used in production");
        assertThat(create(props("MOCK", null), "dev")).isInstanceOf(MockPaymentGateway.class);
    }
}
