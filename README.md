# digital-voting
Secure on-line voting based on a blinded signing protocol.

Suppose you want to run elections within your organization, but on-line, because with the pandemic you can't get together 
physically to hand in little folded pieces of paper. But you want your votes to be confidential&mdash; known only to 
you and any unearthly higher power, but NOT to individuals with leadership roles in the organization&mdash; and thus not to the
people running the web server running your on-line election, as they are likely to be beholden to the leadership.

This software implements a protocol similar to that briefly described in <i>Applied Cryptography: Protocols, Algorithms, and Source Code in C</i>
by Bruce Schneier, (c) 1994, John Wiley & Sons Inc., New York, on pages 106 - 107. See also pp. 403-404, and 
https://en.wikipedia.org/wiki/Blind_signature#Blind_RSA_signatures for some clarifications on the math involved.

1. Each Question posted for voting has a set of possible responses, i.e. "yes", "no", or "abstain". Each Voter, for each Question,
generates a set of messages that I refer to as Chits: each Chit containing one of the possible responses, as well as an 
identifier for the Question it relates to, and&mdash; here is the important part&mdash; a randomly generated serial number
from a range large enough to avoid duplicates with other Voters. Also, for each Question, the Voter generates a Chit that 
refers not to a particular response, but to the entire Question.

2. Each Voter <em>blinds</em> each of their Chits: i.e., they apply a cryptographic transformation to the Chit that makes it
unreadable. Each blinded Chit is sent to the Central Tabulating Facility (CTF) with information giving the identity of the Voter
who sent it in. 

3. The Central Tabulating Facility checks to make sure that the Voter sending in each Chit is authorized to vote, and that the 
Voter is not sending in an excessive number of Chits per Question. I.e., if there are 3 possible responses on a Question, we
expect 4 Chits: one for each response, plus one. If these tests pass then the CTF <em>signs</em> each chit and sends it back
to the Voter. 

4. Upon receiving the signed, blinded Chit back from the CTF, the Voter <em>un</em>blinds the Chit. This yields a Chit that is
signed but not blinded. 

5. The Voter choses their response on the Question, and sends to the CTF the corresponding response Chit in two forms&mdash; the original
plaintext, and the signed (but unblinded) version&mdash; accompanied by the plaintext and signed versions of the Chit for
the Question itself. 

6. The CTF verifies that the signed message arriving with each Chit is the signed version of the plaintext message. This verifies
that the CTF had, in fact, signed that Chit (even though it could not read the Chit before, because it was blinded!) The 
CTF verifies that the serial number in the Chit for the Question itself is unique amongst the votes it is tabulating for the
Question. This verifies that a Voter had not sent in multiple different responses to the same Question. The one thing
that the CTF pointedly does <em>not</em> verify at this stage is the identity of the Voter. The fact that the Chit was 
proovably signed by the CTF verifies that it originates from a valid Voter. The CTF is not privy to the knowledge of which 
Voter sent which Chit, because when the CTF received the Chit to sign it, it was blinded.

7. The CTF supplies, upon request, a list of the Votes that were counted, including, for each, the actual answer selected, and
the serial numbers for the response and for the Question/Response. Each Voter (...actually, the client software on the Voter's
behalf) can verify that the serial number of the answer they gave appears amongst the Votes counted, with the correct response, and
the correct serial number they supplied for the Question itself; and that the total Votes for each response match what the 
election authories claim.

The magic of all this hinges on there being two reversable functions which can be applied to a message in either order. That is,
there is a pair of functions for blinding and unblinding a message, b() and b'(), such that b'(b(m)) = m; and there is
a set of functions for signing and verifying a message, such that s'(s(m)) = m; and&mdash; here is the kicker&mdash;
s'(b'(s(b(m)))) = m or b'(s(b(m))) = s(m). <em>Signing</em> a message consists of encrypting it with one's <em>private</em> key. 
Unlike encrypting a message with one's public key&mdash; which yields a message that only you, the private key holder can
read&mdash; signing something with one's private key yields a message that we all can read (because we all know your public
key) and we know that only you could've applied this transformation to (because only you have the privileged knowledge of
your own private key. <em>Blinding</em> a message in such a way that the blinding can be reversed requires a secret on
the Voter's side: a randomly-chosen number (from a very large range), k, that is mathematically related to key the CTF uses for
signing. I'm not going to even try to explain why the math works: see Schneier pp. 403-404 for a very very sketchy 
overview and proof. (Note that when Schneier says "divide by k" he actually means "multiply by the mod inverse of k" and that
the proof plays fast and loose with conflating the concepts of equality and congruence.) 

If your eyes glazed over reading the previous paragraph, don't worry; you don't need to know the math to use this software.

The basic protocol itself is pretty simple, but to provide a practical implementation requies attending to many details. 
Someone has to create and post Questions. Individuals who have the right to vote have to be identified. Thus we have a complete
account-management system, with reasonable security: salted and hashed passwords in the database, that kind of thing.
As everyone is a potential voter, every user's account is an instance of "Voter", whether they have voting privileges or not.
There are two types of privilege a user account may have: "admin" and "voting". Users with admin privileges have a limited
ability to manage the user database&mdash; they may grant and revoke privileges (of both types). Admin users may also 
create, post, and close Questions. Users with voting privilege may vote. 

The database, containing details of user accounts, and the Questions and Votes, is persisted to disk in the form of an H2
database. This is run by the JVM, within the server software.

The RSA key used to sign Chits is NOT saved to disk anywhere. If the server re-starts, all Questions have to be closed, and
new Questions posted if need be, because the server cannot verify its own signatures from the previous run. In general, 
information that could be used by an attacker is not saved to disk. 
