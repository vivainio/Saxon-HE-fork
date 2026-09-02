package net.sf.saxon;

import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regex lookahead/lookbehind assertions ((*positive_lookahead:...), etc.) are
 * new in the regex engine that shipped with Saxon-HE 13.0 (part of the draft
 * XPath/XQuery 4.0 feature set) and are not license-gated: they are wired
 * unconditionally into net.sf.saxon.regex.RECompiler, so they work in HE.
 */
class RegexLookaroundTest {

    private boolean matches(String input, String pattern) throws SaxonApiException {
        Processor processor = new Processor(false);
        XPathCompiler xpath = processor.newXPathCompiler();
        XdmValue result = xpath.evaluate(
                "matches('" + input + "', '" + pattern.replace("'", "''") + "')", null);
        return ((XdmAtomicValue) result).getBooleanValue();
    }

    @Test
    void positiveLookaheadMatches() throws SaxonApiException {
        assertTrue(matches("foobar", "foo(*positive_lookahead:bar)"));
    }

    @Test
    void positiveLookaheadFailsWhenAssertionFails() throws SaxonApiException {
        assertFalse(matches("foobaz", "foo(*positive_lookahead:bar)"));
    }

    @Test
    void negativeLookaheadMatches() throws SaxonApiException {
        assertTrue(matches("foobaz", "foo(*negative_lookahead:bar)"));
    }

    @Test
    void negativeLookaheadFailsWhenAssertionSucceeds() throws SaxonApiException {
        assertFalse(matches("foobar", "foo(*negative_lookahead:bar)"));
    }

    @Test
    void positiveLookbehindMatches() throws SaxonApiException {
        assertTrue(matches("foobar", "(*positive_lookbehind:foo)bar"));
    }

    @Test
    void negativeLookbehindMatches() throws SaxonApiException {
        assertTrue(matches("bazbar", "(*negative_lookbehind:foo)bar"));
    }

    @Test
    void negativeLookbehindFailsWhenAssertionSucceeds() throws SaxonApiException {
        assertFalse(matches("foobar", "(*negative_lookbehind:foo)bar"));
    }
}
