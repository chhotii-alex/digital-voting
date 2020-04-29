function getCookieValue(key) {
    key = key + '=';
    var cookieArray = document.cookie.split(';');
    for (var i = 0; i < cookieArray.length; i++) {
    	var c = cookieArray[i];
	    while (c.charAt(0) == ' ') c = c.substring(1);
	    if (c.indexOf(key) == 0) {
            return c.substring(key.length, c.length);
	    }
    }
    return "";
}
function getUser() {
    return getCookieValue("user");
}
