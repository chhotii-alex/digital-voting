var counter = 1;

/* Fischer-Yates ftw */
function shuffle(a) {
    var j, x, i;
    for (i = a.length - 1; i > 0; i--) {
        j = Math.floor(Math.random() * (i + 1));
        x = a[i];
        a[i] = a[j];
        a[j] = x;
    }
    return a;
}

function vote(items, q, array) {
    var v = Math.floor(Math.random() * 1000000);
    var j;
    for (j = 0; j < items.length; ++j) {
	    var c = Math.floor(Math.random() * 1000000);
	    var obj = {};
	    obj.id = counter; ++counter;
	    obj.question = q;
    	obj.response = items[j];
	    obj.receivedWhen = "2020-05-07T19:01:55.300058";
    	obj.voterChitNumber = v;
	    obj.responseChitNumber = c;
	    obj.ranking = j;
	    array.push(obj);
    }
}

function question(text) {
    var obj = {};
    obj.text = text;
    obj.id = counter; ++counter;
    return obj;
}

function showOnPage(data) {
    let str = JSON.stringify(data, undefined, 4);
    var newDiv = document.createElement("div");
    var newPre = document.createElement("pre");
    var newContent = document.createTextNode(str);
    newDiv.appendChild(newPre);
    newPre.appendChild(newContent);
    var place = document.getElementById("place");
    document.body.insertBefore(newDiv, place);
}

function oneTest(questionText, rankings) {
    var array = [];
    var q1 = question(questionText);
    var j;
    for (j = 0; j < rankings.length; ++j) {
        vote(rankings[j], q1, array);
    }
    shuffle(array);
    tabulateRankedChoiceResults(q1, array);
    showOnPage(q1);

}

function doit() {
    console.log("Doing test...");
    oneTest("What snacks shall we have?", [
        ['cookies'],
        ['chips', 'cookies', 'celery'],
        ['celery'],
        ['celery', 'cookies'],
        ['chips', 'celery', 'cookies'],
        ['cookies', 'celery'],
        ['chips', 'cookies'],
        ['celery', 'cookies'],
    ]);

    oneTest("Where should we go?", [
        ['mall', 'library', 'None of the Above'],
        ['library'],
        ['theatre'],
        ['theatre', 'library'],
        ['library', 'theatre', 'None of the Above'],
        ['library', 'theatre', 'mall'],
        ['library', 'None of the Above'],
        ['mall', 'None of the Above'],
     ]);

     oneTest("Who should we see?", [
        ['Sam'],
        ['Sam'],
        ['None of the Above'],
        ['None of the Above'],
        ['None of the Above'],
     ]);

/* Sometimes the result is supposed to depend on a coin toss. */
     oneTest("minor office", [
        ['Bruce'],
        ['Lillian'],
     ]);

/*  This test case displays a rather pathological condition. At the end of the first round, neither
   Sam nor James has the majority. Since they have an equal number of first-choice ballots, it's random
   which one is chosen for elimination. With different coin flips, the winner can be either James or
   "None of the Above".
*/
    oneTest("Who?", [
        ['Sam', 'James'],
        ['Sam', 'James'],
        ['James'],
        ['James'],
        ['None of the Above'],
        ['None of the Above'],
        ['None of the Above'],
     ]);

 /* Does a simplified scenario display the pathology? */
    oneTest("Who2", [
        ['Bruce', 'Lillian'],
        ['Lillian'],
        ['None of the Above'],
     ]);

    oneTest("color1", [
        ['red', 'green'],
        ['green', 'red'],
        ['red', 'green'],
        ['green', 'red'],
        ['green', 'None of the Above'],
        ['red'],
    ]);

    oneTest("color2", [
        ['blue', 'None of the Above'],
        ['green', 'None of the Above'],
        ['green', 'None of the Above'],
        ['green', 'None of the Above'],
        ['green', 'None of the Above'],
        ['red'],
        ['red'],
        ['red'],
        ['red'],
    ]);

}
