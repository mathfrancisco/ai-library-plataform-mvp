package com.ailibrary.common.security;

import com.ailibrary.auth.domain.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test void roundTripsClaims() {
        var props = new AuthProperties("0123456789abcdef0123456789abcdef", 15, 30, null, null);
        var jwt = new JwtService(props);
        var user = new User("dev@example.com", "hash", "Dev");
        String token = jwt.issueAccessToken(user);
        var decoded = jwt.decode(token);
        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("dev@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("USER");
    }
}
