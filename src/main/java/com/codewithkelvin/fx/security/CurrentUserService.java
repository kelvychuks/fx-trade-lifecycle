package com.codewithkelvin.fx.security;

import com.codewithkelvin.fx.common.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the caller to a persisted user, so that lifecycle events can record
 * <em>who</em> did something rather than just that it happened.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository userRepository;

    @Transactional(readOnly = true)
    public AppUser require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessRuleException("NOT_AUTHENTICATED", "No authenticated user on this request");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessRuleException(
                        "UNKNOWN_USER",
                        "Token is valid but user '" + authentication.getName() + "' no longer exists"));
    }
}
