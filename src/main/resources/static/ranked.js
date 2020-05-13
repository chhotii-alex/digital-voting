
/* Returns an array of all permutations of array a. Recursive. */
function permutations(a) {
    if (a.length < 2) {
        return [a];
    }
    var result = [];
    var j, i;
    for (j = 0; j < a.length; ++j) {
        var first = a[j];
        var remaining = a.slice(0, j).concat(a.slice(j+1));
        var perms = permutations(remaining);
        for (i = 0; i < perms.length; ++i) {
            result.push([first].concat(perms[i]));
        }
    }
    return result;
}

/* Returns all possible sets of choices inherent in array a: each set containing one option from a[0],
    one from a[1], ... one from a[n]. */
function selections(a) {
    var results = [];
    if (a.length > 0) {
        var j, i;
        for (j = 0; j < a[0].length; ++j) {
            var first = a[0][j];
            var remaining;
            if (a.length < 2) {
                remaining = [[]];
            }
            else {
                remaining = selections(a.slice(1));
            }
            for (i = 0; i < remaining.length; ++i) {
                results.push([first].concat(remaining[i]));
            }
        }
    }
    return results;
}

/* Given an array of arrays, return concatenation of all the arrays. */
function flatten(a) {
    var results = a[0];
    var j;
    for (j = 1; j < a.length; ++j) {
        results = results.concat(a[j]);
    }
    return results;
}


/* Produces an array of all orderings of array a such that elements are in non-decending order by
vote.
*/
function allEquivalentOrderings(a) {
    var j;
    /* Sort the list in ascending order (by votes property). Items with the same vote numbers will
        be arbitrarily ordered with respect to each other, though. */
    a.sort( (x, y) => x.votes-y.votes );
    /* Take the flat list and turn it into a list of sets, each set containing all those items with
        a given number of votes. */
    var sets = [];
    var currentSet = [a[0]];
    sets.push(currentSet);
    for (j = 1; j < a.length; ++j) {
        if (a[j].votes == currentSet[0].votes) {
            currentSet.push(a[j]);
        }
        else {
            currentSet = [a[j]];
            sets.push(currentSet);
        }
    }
    /* For each set in the list, generate all permutations of orderings. */
    var perms = [];
    for (j = 0; j < sets.length; ++j) {
        perms.push(permutations(sets[j]));
    }
    var results = selections(perms);
    results = results.map(list => flatten(list));
    return results;
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
        question.outcomes: an array of possible outcomes that are possible depending on coin flips.
            Each member of question.outcomes is an object containing:
                winner : identifier of the one candidate that is calculated to be the winner. Note: I'm
                    assuming we are filling ONE position; thus only a single winner is identified.
                    This will be null if nobody voted.
                rounds : an array of results, one member of the array for each round of the calculation.

    In each round of the tabulation:
        Ballots are grouped by the lowest numbered valid candidate who has not been eliminated.
        If one candidate has a majority of the ballots remaining they are the winner.
        If not, one candidate with the lowest number of ballots will be eliminated.
            Ties for lowest number of ballots will be broken such that a candidate with the
            fewest number of first place votes at the start of the first round will be eliminated.
            If there is still a tie, it is stipulated to be broken by coin flip.

    Note: one candidate should be "None of the Above" (with that exact spelling, capitalization, etc.)
    This is a special candidate in that is never eliminated in any round.

    NOTE: Note that the outpcome of the election may be determined by coin flips!!!
    I do not use random. That would result in the results potentially being different every time this
    calculation is re-done... possibly every time a page refreshes... ugh. Mass confusion.
    Rather, I create a list of all possible orderings in which candidates could be prioritized for elimination
    in all possible match-ups, referred to in the code as 'universes'. Imagine, a pair of parallel
    universes is spawned every time a coin is flipped. I don't calculate the set of universes by branching
    at each possibility for a coin flip (that seems messy to me), but every possible way things could go
    is represented in the set of outcomes for each universe. I repeat the tabulation calculation for each
    'universe'. Rather than report the blow-by-blow for each universe, I report one set of rounds for each
    distinct winner. Thus, I report only one scenario of the various possible coin-flipping scenarios that
    could arrive at each possible winner.
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
    universes = allEquivalentOrderings(eliminationPriorityQueue);
    roundsByWinner = {};
    var u;
    for (u = 0; u < universes.length; ++u) {
        eliminationPriorityQueue = universes[u];
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
        roundsByWinner[victor] = rounds;
    } // END for each universe
    question.outcomes = [];
    var possibleWinners = Object.keys(roundsByWinner);
    for (j = 0; j < possibleWinners.length; ++j) {
        var win = possibleWinners[j];
        question.outcomes.push( { 'rounds':roundsByWinner[win], 'winner':win });
    }
    question.numVotersResponded = totalVotersResponding;
}
