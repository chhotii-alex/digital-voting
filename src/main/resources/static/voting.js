// global variables
gPublicKey = null;
gModulus = null;
gTrouble = false;

function LogTrouble(message) {
    console.log(message);
    gTrouble = true;
}

function isTrouble() {
    return gTrouble;
}

function setStorageString(key, cvalue) {
    window.localStorage.setItem(key, encodeURIComponent(cvalue));
}
function setStorageData(key, data) {
    if (!getUser()) {
        throw "Valid user not known or not logged in.";
    }
    var existingData = getStorageString(getUser());
    if (existingData) {
        existingData = JSON.parse(existingData);
    }
    if (!existingData) {
        existingData = {};
    }
    existingData[key] = data;
    setStorageString(getUser(), JSON.stringify(existingData));
}
function getStorageString(key) {
      var value = window.localStorage.getItem(key);
      value = decodeURIComponent(value);
      return value;
}
function getStorageData(key) {
    if (!getUser()) {
        throw "Valid user not known or not logged in.";
    }
    let str = getStorageString(getUser());
      if (str) {
        try {
            let obj = JSON.parse(str);
            return obj[key];
        }
        catch (err) {
            console.log("problem with json encoding?: " + str + " " + err);
        }
    }
    return null;
}

function getArchivedBallotIdentifiers() {
    if (!getUser()) {
        throw "Valid user not known or not logged in.";
    }
    var results = [];
    let str = getStorageString(getUser());
    if (str) {
        try {
            let obj = JSON.parse(str);
            for (const key in obj) {
                let fields = key.split('.');
                if (fields[0] == "ballot" && fields.length > 1) {
                    results.push(fields[1]);
                }
            }
        }
        catch (err) {
            console.log("problem with json encoding?: " + str + " " + err);
        }
    }
    return results;
}

// See https://www.npmjs.com/package/big-integer for BigInteger class documentation.
/* Equivalent to the Java code: new BigInteger(s.getBytes(UTF_8))   */
function stringToBigInteger(s) {
        var b = stringToUtf8ByteArray(s);
        var bi = bigInt();
        var i;
        for (i = 0; i < b.length; ++i) {
            bi = bi.shiftLeft(8);
            bi = bi.add(b[i]);
        }
        return bi;
}
/* Encode a BigInteger as relatively compact string  */
function encoded(bi) {
    return bi.toString(36);
}
/* Read a BigInteger from the relatively compact string format */
function decoded(s) {
    return bigInt(s, 36);
}
Random = {
    // TODO: replace Math.random with something more cryptographically respectable.
    randomBig: function(min, max) {
        return bigInt.randBetween(min, max, Math.random);
    },
};
class VotableQuestion extends Question {
    constructor(obj) {
        super();
        this.id = obj.id;
        this.addResponseOptionsFrom(obj);
        this.text = obj.text;
        this.closed = false;
        let ballotKey = "ballot." + this.id;
        let savedBallotInfo = getStorageData(ballotKey);
        this.ballot = new Ballot(this, ballotKey, savedBallotInfo);
    }
    canVote() {
        return (!this.closed && this.ballot.currentlySelectedResponse
                && (!this.ballot.submittedResponse || !this.ballot.voteAcknowledged)
                && this.ballot.areAllChitsSigned() );
    }
}
/*  TODO: How do we keep votes confidential between people sharing a computer?
*/
class Chit {
    constructor(questionID, obj = null) {
        if (obj) {
            this.questionID = obj.questionID;
            this.number = bigInt(obj.number);
            this.n = bigInt(obj.n);
            this.k = bigInt(obj.k);
            this.e = bigInt(obj.k);
            this.signedMessageText = obj.signedMessageText;
        }
        else {
            this.questionID = questionID;
            this.number = Random.randomBig(1, bigInt('9223372036854775807'));
        }
    };
    getMessageText() {
        return `${ this.questionID } ${ this.number } ${ this.getText() }`;
    };
    getM() {   // message as a BigInteger
        var m = stringToBigInteger(this.getMessageText());
        return m;
    };
    blindedMessageText(n, k, e) {
        this.n = n;
        this.k = k;
        this.e = e;
        var blinded = this.getM().multiply(k.modPow(e, n)).mod(n);
        return encoded(blinded);
    };
    acceptSignedBlindedText(response) {
        var signedBlindedChitText = response.data;
        var n = this.n;
        var k = this.k;
        var e = this.e;
        var signedT = decoded(signedBlindedChitText);
        var s = k.modInv(n).multiply(signedT).mod(n); // s should be the signed but unblinded version of m
        var allegedMessageText = s.modPow(e, n);  // un-sign with public key to confirm that it's original
        if (allegedMessageText.compare(this.getM()) == 0) {
            this.signedMessageText = encoded(s);
        }
        else {
            LogTrouble("Signed message from CTF is not my message");
        }
    };
    matchesID(id) {
        return (this.number.compare(id) == 0);
    };
    isSigned() {
        return this.signedMessageText;
    }
};
class PersonalChit extends Chit {
    constructor(questionID, obj = null) {
        super(questionID, obj);
    };
    getText() {
        return "me";
    };
};
class ResponseChit extends Chit {
    constructor(questionID, responseOption, obj = null) {
        super(questionID, obj);
        if (obj) {
            this.myResponse = new ResponseOption(obj.myResponse.text);
        }
        else {
            this.myResponse = responseOption;
        }
    };
    getText() {
        return this.myResponse.getText();
    };
};
class Ballot {
    constructor(question, ballotKey, savedBallotInfo) {
        this.theQuestion = question;
        this.ballotKey = ballotKey;
	    this.verificationMessage = '';
	    this.results = {};
        if (savedBallotInfo) {
            this.currentlySelectedResponse = savedBallotInfo.currentlySelectedResponse;
            this.voteAcknowledged = savedBallotInfo.voteAcknowledged;
            this.submittedResponse = savedBallotInfo.submittedResponse;
            this.personalChit = new PersonalChit(question.id, savedBallotInfo.personalChit);
            this.responseChits = [];
            savedBallotInfo.responseChits.forEach( (obj, i) => {
                var responseChit = new ResponseChit(question.id, obj.myResponse, obj);
                this.responseChits.push(responseChit);
            } );
        }
        else {
    	    this.currentlySelectedResponse = null;
	        this.voteAcknowledged = false;
            this.submittedResponse = null;  // this is only given a value when vote submitted
            this.personalChit = new PersonalChit(question.id);
            this.responseChits = [];
            question.possibleResponses.forEach( (option, i) => {
		        var responseChit = new ResponseChit(question.id, option);
                    this.responseChits.push(responseChit);
            } );
        }
        this.allChits = [];
        this.allChits.push(this.personalChit);
        this.responseChits.forEach( ( chit, i) => {
            this.allChits.push(chit);
        } );
        if (!(savedBallotInfo && this.areAllChitsSigned())) {
            this.getSignedForUser();
        }
    };
    saveToLocalStorage() {
        var savedBallotInfo = {};
        savedBallotInfo.currentlySelectedResponse = this.currentlySelectedResponse;
        savedBallotInfo.voteAcknowledged = this.voteAcknowledged;
        savedBallotInfo.submittedResponse = this.submittedResponse;
        savedBallotInfo.personalChit = this.personalChit;
        savedBallotInfo.responseChits = this.responseChits;
        setStorageData(this.ballotKey, savedBallotInfo);
    }
    static randomKforModulus(n) {
        var k;
        do {
            k = Random.randomBig(1, n);
        } while (bigInt.gcd(k,n).compare(bigInt.one) != 0);
        return k;
    };
    areAllChitsSigned() {
        var anyUnsigned = false;
        var i;
        for (i = 0; i < this.allChits.length; ++i) {
            if (!this.allChits[i].isSigned()) {
               anyUnsigned = true;
            }
        }
        return !anyUnsigned;
    }
    getSignedForUser() {
	    this.n = gModulus;
        this.e = gPublicKey;
        this.k = Ballot.randomKforModulus(this.n);
        this.errorCountPerSigning = 0;
        this.allChits.forEach( (chit, i) => {
		    var blindedChitText = chit.blindedMessageText(this.n, this.k, this.e);
            this.signOneChit(chit, blindedChitText, this.theQuestion.id);
	    });
	    this.saveToLocalStorage();
    };
    signOneChit(chit, blindedChitText, quid) {
        let url = "ballot/" + quid + "/sign";
        let promise = axios.post(url, { b: blindedChitText });
        promise.then( response => {
            chit.acceptSignedBlindedText(response);
            this.saveToLocalStorage();
         })
        .catch( (error) => {
            console.log(error.response);
            if (!this.errorCountPerSigning) {
                if (error.response && (error.response.status == 403)) {   // Forbidden
                    // Why would the CTF refuse to sign? The only likely possibility is that the question closed
                    // just as we loaded. Re-fetch the question list so that we can detect that it closed (if so.)
                    voterApp.checkForNewQuestions();
                }
                else {  // TODO: test that general internet glitchyness would end up here?
                    alert("Internet glitch? Glitch in communication with server. Please check your network and try re-loading this page.");
                }
            }
            ++this.errorCountPerSigning;
        });
    };
    chitForResponse(response) {
        var chit = null;
        this.responseChits.forEach( (ch, i) => {
		    if (ch.getText() == response) {
		        chit = ch;
		    }
	    });
	    return chit;
    };
    setCurrentlySelectedResponse(opt) {
        if (!this.submittedResponse) {
            this.currentlySelectedResponse = opt;
            this.saveToLocalStorage();
        }
        // otherwise, if this is already voted, can't change response
    };
    classForOption(text) {
        if (text == this.currentlySelectedResponse) {
            return "selected";
        }
        else {
            return "notselected";
        }
    };
    vote() {
        let prompt = "Do you want to vote " + this.currentlySelectedResponse + " on the question " + this.theQuestion.text;
        if (!confirm(prompt)) { return; }
        this.submitVote();
    };
    submitVote() {
        this.submittedResponse = this.currentlySelectedResponse;
        var responseChit = this.chitForResponse(this.currentlySelectedResponse);
        let payload = { meChit: this.personalChit.getMessageText(),
                    meChitSigned: this.personalChit.signedMessageText,
                    responseChit: responseChit.getMessageText(),
                    responseChitSigned: responseChit.signedMessageText };
        let url = "ballot/" + this.theQuestion.id + "/vote";
        let promise = axios.post(url, payload);
        promise.then( response => this.processVoteResponse(response) )
            .catch(error => this.processVoteError(error));
    };
    processVoteResponse(response) {
        this.voteAcknowledged = true;
        this.saveToLocalStorage();
        this.verifyVote();
    };
    processVoteError(error) {
        console.log(error.response);
        console.log(error.response.status);
        this.submittedResponse = null;
        if (error.response.status == 410) {
            voterApp.checkForNewQuestions();
            alert("Unfortunately, this question was closed before you tried to vote, thus your vote was not counted.");
        }
        else {
            LogTrouble("vote failed to post");
            alert("There was some problem with submitting your vote to the CTF.");
        }
    };
    iVoted() {
        return (this.submittedResponse != null);
    };
    verifyVote() {
        let url = "ballot/" + this.theQuestion.id + "/verify";
        let promise = axios.get(url);
        promise.then( response => this.processVerificationData(response) )
            .catch( error => LogTrouble(error) );
    }
    processVerificationData(response) {
        var responseChit = this.chitForResponse(this.submittedResponse);
        this.verificationMessage = '';
        var responseFound = false;
        var report = response.data;
        this.results = {};
        this.theQuestion.possibleResponses.forEach( (option, i) => {
            this.results[option.getText()] = 0;
        });
        var i;
        for (i = 0; i < report.length; ++i) {
	        var record = report[i];
		    var questionID = record.question.id;
            if (questionID != this.theQuestion.id) { continue; }
            if (this.results[record.response]) {
                this.results[record.response] += 1;
            }
            else {   // Covers the case that a response was not in the question's list of possible responses...
                this.results[record.response] = 1;  // which shouldn't happen, as things work now, but just in case...
            }
            var voterID = record.voterChitNumber;
            if (!this.personalChit.matchesID(voterID)) { continue; }
            if (!this.iVoted()) {
		            this.verificationMessage = "The CTF claims I voted, and I don't remember voting!";
		            LogTrouble(this.verificationMessage);
                    return;
            }
            else {
		            var responseID = record.responseChitNumber;
		            if (!responseChit.matchesID(responseID)) {
			            this.verificationMessage = "The CTF reports an invalid number for my response!";
    		            LogTrouble(this.verificationMessage);
			            return;
		            }
		            var responseInReport = record['response'];
		            if (responseInReport == this.submittedResponse) {
			            responseFound = true;
		            }
                    else {
                        this.verificationMessage = "The CTF claims I voted differently than I remember!";
    		            LogTrouble(this.verificationMessage);
                        return;
                    }
            }
	    }
	    if (this.iVoted() == responseFound) {
	        if (this.iVoted()) {
    	        this.verificationMessage = 'Verified!';
    	    }
    	    else {
    	        this.verificationMessage = '';
    	    }
	    }
	    else {
	        this.verificationMessage = "Verification of vote failed.";
            LogTrouble(this.verificationMessage);
	    }
    };
};
var voterApp = new Vue({
    el: '#voterapp',
    data: {
        visible: true,
        username: null,
        name: null,
        email: null,
        allowedToVote: false,
        errorText: '',
        votableQuestions: [],
        isKeyInfoKnown: false,
        showOldQuestions: false,
    },
    mounted() {
        this.$data.username = getUser();
        let url = "voters/" + this.$data.username;
        let aPromise = axios.get(url);
        aPromise.then(response => this.processUserInfo(response), error => this.dealWithError(error));
    },
    computed: {
        anyQuestionsVoted: function() {
            var anyVoted = false;
            this.$data.votableQuestions.forEach( q => {
                if (q.ballot.iVoted()) {
                    anyVoted = true;
                }
            } );
            return anyVoted;
        }
    },
    methods: {
        processUserInfo: function(response) {
            this.$data.name= response.data.name;
            this.$data.email = response.data.email;
            this.$data.allowedToVote = response.data.allowedToVote;
            this.$data.admin = response.data.admin;
            if (this.$data.allowedToVote) {
                this.fetchCTFKeys();
            }
        },
        dealWithError: function(error) {
            this.$data.errorText = "Error: " + error;
            LogTrouble(this.$data.errorText);
        },
        fetchCTFKeys: function() {
            let url = "ballots/keys/";
            let promise = axios.get(url);
            promise.then(response => this.processKeys(response), error => this.dealWithError(error));
        },
        processKeys: function(response) {
            gPublicKey = response.data.public;
            gModulus = response.data.modulus;
            this.$data.isKeyInfoKnown = true;
            this.checkForNewQuestions();
        },
        checkForNewQuestions: function() {  // from voter's POV: questions that they can vote on now
            let url = "ballots/";
            let aPromise = axios.get(url);
            aPromise.then(response => this.processOpenQuestions(response), error => this.dealWithError(error));
        },
        startShowingOldQuestions: function() {
            this.$data.showOldQuestions = true;
            let ids = getArchivedBallotIdentifiers();
            var i;
            for (i = 0; i < ids.length; ++i) {
                let id = ids[i];
                var found = false;  // whether it's already in the list we're showing
                var j;
                for (j = 0; j < this.$data.votableQuestions.length; ++j) {
                     if (id ==  this.$data.votableQuestions[j].id) {
                         found = true; break;
                     }
                }
                if (!found) {
                    let url = "/questions/" + id;
                    let promise = axios.get(url);
                    promise.then( response => this.processOldQuestion(response));
                }
            }
        },
        processOldQuestion: function(response) {
            let obj = response.data;
            var q = new VotableQuestion(obj);
            this.$data.votableQuestions.push(q);
        },
        processOpenQuestions: function(response) {
            var i;
            var j;
            for (j = 0; j < this.$data.votableQuestions.length; ++j) {
                var found = false;
                for (i = 0; i < response.data.length; ++i) {
                    if (response.data[i].id === this.$data.votableQuestions[j].id) {
                        found = true; break;
                    }
                }
                if (!found) {
                    this.$data.votableQuestions[j].closed = true;
                }
            }
            for (i = 0; i < response.data.length; ++i) {
                var obj = response.data[i];
                var found = false;
                for (j = 0; j < this.$data.votableQuestions.length; ++j) {
                    if (obj.id ===  this.$data.votableQuestions[j].id) {
                        found = true; break;
                    }
                }
                if (!found) {
                    /* Only process newly opened questions and instantiate instances of VotableQuestion after
                        the query for the CTF's key info has returned the numbers! A VotableQuestion's Ballot's
                        Chits can only choose its random numbers, and get signed, after we have those numbers.
                    */
                    if (this.isKeyInfoKnown) {
                        var q = new VotableQuestion(obj);
                        this.$data.votableQuestions.push(q);
                    }
                }
            }
            this.verifyAllNow();
        },
        verifyAll: function() {
            this.$data.votableQuestions.forEach( q => {
                q.ballot.verifyVote();
            });
        },
        verifyAllNow: function() {
            this.verifyAll();
            if (this.$data.updateTimerToken) {
                clearInterval(this.$data.updateTimerToken);
                this.$data.updateTimerToken = '';
            }
            this.$data.updateTimerToken = setInterval( () => this.verifyAll(), 2*60*1000);
        }
    },
});

