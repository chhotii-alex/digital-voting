// validate that it's a reasonably ok-looking email address
function isEmailValid(email) {
    var pattern = /^\S+@\S+\.\S+$/ ;
    return email.match(pattern);
}

function handleQueryError(error) {
            if (error.response) {
                if (error.response.status == 401) {  // Not logged in as having admin priv
                    alert("Either you were logged out, or your permission to do this operation was revoked.");
                    window.location.href = "/";
                }
                else {
                    alert(error);
                }
            }
            else {
                alert("Network glitch. Please check the network connection and try again.");
            }
            console.log(error);
}

gLoginTimeoutToken = null;
gFinalLogoutTimerToken = null;

gUserInactivityWarningTime = 1000*60*60*8;   // 8 hours
gPostWarningLogoutTime = 1000*60*5;      // 5 minutes

function userActive() {
    if (gLoginTimeoutToken) {
        clearTimeout(gLoginTimeoutToken);
        gLoginTimeoutToken = null;
    }
    if (gFinalLogoutTimerToken) {
        clearTimeout(gFinalLogoutTimerToken);
        gFinalLogoutTimerToken = null;
    }
    gLoginTimeoutToken = setTimeout(inactivityTimeout, gUserInactivityWarningTime);
}

function inactivityTimeout() {
    window.location.href = "/inactivity.html";
}
