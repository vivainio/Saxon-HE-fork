package net.sf.saxon.trace;

public class TraceLevel {

    /** No tracing **/
    public static final int NONE = 0;
    /** Function and template calls **/
    public static final int LOW = 1;
    /** Instructions (or the equivalent in XQuery) */
    public static final int NORMAL = 2;
    /** All expressions */
    public static final int HIGH = 3;


}


