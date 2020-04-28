// global variables
gPublicKey = null;
gModulus = null;

function LogTrouble(message) {
    console.log(message);
}

function setStorageString(key, cvalue) {
      window.localStorage.setItem(key, encodeURIComponent(cvalue));
}
function setStorageData(key, data) {
    setStorageString(key, JSON.stringify(data));
}
function getStorageString(key) {
      var value = window.localStorage.getItem(key);
      value = decodeURIComponent(value);
      console.log("Found in storage for key: " + key);
      console.log(key);
      return value;
}
function getStorageData(key) {
      let str = getStorageString(key);
      if (str) {
        try {
          let obj = JSON.parse(str);
          console.log("parsed from storage: ");
          console.log(obj);
          return obj;
        }
        catch (err) {
          console.log("problem with json encoding?: " + str + " " + err);
        }
      }
      return null;
}

// See https://www.npmjs.com/package/big-integer for BigInteger class documentation.
/* Equivalent to the Java code: new BigInteger(s.getBytes(UTF_8))   */
function stringToBigInteger(s) {
        var b = stringToUtf8ByteArray(s);
        bi = bigInt();
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
class ResponseOption {
    // TODO: enforce length limit of 80 characters on text
    constructor(text) {
        this.text = text;
    };
    getText() {
        return this.text;
    };
    toString() {
        return this.text;
    };
    getUniqueLabel() {
        return `${ this.questionID } ${ this.getText() }`;
    };
}
class Question {
    constructor(text="") {
        this.text = text;
        this.possibleResponses = [];
    };
    addResponseOption(opt) {
        opt.questionID = this.id;
        this.possibleResponses.push(opt);
    }
    addResponseOptionsFrom(obj) {
        var j;
        for (j = 0; j < obj.possibleResponses.length; ++j) {
             var ro = obj.possibleResponses[j];
             var r = new ResponseOption(ro.text);
             this.addResponseOption(r);
         }
    }
}
class AdministratableQuestion extends Question {
    constructor(obj=null) {
        super();
        this.setOriginalData(obj);
        this.results = {};
    }
    setOriginalData(obj) {
        this.original = obj;
        if (obj == null) {
            this.status = 'new';
        }
        else {
            this.id = obj.id;
            this.text = obj.text;
            this.status = obj.status;
            this.addResponseOptionsFrom(obj);
            if (this.status === 'polling' || this.status === 'closed' ) {
                this.reportVotes();
            }
        }
    }
    reportVotes() {
        let url = "ballot/" + this.id + "/verify";
        let promise = axios.get(url);
        promise.then( response => this.processVerificationData(response) )
            .catch( error => console.log(error) );
    }
    processVerificationData(response) {
        var report = response.data;
        this.results = {};
        var i;
        this.possibleResponses.forEach( (option, i) => {
            this.results[option.getText()] = 0;
        });
        for (i = 0; i < report.length; ++i) {
	        var record = report[i];
		    var questionID = record.question.id;
            if (questionID != this.id) { continue; }
            if (this.results[record.response]) {
                this.results[record.response] += 1;
            }
            else {   // Covers the case that a response was not in the question's list of possible responses...
                this.results[record.response] = 1;  // which shouldn't happen, as things work now, but just in case...
            }
        }
    }
    canBeEdited() {
        return (this.original == null || this.original.editable);
    }
    canBeSaved() {
        if (this.canBeEdited() && this.text.length > 0 && this.possibleResponses.length > 1) {
            return true;
        }
        else {
            return false;
        }
    }
    saveMe() {
        var promise;
        if (this.original) {
            let url = "questions/" + this.original.id;
            promise = axios.patch(url, this);
        }
        else {
            let url = "questions";
            promise = axios.post(url, this);
        }
        promise.then(function (response) {
            voterApp.fetchQuestions();
        })
        .catch(function (error) {
            console.log(error);
        });
    }
    canBePosted() {
        if (this.original == null) {
            return false;
        }
        return this.original.postable;
    }
    postMe() {
        if (!this.canBePosted()) { return; }
        let prompt = "Are you sure you want to start polling on this question now: " + this.text;
        let response = confirm(prompt);
        if (response) {
            let url = "questions/" + this.original.id + "/post";
            let promise = axios.patch(url, this);
            promise.then(function (response) {
                voterApp.fetchQuestions();
            })
            .catch(function (error) {
                console.log(error);
            });
        }
    }
}
class VotableQuestion extends Question {
    constructor(obj) {
        super();
        this.id = obj.id;
        this.addResponseOptionsFrom(obj);
        this.text = obj.text;
        this.closed = false;
        let ballotKey = "ballot." + getUser() + "." + this.id;
        let savedBallotInfo = getStorageData(ballotKey);
        this.ballot = new Ballot(this, ballotKey, savedBallotInfo);
    }
    canVote() {
        return (!this.closed && this.ballot.currentlySelectedResponse
                && (!this.ballot.submittedResponse || !this.ballot.voteAcknowledged));
    }
}
class Voter {
    constructor(obj) {
        this.currentEmail = obj.currentEmail;
        this.name = obj.name;
        this.username = obj.username;
        this.allowedToVote = obj.allowedToVote;
        this.admin = obj.admin;
    };
    grantVoting() {
        this.allowedToVote = true;
        this.savePrivChanges();
    }
    revokeVoting() {
        this.allowedToVote = false;
        this.savePrivChanges();
    }
    grantAdmin() {
        this.admin = true;
        this.savePrivChanges();
    }
    revokeAdmin() {
        let prompt = "Are you sure you want to revoke admin privileges from " + this.name + "?";
        let response = confirm(prompt);
        if (response) {
            this.admin = false;
            this.savePrivChanges();
        }
    }
    savePrivChanges() {
        let url = "voters/" + this.username + "/priv";
        let promise = axios.patch(url, this);
        promise.then(function (response) {
            voterApp.fetchUsers();
        })
        .catch(function (error) {
            console.log(error);
        });
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
        var i;
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
	    var isSigned = false;
        if (savedBallotInfo) {
            var isSigned = true;
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
        if (!isSigned) {
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
    // TODO: intermittent bug somewhere in this process? Some bigint function given a null?
    static randomKforModulus(n) {
        var k;
        do {
            k = Random.randomBig(1, n);
            console.log("Trying a k: " + k.toString(10));
        } while (bigInt.gcd(k,n).compare(bigInt.one) != 0);
        return k;
    };
    getSignedForUser() {
	    this.n = gModulus;
        this.e = gPublicKey;
        this.k = Ballot.randomKforModulus(this.n);
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
        .catch(function (error) {
            console.log(error);
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
        this.submittedResponse = this.currentlySelectedResponse;
        var responseChit = this.chitForResponse(this.currentlySelectedResponse);
        let payload = { meChit: this.personalChit.getMessageText(),
                    meChitSigned: this.personalChit.signedMessageText,
                    responseChit: responseChit.getMessageText(),
                    responseChitSigned: responseChit.signedMessageText };
        let url = "ballot/" + this.theQuestion.id + "/vote";
        let promise = axios.post(url, payload);
        promise.then( response => this.processVoteResponse(response) )
            .catch(function (error) {
                console.log(error);
            });
    };
    processVoteResponse() {
        this.voteAcknowledged = true;
        this.saveToLocalStorage();
    };
    iVoted() {
        return (this.submittedResponse != null);
    };
    verifyVote() {
        let url = "ballot/" + this.theQuestion.id + "/verify";
        let promise = axios.get(url);
        promise.then( response => this.processVerificationData(response) )
            .catch( error => console.log(error) );
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
                    return false;
            }
            else {
		            var responseID = record.responseChitNumber;
		            if (!responseChit.matchesID(responseID)) {
			            this.verificationMessage = "The CTF reports an invalid number for my response!";
			            return false;
		            }
		            var responseInReport = record['response'];
		            if (responseInReport == this.submittedResponse) {
			            responseFound = true;
		            }
                    else {
                        this.verificationMessage = "The CTF claims I voted differently than I remember!";
                        return false;
                    }
            }
	    }
	    if (this.iVoted() == responseFound) {
	        this.verificationMessage = 'Verified!';
	    }
	    else {
	        this.verificationMessage = "Verification of vote failed.";
	    }
    };
};
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
var voterApp = new Vue({
    el: '#voterapp',
    data: {
        visible: true,
        username: null,
        name: null,
        email: null,
        allowedToVote: false,
        admin: false,
        allquestions: [],
        showingQuestions: false,
        allusers: [],
        errorText: '',
        votableQuestions: [],
    },
    mounted() {
        this.$data.username = getUser();
        let url = "voters/" + this.$data.username;
        let aPromise = axios.get(url);
        aPromise.then(response => this.processUserInfo(response), error => this.dealWithError(error));
    },
    methods: {
        processUserInfo: function(response) {
            this.$data.name= response.data.name;
            this.$data.email = response.data.email;
            this.$data.allowedToVote = response.data.allowedToVote;
            this.$data.admin = response.data.admin;
            if (this.$data.allowedToVote) {
                this.fetchCTFKeys();
                this.checkForNewQuestions();
            }
        },
        processQuestionList: function(response) {
            console.log(response.data);
            this.$data.showingQuestions = true;
            var i;
            this.$data.allquestions = [];
            for (i = 0; i < response.data.length; ++i) {
                var obj = response.data[i];
                var q = new AdministratableQuestion(obj);
                this.$data.allquestions.push(q);
            }
        },
        dealWithError: function(error) {
            this.$data.errorText = "Error: " + error;
        },
        fetchQuestions: function() {  // Complete list of questions from admin's point of view
            let url = "questions/";
            let aPromise = axios.get(url);
            aPromise.then(response => this.processQuestionList(response), error => this.dealWithError(error));
        },
        newQuestion: function() {
            let item = new AdministratableQuestion();
            item.addResponseOption(new ResponseOption("yes"));
            item.addResponseOption(new ResponseOption("no"));
            item.addResponseOption(new ResponseOption("abstain"));
            this.$data.allquestions.push(item);
        },
        fetchUsers: function() {
            let url = "voters/";
            let promise = axios.get(url);
            promise.then(response => this.processUserList(response), error => this.dealWithError(error));
        },
        processUserList: function(response) {
            console.log(response.data);
            this.$data.allusers = [];
            var i;
            for (i = 0; i < response.data.length; ++i) {
                var obj = response.data[i];
                v = new Voter(obj);
                this.$data.allusers.push(v);
            }
        },
        fetchCTFKeys: function() {
            let url = "ballots/keys/";
            let promise = axios.get(url);
            promise.then(response => this.processKeys(response), error => this.dealWithError(error));
        },
        processKeys: function(response) {
            gPublicKey = response.data.public;
            gModulus = response.data.modulus;
        },
        checkForNewQuestions: function() {  // from voter's POV: questions that they can vote on now
            let url = "ballots/";
            let aPromise = axios.get(url);
            aPromise.then(response => this.processOpenQuestions(response), error => this.dealWithError(error));
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
                    var q = new VotableQuestion(obj);
                    this.$data.votableQuestions.push(q);
                }
            }
        }
    },
});


// Below: A small snippet from the Google closure-libary file crypt.js
//   Modified: namespace changed 4/26/2020
// See https://github.com/google/closure-library for complete library
// Copyright notice for the stringToUtf8ByteArray function:
// Copyright 2008 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * Converts a JS string to a UTF-8 "byte" array.
 * @param {string} str 16-bit unicode string.
 * @return {!Array<number>} UTF-8 byte array.
 */
stringToUtf8ByteArray = function(str) {
    // TODO(user): Use native implementations if/when available
    var out = [], p = 0;
    for (var i = 0; i < str.length; i++) {
	var c = str.charCodeAt(i);
	if (c < 128) {
	    out[p++] = c;
	} else if (c < 2048) {
	    out[p++] = (c >> 6) | 192;
	    out[p++] = (c & 63) | 128;
	} else if (
		   ((c & 0xFC00) == 0xD800) && (i + 1) < str.length &&
		   ((str.charCodeAt(i + 1) & 0xFC00) == 0xDC00)) {
	    // Surrogate Pair
	    c = 0x10000 + ((c & 0x03FF) << 10) + (str.charCodeAt(++i) & 0x03FF);
	    out[p++] = (c >> 18) | 240;
	    out[p++] = ((c >> 12) & 63) | 128;
	    out[p++] = ((c >> 6) & 63) | 128;
	    out[p++] = (c & 63) | 128;
	} else {
	    out[p++] = (c >> 12) | 224;
	    out[p++] = ((c >> 6) & 63) | 128;
	    out[p++] = (c & 63) | 128;
	}
    }
    return out;
};
