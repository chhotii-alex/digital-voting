var landingApp = new Vue({
    el: '#landingapp',
    data: {
        username: null,
        name: null,
        email: null,
        allowedToVote: false,
        admin: false,
        isShowingEdit: false,
        newName: '',
        newEmail: '',
        newEmailNote: '',
        canGrantProxy: false,
        proxyHolder: null,
        proxyAccepted: false,
        selectedPotentialProxy: '',
        potentialProxies: [],
        acceptedProxyGrantees: [],
    },
    mounted() {
        this.$data.username = getUser();
        this.getUserInfo();
        userActive();
    },
    methods: {
        getUserInfo: function() {
            let url = "voters/" + this.$data.username;
            let aPromise = axios.get(url);
            aPromise.then(response => this.processUserInfo(response), error => handleQueryError(error));
        },
        processUserInfo: function(response) {
            console.log(response.data);
            this.$data.name = response.data.name;
            this.$data.email = response.data.currentEmail;  // may be oldEmail if new email not confirmed
            this.$data.allowedToVote = response.data.allowedToVote;
            // Determine whether to show controls for asking s.o. to hold one's proxy
            if (this.$data.allowedToVote) {  // only relevant for voters...
                if (!response.data.acceptedProxyGrantees.length) {  // if you're not holding someone else's proxy...
                    this.$data.canGrantProxy = true;
                    this.fetchPotentialProxies();
                }
                this.$data.proxyHolder = response.data.proxyHolder;
                this.$data.proxyAccepted = response.data.proxyAccepted;
                this.$data.acceptedProxyGrantees = response.data.acceptedProxyGrantees;
                var j;
                for (j = 0; j < this.$data.acceptedProxyGrantees.length; ++j) {
                    this.$data.acceptedProxyGrantees[j].url
                        = "voting?behalf=" + encodeURIComponent(this.$data.acceptedProxyGrantees[j].username);
                }
            }
            this.$data.admin = response.data.admin;
            this.$data.newName = this.$data.name;
            if (response.data.email != response.data.currentEmail) {
                this.$data.newEmailNote = 'Please check your email for a confirmation message.';
            }
            else {
                this.$data.newEmailNote = '';
            }
            this.$data.newEmail = response.data.email;  // email most recently entered will be in the email field
        },
        showAccountEditing: function() {
            userActive();
            this.$data.isShowingEdit = true;
        },
        saveEdits: function() {
            userActive();
            let url = "voters/" + encodeURIComponent(this.$data.username);
            let editedVoter = { name:this.$data.newName, email:this.$data.newEmail  };
            let promise = axios.patch(url, editedVoter);
            promise.then( response => this.processUpdateResponse(response) )
            .catch(function (error) {
                handleQueryError(error);
            });
        },
        processUpdateResponse: function(response) {
            this.getUserInfo();
            this.$data.isShowingEdit = false;
        },
        requestProxyHolder: function() {
            userActive();
            if (this.$data.selectedPotentialProxy) {
                if (confirm("Request that " + this.$data.selectedPotentialProxy.name + " hold your proxy?")) {
                    let url = "voters/requestproxy?proxy="
                        + encodeURIComponent(this.$data.selectedPotentialProxy.username);
                    let promise = axios.get(url);
                    promise.then( response => this.processRequestResponse(response)).catch( error => handleQueryError(error));
                }
            }
        },
        processRequestResponse: function() {
            this.getUserInfo();
        },
        fetchPotentialProxies: function() {
            let url = "/pp";
            let aPromise = axios.get(url);
            aPromise.then(response => { this.$data.potentialProxies = response.data; },
                            error => handleQueryError(error));
        },
        revokeProxy: function() {
            userActive();
            var prompt;
            if (this.$data.proxyAccepted) {
                prompt = "Revoke " + this.$data.proxyHolder.name + "'s holding of your proxy?";
            }
            else {
                prompt = "Cancel the request that "  + this.$data.proxyHolder.name + " hold your proxy?";
            }
            if (!confirm(prompt)) {
                return;
            }
            userActive();
            let url = "voters/requestproxy";
            let promise = axios.get(url);
            promise.then( response => this.processRequestResponse(response)).catch( error => handleQueryError(error));

        },
    },
    computed: {
        canSaveEdits: function() {
            var dataIsValid = true;
            var dataHasChanged = false;
            if (this.$data.newName.length < 1) { dataIsValid = false; }
            if (this.$data.newEmail.length < 1) { dataIsValid = false; }
            if (this.$data.newName != this.$data.name) { dataHasChanged = true; }
            if (this.$data.newEmail != this.$data.email) { dataHasChanged = true; }
            if (!isEmailValid(this.$data.newEmail)) { dataIsValid = false; }
            return (dataHasChanged && dataIsValid);
        },
        hasActiveProxyHolder: function() {
            return (this.$data.proxyHolder && this.$data.proxyAccepted);
        }
    },
    watch: {
        newName: function() {
            userActive();
        },
        newEmail: function() {
            userActive();
        },
    },
});
