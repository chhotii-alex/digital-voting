package com.jagbag.dvoting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "invalid parameters")
public class BadRequestException extends RuntimeException {
}

