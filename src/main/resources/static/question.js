class ResponseOption {
    constructor(text) {
        this.text = text;
    };
    getText() {
        return this.text.trim();
    };
    toString() {
        return this.text.trim();
    };
    trimSpaces() {
        this.text = this.getText();
    }
    getUniqueLabel() {
        return `${ this.questionID } ${ this.getText() }`;
    };
}
class Question {
    constructor(text="") {
        this.original = null;
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
