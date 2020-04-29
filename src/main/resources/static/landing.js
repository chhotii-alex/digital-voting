var landingApp = new Vue({
    el: '#landingapp',
    data: {
        username: null,
        name: null,
        email: null,
        allowedToVote: false,
        admin: false,
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
        },
    },
});
