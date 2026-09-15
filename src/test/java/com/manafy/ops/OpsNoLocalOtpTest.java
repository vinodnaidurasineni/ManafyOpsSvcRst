package com.manafy.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Ops does NOT own OTP and cannot accidentally enable a local-OTP / local
 * authentication path (task §9/§10). Authentication in Ops is exclusively Cognito
 * JWT validation; OTP delivery (via MSG91) is a Cognito-Lambda concern, never Ops.
 *
 * This is the Ops-side "local OTP cannot be enabled by default" safeguard: there is
 * simply no OTP surface here to enable — no OTP controller, no OTP config, no
 * self-issued JWT secret. The safeguard is structural, not a flag that could flip on.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpsNoLocalOtpTest {

    @Autowired ApplicationContext ctx;
    @Autowired Environment env;

    @Test
    void opsHasNoOtpController() {
        // No @RestController whose type name hints at OTP. (Ops HAS an AuthController,
        // but it only exposes /auth/me + /auth/logout for the Cognito principal — it
        // issues no tokens and performs no OTP; that is the correct resource-server
        // shape, so the guard targets OTP specifically, not the word "auth".)
        boolean anyOtpController = java.util.Arrays.stream(
                        ctx.getBeanNamesForAnnotation(RestController.class))
                .map(name -> ctx.getBean(name).getClass().getSimpleName().toLowerCase())
                .anyMatch(simple -> simple.contains("otp"));
        assertThat(anyOtpController)
                .as("Ops must expose no OTP controller — Cognito-only, OTP is a Cognito/Lambda concern")
                .isFalse();
    }

    @Test
    void opsHasNoLocalOtpOrOtpConfiguration() {
        // No OTP / local-OTP / self-JWT configuration should be present in Ops.
        assertThat(env.getProperty("otp.expose-in-response")).isNull();
        assertThat(env.getProperty("manafy.auth.local-otp-enabled")).isNull();
        assertThat(env.getProperty("otp.length")).isNull();
        assertThat(env.getProperty("jwt.secret")).isNull();
    }

    @Test
    void opsHasNoOtpOrJwtIssuanceBeans() {
        // No bean resembling an OTP repository/service or a JWT-issuing utility.
        boolean anyOtpBean = java.util.Arrays.stream(ctx.getBeanDefinitionNames())
                .map(String::toLowerCase)
                .anyMatch(name -> name.contains("otp")
                        || name.equals("jwtutil")
                        || name.contains("jwtissu")
                        || name.contains("smssender"));
        assertThat(anyOtpBean)
                .as("Ops must have no OTP/JWT-issuance/SMS beans")
                .isFalse();
    }
}
