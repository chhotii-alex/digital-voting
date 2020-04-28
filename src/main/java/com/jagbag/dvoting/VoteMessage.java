package com.jagbag.dvoting;

/**
 * Packaging of the data required to cast a vote sent by the client.
 */
public class VoteMessage {
    /** chit containing the number that represents the submitting voter's identity */
    public String meChit;
    /** version of meChit signed by the CTF */
    public String meChitSigned;
    /** chit containing the Voter's chosen response, and the number representing their response */
    public String responseChit;
    /** version of responseChit signed by the CTF */
    public String responseChitSigned;
}
