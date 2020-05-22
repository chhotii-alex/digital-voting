class Voter {
    constructor(obj) {
        this.currentEmail = obj.currentEmail;
        this.name = obj.name;
        this.username = obj.username;
        this.allowedToVote = obj.allowedToVote;
        this.admin = obj.admin;
    };
    grantVoting() {
        userActive();
        this.allowedToVote = true;
        this.savePrivChanges();
    }
    revokeVoting() {
        userActive();
        this.allowedToVote = false;
        this.savePrivChanges();
    }
    grantAdmin() {
        userActive();
        this.admin = true;
        this.savePrivChanges();
    }
    revokeAdmin() {
        userActive();
        let prompt = "Are you sure you want to revoke admin privileges from " + this.name + "?";
        userActive();
        let response = confirm(prompt);
        userActive();
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
            handleQueryError(error);
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
        userRefreshTime: '',
        allusers: [],
        errorText: '',
        updateTimerToken: '',
    },
    mounted() {
        userActive();
        this.$data.username = getUser();
        let url = "voters/" + this.$data.username;
        let aPromise = axios.get(url);
        aPromise.then(response => this.processUserInfo(response), error => handleQueryError(error));
        this.fetchUsers();
    },
    watch: {
        showOldQuestions: function(val) {
            userActive();
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
        fetchUsers: function() {
            userActive();
            let url = "voters/";
            let promise = axios.get(url);
            promise.then(response => this.processUserList(response), error => handleQueryError(error));
        },
        processUserList: function(response) {
            this.$data.showingUsers = true;
            this.$data.userRefreshTime = new Date();
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
