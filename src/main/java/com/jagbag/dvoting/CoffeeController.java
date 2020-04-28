package com.jagbag.dvoting;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Exception to be raised if we ask a teapot (or any other unsuitable object, actually) to brew coffee.
 * Raising this in an endpoint handler will cause the "I'm a teapot" status code to be returned to the client.
 */
@ResponseStatus(code = HttpStatus.I_AM_A_TEAPOT, reason = "I'm not a coffeemaker.")
class TeapotException extends RuntimeException {
}

/**
 * Coffee endpoint is never going to be functionally implemented. But hey, we can at least return the appropriate
 * status code!
 * Should work on any server, not just a teapot.
 */
@RestController
public class CoffeeController extends APIController {
    @GetMapping("/coffee")
    public String brewMyCoffee() {
        throw new TeapotException();
    }
}
