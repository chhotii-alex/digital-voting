package com.jagbag.dvoting;

/**
 * Interface to some facility for appropriately logging anything suspicious.
 */
public class TroubleLogger {
    public static void reportTrouble(String str) {
        // TODO: voting protocol irregularities go here. How to report?
        System.err.println(str);
    }
}
