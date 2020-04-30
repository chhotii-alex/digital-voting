// validate that it's a reasonably ok-looking email address
function isEmailValid(email) {
    var pattern = /^\S+@\S+\.\S+$/ ;
    return email.match(pattern);
}

