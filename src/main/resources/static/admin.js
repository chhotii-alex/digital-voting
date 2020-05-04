class AdministratableQuestion extends Question {
    constructor(obj=null) {
        super();
        this.shouldShowResultsDetail = false;
        this.setOriginalData(obj);
        this.results = {};
        this.total = 0;
    }
    setOriginalData(obj) {
        this.original = obj;
        if (obj == null) {
            this.status = 'new';
        }
        else {
            this.id = obj.id;
            this.updateFrom(true, obj);
        }
    }
    updateFrom(doingInitialization, obj) {
        // if this is an update of a question we're already listing, Do not over-write changes made in this client
        if (doingInitialization || !this.hasDifferencesFromOriginal()) {
            this.text = obj.text;
            this.addResponseOptionsFrom(obj);
        }
        this.status = obj.status;
        this.postable = obj.postable;
        this.editable = obj.editable;
        this.closable = obj.closable;
        if (this.status === 'polling' || this.status === 'closed' ) {
            this.reportVotes();
            if (this.status === 'closed')  {
                this.shouldShowResultsDetail = true;
            }
        }
    }
    addOption() {
        var r = new ResponseOption("");
        this.addResponseOption(r);
    }
    deleteOption(optionText) {
        if (this.possibleResponses.length < 3) {
            return;
        }
        if (this.status != "new") { return; }
        var i;
        for (i = 0; i < this.possibleResponses.length; ++i) {
            if (this.possibleResponses[i].getText() == optionText) {
                this.possibleResponses.splice(i, 1);
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
        this.total = 0;
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
            ++(this.total);
        }
    }
    canDeleteOption() {
        return (this.possibleResponses.length > 2);
    }
    canBeEdited() {
        return (this.original == null || this.editable);
    }
    hasUnsavedChanges() {
        if (!this.original) { return true; }
        if (this.hasDifferencesFromOriginal()) {return true;}
        return false;
    }
    hasDifferencesFromOriginal() {
        if (this.original.text != this.text) { return true; }
        if (this.possibleResponses.length != this.original.possibleResponses.length) { return true; }
        var i;
        for (i = 0; i < this.possibleResponses.length; ++i) {
            if (this.possibleResponses[i].getText() != this.original.possibleResponses[i].text)  {
                return true;
            }
        }
        return false;
    }
    trimSpaces() {
        var i;
        for (i = 0; i < this.possibleResponses.length; ++i) {
            this.possibleResponses[i].trimSpaces();
        }
    }
    canBeSaved() {
        if (!this.canBeEdited()) { return false; }
        if (this.text.length < 1) { return false; }
        if (this.possibleResponses.length < 2) { return false; }
        var i;
        var counts = {};
        for (i = 0; i < this.possibleResponses.length; ++i) {
            if (!this.possibleResponses[i].getText()) {
                return false;
            }
            if (counts[this.possibleResponses[i].getText()]) { return false; } // duplicate text found
            counts[this.possibleResponses[i].getText()] = 1;
        }
        if (!this.hasUnsavedChanges()) return false;
        return true;
    }
    saveMe() {
        var promise;
        this.trimSpaces();
        if (this.original) {
            // actually send the PATCH
            let url = "questions/" + this.original.id;
            promise = axios.patch(url, this);
        }
        else {
            let url = "questions";
            promise = axios.post(url, this);
        }
        promise.then( (response) => {  // note: use a lambda! we can refer to 'this' in a lambda b/c lexical binding
            this.original = response.data; this.updateFrom(true, response.data);  // re-initialize from what's now in database
            if (this.id) {
                if (this.id != response.data.id) {
                    console.log("??!?!??!?!");
                }
            }
            else {
                this.id = response.data.id;
                adminApp.setQuestionForID(response.data.id, this);
            }
            adminApp.fetchQuestions();
        })
        .catch(function (error) {
            console.log(error);
        });
    }
    canBePosted() {
        if (this.original == null) {  // If it has never been saved to the database, don't post
            return false;
        }
        return this.postable;
    }
    postMe() {
        if (!this.canBePosted()) { return; }
        let prompt = "Are you sure you want to start polling on this question now: " + this.text;
        let response = confirm(prompt);
        if (response) {
            this.post();
        }
    }
    post() {
            let url = "questions/" + this.original.id + "/post";
            let promise = axios.patch(url, this);
            promise.then(function (response) {
                adminApp.fetchQuestions();
            })
            .catch(function (error) {
                console.log(error);
            });

    }
    canBeClosed() {
        if (this.original == null) {  // if it's not in the database, it has never been up for polling
            return false;
        }
        return this.closable;
    }
    closeMe() {
        if (!this.canBeClosed()) { return; }
        let prompt = "Are you sure you want to END polling on this question now: " + this.text;
        let response = confirm(prompt);
        if (response) {
            let url = "questions/" + this.original.id + "/close";
            let promise = axios.patch(url, this);
            promise.then(function (response) {
                adminApp.fetchQuestions();
            })
            .catch(function (error) {
                console.log(error);
            });
        }
    }
    canBeDeleted() {
        return (this.status == 'new');
    }
    deleteMe() {
        if (!this.canBeDeleted()) { return; }
        let prompt = "Delete question?   " + this.text;
        let response = confirm(prompt);
            adminApp.removeQuestion(this);
        if (response) {
            if (this.original) {
                let url = "questions/" + this.original.id + "/delete";
                let promise = axios.delete(url, this);
                promise.then(function (response) {
                    adminApp.fetchQuestions();
                })
                .catch(function (error) {
                    console.log(error);
                });
           }
        }
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
            adminApp.fetchUsers();
        })
        .catch(function (error) {
            console.log(error);
        });
    }
}
var adminApp = new Vue({
    el: '#adminapp',
    data: {
        visible: true,
        username: '',
        name: '',
        email: '',
        allowedToVote: false,
        admin: false,
        allquestions: [],
        questionsById: {},
        showingQuestions: false,
        showOldQuestions: false,
        allusers: [],
        errorText: '',
        updateTimerToken: '',
    },
    mounted() {
        this.$data.username = getUser();
        let url = "voters/" + this.$data.username;
        let aPromise = axios.get(url);
        aPromise.then(response => this.processUserInfo(response), error => this.dealWithError(error));
    },
    watch: {
        showOldQuestions: function(val) {
            this.fetchQuestions();  // re-fetch when the filter changes
        },
    },
    methods: {
        processUserInfo: function(response) {
            this.$data.name= response.data.name;
            this.$data.email = response.data.email;
            this.$data.allowedToVote = response.data.allowedToVote;
            this.$data.admin = response.data.admin;
        },
        setQuestionForID(id, q) {
            this.$data.questionsById[id] = q;
        },
        processQuestionList: function(response) {
            var now = Date.now();
            this.$data.showingQuestions = true;
            var i;
            var newArray = [];
            for (i = 0; i < response.data.length; ++i) {
                var obj = response.data[i];
                if (!this.$data.showOldQuestions) {
                    if (obj.status == "closed") {
                        var closedWhen = Date.parse(obj.closedWhen);
                        var diffInMillis = now - closedWhen;
                        if (diffInMillis > 1000*60*60*24) {
                            continue;
                        }
                    }
                }
                if (this.$data.questionsById[obj.id]) {
                    this.$data.questionsById[obj.id].updateFrom(false, obj);
                    newArray.push(this.$data.questionsById[obj.id]);
                }
                else {
                    var q = new AdministratableQuestion(obj);
                    newArray.push(q);
                    this.setQuestionForID(obj.id, q);
                }
            }
            for (i = 0; i < this.$data.allquestions.length; ++i) {
                var q = this.$data.allquestions[i];
                if (q.original == null) {
                    newArray.push(q);
                }
            }
            this.$data.allquestions = newArray;
        },
        dealWithError: function(error) {
            this.$data.errorText = "Error: " + error;
        },
        fetchQuestions: function() {  // Complete list of questions from admin's point of view
            let url = "questions/";
            let aPromise = axios.get(url);
            aPromise.then(response => this.processQuestionList(response), error => this.dealWithError(error));
        },
        startRepeatedlyUpdatingQuestions: function() {
            this.fetchQuestions();
            if (this.$data.updateTimerToken) {
                clearInterval(this.$data.updateTimerToken);
                this.$data.updateTimerToken = '';
            }
            this.$data.updateTimerToken = setInterval( () => this.fetchQuestions(), 30*1000);
        },
        newQuestion: function(text=null) {
            let item = new AdministratableQuestion();
            if ((typeof text) == "string" ) {
                item.text = text;
            }
            item.addResponseOption(new ResponseOption("yes"));
            item.addResponseOption(new ResponseOption("no"));
            item.addResponseOption(new ResponseOption("abstain"));
            this.$data.allquestions.push(item);
            return item;
        },
        removeQuestion: function(q) {
            var index = this.$data.allquestions.findIndex( (element) => element == q );
            this.$data.allquestions.splice(index, 1);
        },
        deleteOption: function(sender) {
            let fields = sender.target.id.split(":");
            var i;
            for (i = 0; i < this.$data.allquestions.length; ++i) {
                let q = this.$data.allquestions[i];
                if (q.id == fields[0]) {
                    q.deleteOption(fields[1]);
                    break;
                }
            }
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
    },
});
