package com.jagbag.dvoting;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception to be raised in an endpoint handler when an operation that might otherwise be legitimate is
 * attempted when the client is not logged in.
 */
@ResponseStatus(code= HttpStatus.UNAUTHORIZED, reason="User not looged in.")
public class UnauthorizedException extends RuntimeException { // thrown when we should return status 401
}
