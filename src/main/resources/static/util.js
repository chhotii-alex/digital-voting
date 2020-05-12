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