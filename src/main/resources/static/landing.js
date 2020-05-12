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
            this.$data.name = response.data.name;
            this.$data.email = response.data.currentEmail;  // may be oldEmail if new email not confirmed
            this.$data.allowedToVote = response.data.allowedToVote;
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
            let url = "voters/" + this.$data.username;
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
