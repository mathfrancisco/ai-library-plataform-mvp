package com.ailibrary.common.security;

import com.ailibrary.common.error.ApiException;
import com.ailibrary.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public UserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authenticated user required");
        }
        return principal;
    }

    public UUID id() {
        return principal().id();
    }
}
