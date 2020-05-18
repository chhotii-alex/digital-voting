package com.jagbag.dvoting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception to be raised in an endpoint handler when the specified item does not exist.
 */
@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "item not found")
public class ItemNotFoundException extends RuntimeException {
}
