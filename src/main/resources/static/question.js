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
        return `${ this.questionID }_${ this.getText() }`;
    };
}
class Question {
    constructor(text="") {
        this.original = null;
        this.text = text;
        this.possibleResponses = [];
        this.type = "SINGLE";
    };
    addResponseOption(opt) {
        opt.questionID = this.id;
        this.possibleResponses.push(opt);
    }
    addResponseOptionsFrom(obj) {
        this.possibleResponses = [];
        var j;
        for (j = 0; j < obj.possibleResponses.length; ++j) {
             var ro = obj.possibleResponses[j];
             var r = new ResponseOption(ro.text);
             this.addResponseOption(r);
         }
    }
    responseOptionForText(text) {
        var j;
        for (j = 0; j < this.possibleResponses.length; ++j) {
            if (this.possibleResponses[j].getText() == text) {
                return this.possibleResponses[j];
            }
        }
    }
    typeDescription() {
        if (this.type == "SINGLE") {
            return "Single";
        }
        else if (this.type == "MULTIPLE") {
            return "Multiple";
        }
        else if (this.type == "RANKED_CHOICE") {
            return "Ranked";
        }
        else {
            console.log("invalid type: ");
            console.log(this);
        }
    }
    isSingleChoice() {
        return (this.type == "SINGLE");
    }
    isRankedChoice() {
        return (this.type == "RANKED_CHOICE");
    }
}
Question.CountingTypeSingle = "SINGLE";
Question.CountingTypeMultiple = "MULTIPLE";
Question.CountingTypeRanked = "RANKED_CHOICE";
