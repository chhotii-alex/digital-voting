function tabulateRankedChoiceResults(question, report) {
    var perVoterResponses = {};
    var rounds = [];
    var i;
    var j;
    for (j = 0; j < report.length; ++j) {
        var record = report[j];
        var meID = record.voterChitNumber;
        if (!perVoterResponses[meID]) {
            perVoterResponses[meID] = [];
        }
        perVoterResponses[meID].push(record);
    }
    var respondingVoters = Object.keys(perVoterResponses);
    var totalVotersResponding = respondingVoters.length;
    for (j = 0; j < totalVotersResponding; ++j) {
        var meID = respondingVoters[j];
        perVoterResponses[meID].sort( (a,b) => a.ranking-b.ranking );
    }
    var victor = null;
    if (totalVotersResponding) {
        var eliminatedCandidates = [];
        do {
            var perCandidateTotals = {};
            for (j = 0; j < totalVotersResponding; ++j) {
                var meID = respondingVoters[j];
                var arr = perVoterResponses[meID];
                for (i = 0; i < arr.length; ++i) {
                    var candidate = arr[i].response;
                    if (eliminatedCandidates.indexOf(candidate) < 0) {
                        if (!perCandidateTotals[candidate]) {
                            perCandidateTotals[candidate] = 0;
                        }
                        perCandidateTotals[candidate]++;
                        break; // Go with the top-most non-eliminated candidate on voter's list
                    }
                } // END looking down this voter's list of choices
            } // END for each voter
            // sort candidates by how many votes they got that round
            var roundResults = [];
            var candidatesWithNonZeroVotes = Object.keys(perCandidateTotals);
            for (j = 0; j < candidatesWithNonZeroVotes.length; ++j) {
                var candidate = candidatesWithNonZeroVotes[j];
                roundResults.push({name: candidate, votes: perCandidateTotals[candidate]});
            }
	    roundResults.sort( (a, b) => b.votes-a.votes ); 
	    console.log(roundResults);
            var possibleWinner = roundResults[0];
            if ((possibleWinner.votes/totalVotersResponding) > 0.5) {
                if ((roundResults.length == 1) || (possibleWinner.votes > roundResults[1].votes)) {
                    victor = possibleWinner.name;
                }
            }
            if (!victor) {
                var lastIndex = roundResults.length-1;
                eliminatedCandidates.push(roundResults[lastIndex].name);
                var index = lastIndex - 1;
                while ((index >= 0) && (roundResults[lastIndex].votes == roundResults[index].votes)) {
                    eliminatedCandidates.push(roundResults[index].name);
                    --index;
                }
            }
            rounds.push(roundResults);
        }  while (!victor);
    } // END if anyone voted
    question.numVotersResponded = totalVotersResponding;
    question.results = rounds;
    question.winner = victor;
}
