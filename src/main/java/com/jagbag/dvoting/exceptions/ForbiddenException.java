package com.jagbag.dvoting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception to be raised in an endpoint controller when an operation that might otherwise be legitimate
 * is attempted by an unauthorized user.
 * TODO: organize dvoting into subpackages. Exceptions into one subpackage, controllers into another, entities...
 */
@ResponseStatus(code = HttpStatus.FORBIDDEN, reason="User does not have permission to do this.")
public class ForbiddenException extends RuntimeException { // thrown when we should return status 403
}
