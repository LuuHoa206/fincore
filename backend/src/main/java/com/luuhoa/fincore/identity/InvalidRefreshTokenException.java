package com.luuhoa.fincore.identity;

import com.luuhoa.fincore.shared.api.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired, or revoked");
    }
}
