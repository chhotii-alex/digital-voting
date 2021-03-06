class AdministratableQuestion extends Question {
    constructor(obj=null) {
        super();
        this.shouldShowResultsDetail = false;
        this.setOriginalData(obj);
        this.results = {};
        this.numVotersResponded = 0;
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
            this.type = obj.type;
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
        if (this.isSingleChoice()) {
            this.results = {};
            this.numVotersResponded = 0;
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
                ++(this.numVotersResponded);
            }
        }
        else if (this.isRankedChoice()) {
            tabulateRankedChoiceResults(this, report);
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
        if (this.original.type != this.type) { return true; }
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
        userActive();
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
        userActive();
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
            handleQueryError(error);
        });
    }
    canBePosted() {
        if (this.original == null) {  // If it has never been saved to the database, don't post
            return false;
        }
        return this.postable;
    }
    postMe() {
        userActive();
        if (!this.canBePosted()) { return; }
        let prompt = "Are you sure you want to start polling on this question now: " + this.text;
        let response = confirm(prompt);
        userActive();
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
                handleQueryError(error);
            });

    }
    canBeClosed() {
        if (this.original == null) {  // if it's not in the database, it has never been up for polling
            return false;
        }
        return this.closable;
    }
    closeMe() {
        userActive();
        if (!this.canBeClosed()) { return; }
        let prompt = "Are you sure you want to END polling on this question now: " + this.text;
        let response = confirm(prompt);
        userActive();
        if (response) {
            let url = "questions/" + this.original.id + "/close";
            let promise = axios.patch(url, this);
            promise.then(function (response) {
                adminApp.fetchQuestions();
            })
            .catch(function (error) {
                handleQueryError(error);
            });
        }
    }
    canBeDeleted() {
        return (this.status == 'new');
    }
    deleteMe() {
        userActive();
        if (!this.canBeDeleted()) { return; }
        let prompt = "Delete question?   " + this.text;
        let response = confirm(prompt);
        userActive();
        if (response) {
            if (this.original) {
                let url = "questions/" + this.original.id + "/delete";
                let promise = axios.delete(url, this);
                promise.then( (response) => {
                    adminApp.removeQuestion(this);
                    adminApp.fetchQuestions();
                })
                .catch(function (error) {
                    handleQueryError(error);
                });
           }
           else {
                adminApp.removeQuestion(this);
           }
        }
    }
    isClosed() {
        return (this.status == 'closed');
    }
    isPolling() {
        return (this.status == 'polling');
    }
    isNew() {
        return (this.status == 'new');
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
        questionRefreshTime: '',
        showOldQuestions: false,
        areAnyQuestionsOld: false,
        errorText: '',
        updateTimerToken: '',
    },
    mounted() {
        userActive();
        this.$data.username = getUser();
        let url = "voters/" + this.$data.username;
        let aPromise = axios.get(url);
        aPromise.then(response => this.processUserInfo(response), error => handleQueryError(error));
        this.startRepeatedlyUpdatingQuestions();
    },
    watch: {
        showOldQuestions: function(val) {
            userActive();
            this.fetchQuestions();  // re-fetch when the filter changes
        },
    },
    computed: {
        closedQuestions: function() {
            return this.$data.allquestions.filter( q => q.isClosed());
        },
        pollingQuestions: function() {
            return this.$data.allquestions.filter( q => q.isPolling());
        },
        newQuestions: function() {
            return this.$data.allquestions.filter( q => q.isNew());
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
            this.$data.questionRefreshTime = new Date();
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
                            this.$data.areAnyQuestionsOld = true;
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
        fetchQuestions: function() {  // Complete list of questions from admin's point of view
            let url = "questions/";
            let aPromise = axios.get(url);
            aPromise.then(response => this.processQuestionList(response), error => handleQueryError(error));
        },
        startRepeatedlyUpdatingQuestions: function() {
            userActive();
            this.fetchQuestions();
            if (this.$data.updateTimerToken) {
                clearInterval(this.$data.updateTimerToken);
                this.$data.updateTimerToken = '';
            }
            this.$data.updateTimerToken = setInterval( () => this.fetchQuestions(), 30*1000);
        },
        newQuestionSingle: function(text=null) {
            userActive();
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
        newQuestionRanked: function(text=null) {
            userActive();
            let item = new AdministratableQuestion();
            item.type = Question.CountingTypeRanked;
            if ((typeof text) == "string" ) {
                item.text = text;
            }
            item.addResponseOption(new ResponseOption("None of the Above"));
            this.$data.allquestions.push(item);
            return item;
        },
        removeQuestion: function(q) {
            var index = this.$data.allquestions.findIndex( (element) => element == q );
            this.$data.allquestions.splice(index, 1);
        },
    },
});
