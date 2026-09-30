package com.ailibrary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(PostgresIntegrationTest.DATABASE_AVAILABLE)
class AuthFlowIT extends PostgresIntegrationTest {
    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void refreshRotatesAndReuseSignsOutEverySession() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        Res reg = call(
                "POST",
                "/api/auth/register",
                null,
                "{\"email\":\"" + email + "\",\"password\":\"password1\",\"displayName\":\"Flow\"}");
        String r1 = reg.body().path("refreshToken").asString();

        Res rotated = call("POST", "/api/auth/refresh", null, "{\"refreshToken\":\"" + r1 + "\"}");
        assertThat(rotated.status()).isEqualTo(200);
        String r2 = rotated.body().path("refreshToken").asString();

        Res reused = call("POST", "/api/auth/refresh", null, "{\"refreshToken\":\"" + r1 + "\"}");
        assertThat(reused.status()).isEqualTo(401);
        assertThat(reused.body().path("code").asString()).isEqualTo("INVALID_REFRESH_TOKEN");
        // Reuse detection revoked the legitimate newer token as well.
        assertThat(call("POST", "/api/auth/refresh", null, "{\"refreshToken\":\"" + r2 + "\"}")
                        .status())
                .isEqualTo(401);

        Res login = call("POST", "/api/auth/login", null, credentials(email, "password1"));
        String r3 = login.body().path("refreshToken").asString();
        assertThat(call("POST", "/api/auth/logout", null, "{\"refreshToken\":\"" + r3 + "\"}")
                        .status())
                .isEqualTo(204);
        assertThat(call("POST", "/api/auth/refresh", null, "{\"refreshToken\":\"" + r3 + "\"}")
                        .status())
                .isEqualTo(401);
        assertThat(call("POST", "/api/auth/login", null, credentials(email, "wrong-pass"))
                        .body()
                        .path("code")
                        .asString())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void accountEndpointsUpdateProfileChangePasswordAndDelete() throws Exception {
        String email = "acct-" + UUID.randomUUID() + "@example.com";
        Res reg = call(
                "POST",
                "/api/auth/register",
                null,
                "{\"email\":\"" + email + "\",\"password\":\"password1\",\"displayName\":\"Old\"}");
        String token = reg.body().path("accessToken").asString();

        Res renamed = call("PATCH", "/api/auth/me", token, "{\"displayName\":\"New name\"}");
        assertThat(renamed.body().path("displayName").asString()).isEqualTo("New name");
        assertThat(renamed.body().path("createdAt").asString()).isNotBlank();

        Res wrong = call(
                "POST",
                "/api/auth/me/password",
                token,
                "{\"currentPassword\":\"nope-nope\",\"newPassword\":\"password2\"}");
        assertThat(wrong.body().path("code").asString()).isEqualTo("WRONG_PASSWORD");
        Res changed = call(
                "POST",
                "/api/auth/me/password",
                token,
                "{\"currentPassword\":\"password1\",\"newPassword\":\"password2\"}");
        assertThat(changed.status()).isEqualTo(200);
        String oldRefresh = reg.body().path("refreshToken").asString();
        assertThat(call("POST", "/api/auth/refresh", null, "{\"refreshToken\":\"" + oldRefresh + "\"}")
                        .status())
                .isEqualTo(401);
        assertThat(call("POST", "/api/auth/login", null, credentials(email, "password2"))
                        .status())
                .isEqualTo(200);

        String fresh = changed.body().path("accessToken").asString();
        assertThat(call("POST", "/api/auth/logout-all", fresh, null).status()).isEqualTo(204);
        assertThat(call("DELETE", "/api/auth/me", fresh, "{\"password\":\"password2\"}")
                        .status())
                .isEqualTo(204);
        assertThat(call("POST", "/api/auth/login", null, credentials(email, "password2"))
                        .status())
                .isEqualTo(401);
    }
}
