// global variables
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
        this.type = obj.type;
        this.closed = (obj.status == "closed");
        this.e = bigInt(obj.exponentStr);
        this.n = bigInt(obj.modulusStr);
        let ballotKey = "ballot." + this.id;
        let savedBallotInfo = getStorageData(ballotKey);
        if (this.type == Question.CountingTypeSingle) {
            this.ballot = new SingleChoiceBallot(this, ballotKey, savedBallotInfo);
        }
        else if (this.type == Question.CountingTypeRanked) {
            this.ballot = new RankedChoiceBallot(this, ballotKey, savedBallotInfo);
        }
    }
    canVote() {
        return (!this.closed && this.ballot.hasResponse()
                && (!this.ballot.iVoted() || !this.ballot.voteAcknowledged)
                && this.ballot.areAllChitsSigned() );
    }
    shouldShowResultsDetail() {
        return this.closed;
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
    getSignatureEndpoint() {
        return "/signme";
    }
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
    getSignatureEndpoint() {
        return "/sign";
    }
};
class Ballot {
    constructor(question, ballotKey, savedBallotInfo) {
        this.theQuestion = question;
        this.ballotKey = ballotKey;
	    this.verificationMessage = '';
	    this.results = {};
        if (savedBallotInfo) {
            this.initFromSavedBallot(savedBallotInfo);
        }
        else {
            this.init();
        }
        this.allChits = [];
        this.allChits.push(this.personalChit);
        this.responseChits.forEach( ( chit, i) => {
            this.allChits.push(chit);
        } );
        if (!this.theQuestion.closed) {
            if (!this.areAllChitsSigned()) {
                this.getSignedForUser();
            }
        }
    };
    init() {
	        this.voteAcknowledged = false;
            this.personalChit = new PersonalChit(this.theQuestion.id);
            this.responseChits = [];
            this.theQuestion.possibleResponses.forEach( (option, i) => {
		        var responseChit = new ResponseChit(this.theQuestion.id, option);
                    this.responseChits.push(responseChit);
            } );
    };
    initFromSavedBallot(savedBallotInfo) {
            this.voteAcknowledged = savedBallotInfo.voteAcknowledged;
            this.personalChit = new PersonalChit(this.theQuestion.id, savedBallotInfo.personalChit);
            this.responseChits = [];
            savedBallotInfo.responseChits.forEach( (obj, i) => {
                var responseChit = new ResponseChit(this.theQuestion.id, obj.myResponse, obj);
                this.responseChits.push(responseChit);
            } );
    };
    makeSavedBallotObj() {
        var savedBallotInfo = {};
        savedBallotInfo.voteAcknowledged = this.voteAcknowledged;
        savedBallotInfo.personalChit = this.personalChit;
        savedBallotInfo.responseChits = this.responseChits;
        return savedBallotInfo;
    };
    saveToLocalStorage() {
        setStorageData(this.ballotKey, this.makeSavedBallotObj());
    };
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
        this.errorCountPerSigning = 0;
        var k = Ballot.randomKforModulus(gModulus);
        // Response chits are all signed with the same key
        this.responseChits.forEach( (chit, i) => {
		    var blindedChitText = chit.blindedMessageText(gModulus, k, gPublicKey);
            this.signOneChit(chit, blindedChitText, this.theQuestion.id);
	    });
	    // The me chit is signed with a key that is specific to the the question
	    k = Ballot.randomKforModulus(this.theQuestion.n);
	    var blindedChitText = this.personalChit.blindedMessageText(this.theQuestion.n, k, this.theQuestion.e);
	    this.signOneChit(this.personalChit, blindedChitText, this.theQuestion.id);
	    this.saveToLocalStorage();
    };
    signOneChit(chit, blindedChitText, quid) {
        let url = "ballot/" + quid + chit.getSignatureEndpoint();
        let promise = axios.post(url, { b: blindedChitText });
        promise.then( response => {
            chit.acceptSignedBlindedText(response);
            this.saveToLocalStorage();
         })
        .catch( (error) => {
            console.log(error.response);
            if (!this.errorCountPerSigning) {
                if (error.response) {
                    if  (error.response.status == 403) {   // Forbidden
                        // Why would the CTF refuse to sign? The only likely possibility is that the question closed
                        // just as we loaded. Re-fetch the question list so that we can detect that it closed (if so.)
                        voterApp.checkForNewQuestions();
                    }
                    else if (error.response.status == 401) {  // Unauthorized
                        alert("Either you were logged out, or your permission to vote was revoked.");
                        window.location.href = "/";
                    }
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
    voteConfirmingPrompt() {
        return "voteConfirmingPrompt() not implemented in this Ballot class";
    }
    vote() {
        let prompt = this.voteConfirmingPrompt();
        if (!confirm(prompt)) { return; }
        this.submitVote();
    };
    submitVote() {
        alert("I don't yet know how to submit a vote for a " + this.theQuestion.typeDescription());
    };
    iVoted() {   // MUST OVERRIDE
        return false;
    };
    verifyVote() {
        let url = "ballot/" + this.theQuestion.id + "/verify";
        let promise = axios.get(url);
        promise.then( response => this.processVerificationData(response) )
            .catch( error => LogTrouble(error) );
    }
    processVerificationData(response) {
        console.log("Implement processVerificationData() for " + this.theQuestion.typeDescription());
    };
};
class SingleChoiceBallot extends Ballot {
    constructor(question, ballotKey, savedBallotInfo) {
        super(question, ballotKey, savedBallotInfo);
    };
    initFromSavedBallot(savedBallotInfo) {
        super.initFromSavedBallot(savedBallotInfo);
        this.currentlySelectedResponse = savedBallotInfo.currentlySelectedResponse;
        this.submittedResponse = savedBallotInfo.submittedResponse;
    };
    init() {
        super.init();
    	this.currentlySelectedResponse = null;
        this.submittedResponse = null;  // this is only given a value when vote submitted
    };
    makeSavedBallotObj() {
        var savedBallotInfo = super.makeSavedBallotObj();
        savedBallotInfo.currentlySelectedResponse = this.currentlySelectedResponse;
        savedBallotInfo.submittedResponse = this.submittedResponse;
        return savedBallotInfo;
    };
    hasResponse() {
        return this.currentlySelectedResponse;
    };
    setCurrentlySelectedResponse(opt) {
        if (!(this.submittedResponse || this.theQuestion.closed)) {
            this.currentlySelectedResponse = opt;
            this.saveToLocalStorage();
        }
        // otherwise, if this is already voted, can't change response
        // or, if it's closed, it's fruitless to try to pick a response
    };
    classForOption(text) {
        if (text == this.currentlySelectedResponse) {
            return "fa fa-dot-circle-o";
        }
        else {
            return "fa fa-circle-o";
        }
    };
    voteConfirmingPrompt() {
        return "Do you want to vote " + this.currentlySelectedResponse + " on the question " + this.theQuestion.text;
    };
    submitVote() {
        this.submittedResponse = this.currentlySelectedResponse;
        var responseChit = this.chitForResponse(this.currentlySelectedResponse);
        let payload = { meChit: this.personalChit.getMessageText(),
                    meChitSigned: this.personalChit.signedMessageText,
                    responseChit: responseChit.getMessageText(),
                    responseChitSigned: responseChit.signedMessageText,
                     ranking: 0 };
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
}
class RankedChoiceBallot extends Ballot {
    constructor(question, ballotKey, savedBallotInfo) {
        super(question, ballotKey, savedBallotInfo);
    };
    initFromSavedBallot(savedBallotInfo) {
        super.initFromSavedBallot(savedBallotInfo);
        var j;
    	this.rankedChoices = [];
        for (j = 0; j < savedBallotInfo.rankedChoices.length; ++j) {
            var obj = savedBallotInfo.rankedChoices[j];
            this.rankedChoices.push(this.theQuestion.responseOptionForText(obj.text));
        }
    	this.unrankedChoices = [];
        for (j = 0; j < savedBallotInfo.unrankedChoices.length; ++j) {
            var obj = savedBallotInfo.unrankedChoices[j];
            this.unrankedChoices.push(this.theQuestion.responseOptionForText(obj.text));
        }
        this.acks = savedBallotInfo.acks;
        this.voteSubmissionAttempted = savedBallotInfo.voteSubmissionAttempted;
    };
    init() {
        super.init();
    	this.rankedChoices = [];
    	this.unrankedChoices = [];
    	var j;
    	for (j = 0; j < this.theQuestion.possibleResponses.length; ++j) {
    	    this.unrankedChoices.push(this.theQuestion.possibleResponses[j]);
    	}
        this.acks = {}; // acknowledgements received from server upon receiving each ranking in the vote
        this.voteSubmissionAttempted = false;
    };
    makeSavedBallotObj() {
        var savedBallotInfo = super.makeSavedBallotObj();
        savedBallotInfo.rankedChoices = this.rankedChoices;
        savedBallotInfo.unrankedChoices = this.unrankedChoices;
        savedBallotInfo.acks = this.acks;
        savedBallotInfo.voteSubmissionAttempted = this.voteSubmissionAttempted;
        return savedBallotInfo;
    };
    hasResponse() {
        return this.rankedChoices.length;
    };
    voteConfirmingPrompt() {
        var str = "Do you want to vote for this ranking:\n";
        var j;
        for (j = 0; j < this.rankedChoices.length; ++j) {
            str = str + (j+1) + ". " +  this.rankedChoices[j].getText() + "\n";
        }
        str = str + " on the question " + this.theQuestion.text;
        return str;
    };
    /* TODO: submit entire ranking in one message
        This is a messy source of potential bugs-- having to keep track of which messages have been
        acknowledged, and answering the question "did I successfully submit the vote or not?"
        Try changing the endpoint to accept an Array
    */
    submitVote() {
        var j;
        this.voteSubmissionAttempted = true;
        for (j = 0; j < this.rankedChoices.length; ++j) {
            var choice = this.rankedChoices[j];
            var responseChit = this.chitForResponse(choice.getText());
            let payload = { meChit: this.personalChit.getMessageText(),
                    meChitSigned: this.personalChit.signedMessageText,
                    responseChit: responseChit.getMessageText(),
                    responseChitSigned: responseChit.signedMessageText,
                     ranking: j };
            let url = "ballot/" + this.theQuestion.id + "/vote";
            let promise = axios.post(url, payload);
            promise.then( response => this.processVoteResponse(response) )
                .catch(error => this.processVoteError(error));
        }
    };
    processVoteResponse(response) {
        this.acks[response.data] = response.status;
        if (this.allVoteRanksAcknowledged()) {
            this.voteAcknowledged = true;
            this.saveToLocalStorage();
            this.verifyVote();
        }
    };
    iVoted() {
        return this.voteSubmissionAttempted;
    };
    canEditResponses() {
        return !(this.iVoted() || this.theQuestion.closed);
    };
    allVoteRanksAcknowledged() {
        return (Object.keys(this.acks).length == this.rankedChoices.length);
    };
    processVerificationData(response) {
        var responseChits = [];
        var responsesFound = [];
        var j;
        for (j = 0; j < this.rankedChoices.length; ++j) {
            var choice = this.rankedChoices[j];
            var responseChit = this.chitForResponse(choice.getText());
            responseChits.push(responseChit);
            responsesFound.push(false);
        }
        this.verificationMessage = '';
        var report = response.data;
        console.log(report);
        var i;
        for (i = 0; i < report.length; ++i) {
	        var record = report[i];
		    var questionID = record.question.id;
            if (questionID != this.theQuestion.id) { continue; }
            var voterID = record.voterChitNumber;
            if (!this.personalChit.matchesID(voterID)) { continue; }
            if (!this.iVoted()) {
		            this.verificationMessage = "The CTF claims I voted, and I don't remember voting!";
		            LogTrouble(this.verificationMessage);
                    return;
            }
            else {
		            var responseID = record.responseChitNumber;
		            var ranking = record.ranking;
		            if (ranking >= this.rankedChoices.length) {
		                this.verificationMessage = "The CTF reports more responses on this question than I submitted!";
		                LogTrouble(this.verificationMessage);
		            }
		            if (!responseChits[ranking].matchesID(responseID)) {
			            this.verificationMessage = "The CTF reports an invalid number for my response!";
    		            LogTrouble(this.verificationMessage);
			            return;
		            }
		            var responseInReport = record['response'];
		            if (responseInReport == this.rankedChoices[ranking]) {
			            responsesFound[ranking] = true;
		            }
                    else {
                        this.verificationMessage = "The CTF claims I voted differently than I remember!";
    		            LogTrouble(this.verificationMessage);
                        return;
                    }
            }
	    }
	    if (this.voteSubmissionAttempted) {
    	    if (responsesFound.every((item) => item)) {
    	        this.verificationMessage = 'Verified!';
	        }
	        else {
	            this.verificationMessage = 'Not all items in ranking verified.';
                LogTrouble(this.verificationMessage);
	        }
	    }
	};
    addChoice(optionText) {
        var j;
        for (j = 0; j < this.unrankedChoices.length; ++j) {
            if (this.unrankedChoices[j].text == optionText) {
                this.rankedChoices.push(this.unrankedChoices[j]);
                this.unrankedChoices.splice(j, 1);
                return;
            }
        }
    };
    insertChoiceAt(optionText, index) {
        --index;
        if (index < 0) { index = 0; }
        if (index > this.rankedChoices.length) { index = this.rankedChoices.length; }
        var j;
        for (j = 0; j < this.unrankedChoices.length; ++j) {
            if (this.unrankedChoices[j].text == optionText) {
                this.rankedChoices.splice(index, 0, this.unrankedChoices[j]);
                this.unrankedChoices.splice(j, 1);
                return;
            }
        }
    };
    moveRankingUp(optionText) {
        var j;
        for (j = 1; j < this.rankedChoices.length; ++j) {
            if (this.rankedChoices[j].text == optionText) {
                var option = this.rankedChoices[j];
                this.rankedChoices.splice(j, 1);
                this.rankedChoices.splice(j-1, 0, option);
                return;
            }
        }
    };
    moveRankingDown(optionText) {
        var j;
        for (j = 0; (j+1) < this.rankedChoices.length; ++j) {
            if (this.rankedChoices[j].text == optionText) {
                var option = this.rankedChoices[j];
                this.rankedChoices.splice(j, 1);
                this.rankedChoices.splice(j+1, 0, option);
                return;
            }
        }
    };
    deleteRankedChoice(optionText) {
        var option = null;
        var optionIndex = 0;
        var j;
        for (j = 0; j < this.rankedChoices.length; ++j) {
            if (this.rankedChoices[j].text == optionText) {
                option = this.rankedChoices[j];
                optionIndex = j;
            }
        }
        if (!option) { return; }
        this.rankedChoices.splice(optionIndex, 1);
        this.unrankedChoices.push(option);
    };
    insertChoiceBefore(insertedOptionText, displacedOptionText) {
        if (insertedOptionText == displacedOptionText) { return; }
        var displacedOption = null;
        var displacedOptionIndex = 0;
        var insertedOption = null;
        var insertedOptionIndex = 0;  // its orignal index (before the reordering)
        var j;
        for (j = 0; j < this.rankedChoices.length; ++j) {
            if (this.rankedChoices[j].text == displacedOptionText) {
                displacedOption = this.rankedChoices[j];
                displacedOptionIndex = j;
            }
        }
        for (j = 0; j < this.rankedChoices.length; ++j) {
            if (this.rankedChoices[j].text == insertedOptionText) {
                insertedOption = this.rankedChoices[j];
                insertedOptionIndex = j;
            }
        }
        if (insertedOption) {  // we are rearranging within the list of ranked choices
            this.rankedChoices.splice(displacedOptionIndex, 0, insertedOption);
            this.rankedChoices.splice(
                (displacedOptionIndex<insertedOptionIndex)?(insertedOptionIndex+1):insertedOptionIndex , 1);
        }
        else {  // inserted an unranked in at a specific place
            for (j = 0; j < this.unrankedChoices.length; ++j) {
                if (this.unrankedChoices[j].text == insertedOptionText) {
                    insertedOption = this.unrankedChoices[j];
                    insertedOptionIndex = j;
                }
            }
            if (insertedOption) {
                this.rankedChoices.splice(displacedOptionIndex, 0, insertedOption);
                this.unrankedChoices.splice(insertedOptionIndex, 1);
            }
        }
    }
}
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
        },
        hasUnshownOldQuestions: function() {
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
                if (!found) { return true; }
            }
            return false;
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
            handleQueryError(error);
        },
        fetchCTFKeys: function() {
            if (gModulus && gPublicKey) {
                this.$data.isKeyInfoKnown = true;
                this.getQuestions();
            }
            else {
                let url = "ballots/keys/";
                let promise = axios.get(url);
                promise.then(response => this.processKeys(response), error => this.dealWithError(error));
            }
        },
        processKeys: function(response) {
            gPublicKey = bigInt(response.data.public);
            gModulus = bigInt(response.data.modulus);
            this.$data.isKeyInfoKnown = true;
            this.getQuestions();
        },
        getQuestions: function() {
            if (gPageLoadQuestions) {  // even an empty array is truthy
                this.processOpenQuestions(gPageLoadQuestions);
            }
            else {
                this.checkForNewQuestions();
            }
        },
        checkForNewQuestions: function() {  // from voter's POV: questions that they can vote on now
            let url = "ballots/";
            let aPromise = axios.get(url);
            aPromise.then(response => this.processOpenQuestions(response.data),
                error => handleQueryError(error));
        },
        startShowingOldQuestions: function() {
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
                    var errorCount = 0;
                    let url = "/questions/" + id;
                    let promise = axios.get(url);
                    promise.then( response => this.processOldQuestion(response),
                        error => {
                            if (!(error.response && error.response.status == 404)) {
                                if (!errorCount) {
                                    handleQueryError(error);
                                }
                                ++errorCount;
                            }
                        });
                }
            }
        },
        processOldQuestion: function(response) {
            let obj = response.data;
            var q = new VotableQuestion(obj);
            this.$data.votableQuestions.push(q);
        },
        processOpenQuestions: function(questions) {
            var i;
            var j;
            for (j = 0; j < this.$data.votableQuestions.length; ++j) {
                var found = false;
                for (i = 0; i < questions.length; ++i) {
                    if (questions[i].id === this.$data.votableQuestions[j].id) {
                        found = true; break;
                    }
                }
                if (!found) {
                    this.$data.votableQuestions[j].closed = true;
                }
            }
            for (i = 0; i < questions.length; ++i) {
                var obj = questions[i];
                var found = false;
                for (j = 0; j < this.$data.votableQuestions.length; ++j) {
                    if (obj.id ===  this.$data.votableQuestions[j].id) {
                        found = true; break;
                    }
                }
                if (!found) {
                    /* Only process newly opened questions and instantiate instances of VotableQuestion after
                        we know the CTF's key info! A VotableQuestion's Ballot's
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
        },
        questionForId: function(id) {
            var j;
            for (j = 0; j < this.$data.votableQuestions.length; ++j) {
                 if (id ==  this.$data.votableQuestions[j].id) {
                     return this.$data.votableQuestions[j];
                 }
            }
            return null;
        },
        // Methods for handling choosing and re-ordering ranked choice:
        drag: function(ev) {
            ev.dataTransfer.setData("text", ev.target.id);
        },
        allowDrop: function(ev) {
             ev.preventDefault();
        },
        drop: function(ev) {
            ev.preventDefault();
            let itemToMoveId = ev.dataTransfer.getData("text");
            let fields = itemToMoveId.split('_', 2);
            let q = this.questionForId(fields[0]);
            q.ballot.addChoice(fields[1]);   // only works for ranking
        },
        dropInsert: function(ev) {
            ev.preventDefault();
            let itemToMoveId = ev.dataTransfer.getData("text");
            var fields = itemToMoveId.split('_', 2);
            let quid1 = fields[0];
            let insertedOption = fields[1];
            let q = this.questionForId(quid1);
            let targetId = ev.target.id;
            fields = targetId.split('_', 2);
            let quid2 = fields[0];
            let displacedOption = fields[1];
            if (quid1 != quid2) { // Can't drag a response onto a different question!!!
                return;
            }
            q.ballot.insertChoiceBefore(insertedOption, displacedOption);
        },
        dropDelete: function(ev) {
            ev.preventDefault();
            let itemToMoveId = ev.dataTransfer.getData("text");
            this.deleteWithId(itemToMoveId);
        },
        deleteWithId: function(itemToMoveId) {
            let fields = itemToMoveId.split('_', 2);
            let quid1 = fields[0];
            let removedOption = fields[1];
            let q = this.questionForId(quid1);
            q.ballot.deleteRankedChoice(removedOption);
        },
        keyOnUnselected: function(ev) {
            let key = ev.key;
            let targetId = ev.target.id;
            let fields = targetId.split('_', 2);
            let quid1 = fields[0];
            let optionText = fields[1];
            let q = this.questionForId(quid1);
            if (key == "ArrowLeft") {
                q.ballot.addChoice(optionText);
            }
            else if (!isNaN(key)) {
                q.ballot.insertChoiceAt(optionText, key);
            }
        },
        keyOnSelected: function(ev) {
            let key = ev.key;
            let targetId = ev.target.id;
            let fields = targetId.split('_', 2);
            let quid1 = fields[0];
            let optionText = fields[1];
            let q = this.questionForId(quid1);
            if (key == "Backspace") {
                this.deleteWithId(targetId);
            }
            else if (key == "ArrowUp") {
                q.ballot.moveRankingUp(optionText);
            }
            else if (key == "ArrowDown") {
                q.ballot.moveRankingDown(optionText);
            }
            else if (key == "ArrowRight") {
                this.deleteWithId(targetId);
            }
        },
        clickUnselected: function(ev) {
            let itemToMoveId = ev.target.id;
            let fields = itemToMoveId.split('_', 2);
            let q = this.questionForId(fields[0]);
            q.ballot.addChoice(fields[1]);   // only works for ranking
        }
    },
});

