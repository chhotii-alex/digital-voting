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

/*
    Input:
    question - A Question object. We don't actually use any of the data in the Question object. Rather,
        for reporting the results of this algorithm, fields are added to the Question object at the end.
    report - An array of vote records. These do not need to be sorted or ordered in any way.
        Each member of the array must be an Object (that is, a JS dictionary)
        with the following fields:
             voterChitNumber - anonymous identifier for an individual voter (and thus id for the Ballot)
             response - a string identifying a candidate
             ranking - the ranking of that candidate within that Ballot
    Output, as fields added to the Question:
        question.numVotersResponded : number of distinct Ballots identified
        question.winner : identifier of the one candidate that is calculated to be the winner. Note: I'm
            assuming we are filling ONE position; thus only a single winner is identified.
            This will be null if nobody voted.
        question.results : an array of results, one member of the array for each round of the calculation.

    In each round of the tabulation:
        Ballots are grouped by the lowest numbered valid candidate who has not been eliminated.
        If one candidate has a majority of the ballots remaining they are the winner.
        If not, one candidate with the lowest number of ballots will be eliminated.
            Ties for lowest number of ballots will be broken such that a candidate with the
            fewest number of first place votes at the start of the first round will be eliminated.
            If there is still a tie, it will be broken randomly.

    Note: one candidate should be "None of the Above" (with that exact spelling, capitalization, etc.)
    This is a special candidate in that is never eliminated in any round.
*/
function tabulateRankedChoiceResults(question, report) {
    let NOTA = "None of the Above"; // magic candidate that cannot be eliminated
    var i;
    var j;
    /* Organize vote records into one set per Voter.   */
    var perVoterResponses = {};
    for (j = 0; j < report.length; ++j) {
        var record = report[j];
        var meID = record.voterChitNumber;
        if (!perVoterResponses[meID]) {
            perVoterResponses[meID] = [];
        }
        perVoterResponses[meID].push(record);
    }
    /* Make an array listing the voter/ballot identifiers, so we can loop over them conveniently. */
    var respondingVoters = Object.keys(perVoterResponses);
    var totalVotersResponding = respondingVoters.length;
    /* Within each Voter's Ballot, sort candidates by ranking. */
    for (j = 0; j < totalVotersResponding; ++j) {
        var meID = respondingVoters[j];
        perVoterResponses[meID].sort( (a,b) => a.ranking-b.ranking );
    }
    /* For the purpose of breaking ties, rank the candidates (except for "None of the Above") by how
        many first place votes they have prior to any elimination. */
    var eliminationPriorityQueue = [];
    var firstChoiceTotals = {};
    for (j = 0; j < totalVotersResponding; ++j) {
        var meID = respondingVoters[j];
        var firstChoice = perVoterResponses[meID][0].response;
        if (firstChoice != NOTA) {
            if (!firstChoiceTotals[firstChoice]) {
                firstChoiceTotals[firstChoice] = 0;
            }
            firstChoiceTotals[firstChoice]++;
        }   // END first choice is not NOTA
    }  // END for each Ballot
    var candidatesWithNonZeroVotes = Object.keys(firstChoiceTotals);
    for (j = 0; j < candidatesWithNonZeroVotes.length; ++j) {
         var candidate = candidatesWithNonZeroVotes[j];
         eliminationPriorityQueue.push({name: candidate, votes: firstChoiceTotals[candidate]});
    }
    /* Shuffle the array of per-candidate totals, so that ordering of tied candidates is randomized */
    shuffle(eliminationPriorityQueue);
    /* Note that this is sorted in opposite direction of round results: fewer votes puts you earlier */
    eliminationPriorityQueue.sort( (a, b) => a.votes-b.votes );
    /* We just need the names */
    eliminationPriorityQueue = eliminationPriorityQueue.map(item => item.name);

    var rounds = [];
    var victor = null;
    var eliminatedCandidates = [];
    if (totalVotersResponding) {  // Don't run alogorithm if nobody voted!
        do {
            var remainingBallotsThisRound = 0;
            var perCandidateTotals = {};
            for (j = 0; j < totalVotersResponding; ++j) {
                var meID = respondingVoters[j];
                var arr = perVoterResponses[meID];
                for (i = 0; i < arr.length; ++i) {
                    var candidate = arr[i].response;
                    if (eliminatedCandidates.indexOf(candidate) < 0) { // find the first non-eliminated candidate
                        if (!perCandidateTotals[candidate]) {   // initialize counter if needed
                            perCandidateTotals[candidate] = 0;
                        }
                        perCandidateTotals[candidate]++;
                        remainingBallotsThisRound++
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
            if ((possibleWinner.votes/remainingBallotsThisRound) > 0.5) {
                victor = possibleWinner.name;
            }
            /* If no one candidate has a majority, select one candidate to eleminate for the next round. */
            if (!victor) {
                /* Remove "None of the Above" from list of candidates that are candidates for elimination */
                var sortedCandidates = roundResults.filter(item => (item.name != NOTA));
                var lastIndex = sortedCandidates.length-1;
                /* Get a list of candidates who are tied at the bottom */
                var possibleEliminatedCandidates = [];
                possibleEliminatedCandidates.push(sortedCandidates[lastIndex].name);
                var index = lastIndex - 1;
                while ((index >= 0) && (sortedCandidates[lastIndex].votes == sortedCandidates[index].votes)) {
                    possibleEliminatedCandidates.push(sortedCandidates[index].name);
                    --index;
                }
                /* If there's more than one of these, which shall we actually eliminate? */
                /* Rank these according to their standing in the eliminationPriorityQueue */
                var possibleEliminatedCandidatesWithRanking = [];
                for (i = 0; i < possibleEliminatedCandidates.length; ++i) {
                    var item = possibleEliminatedCandidates[i];
                    possibleEliminatedCandidatesWithRanking.push(
                            { name:item, rank:eliminationPriorityQueue.indexOf(item) });
                }
                possibleEliminatedCandidatesWithRanking.sort( (a,b) => a.rank-b.rank );
                console.log(possibleEliminatedCandidatesWithRanking[0].name);
                eliminatedCandidates.push(possibleEliminatedCandidatesWithRanking[0].name);
                console.log("Eliminated candidates now:");
                console.log(eliminatedCandidates);
            }
            rounds.push(roundResults);
            if (rounds.length > 5) break;
        }  while (!victor);
    } // END if anyone voted
    question.numVotersResponded = totalVotersResponding;
    question.results = rounds;
    question.winner = victor;
}
