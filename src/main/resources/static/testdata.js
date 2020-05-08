var counter = 1;
var array = [];


function vote(items, q) {
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

function doit() {
    console.log("Doing test...");
    array = [];
    var q1 = question("What snacks shall we have?");
    vote(['cookies'], q1);
    vote(['chips', 'cookies', 'celery'], q1);
    vote(['celery'], q1);
    vote(['celery', 'cookies'], q1);
    vote(['chips', 'celery', 'cookies'], q1);
    vote(['cookies', 'celery'], q1);
    vote(['chips', 'cookies'], q1);
    vote(['celery', 'cookies'], q1);
    tabulateRankedChoiceResults(q1, array);
    document.getElementById("q1").innerHTML = JSON.stringify(q1, undefined, 4);
    
    array = [];
    var q2 = question("Where should we go?");
    vote(['mall', 'library', 'NOTA'], q2);
    vote(['library'], q2);
    vote(['theatre'], q2);
    vote(['theatre', 'library'], q2);
    vote(['library', 'theatre', 'NOTA'], q2);
    vote(['library', 'theatre', 'mall'], q2);
    vote(['library', 'NOTA'], q2);
    vote(['mall', 'NOTA'], q2);
    tabulateRankedChoiceResults(q2, array);
    document.getElementById("q2").innerHTML = JSON.stringify(q2, undefined, 4);

}

doit();
