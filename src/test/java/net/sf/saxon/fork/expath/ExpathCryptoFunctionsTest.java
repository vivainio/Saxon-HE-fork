package net.sf.saxon.fork.expath;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmAtomicValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpathCryptoFunctionsTest {

    private Processor processor;
    private XPathCompiler xp;

    @BeforeEach
    void setup() {
        processor = new Processor(false);
        ExpathExtensions.registerAll(processor.getUnderlyingConfiguration());
        xp = processor.newXPathCompiler();
        xp.declareNamespace("crypto", "http://expath.org/ns/crypto");
    }

    private String evalStr(String expr) throws SaxonApiException {
        return xp.compile(expr).load().evaluate().itemAt(0).getStringValue();
    }

    @Test
    void md5Hex() throws Exception {
        // md5("hello") = 5d41402abc4b2a76b9719d911017c592
        assertEquals("5d41402abc4b2a76b9719d911017c592",
                evalStr("crypto:hash('hello', 'MD5', 'hex')"));
    }

    @Test
    void sha256Hex() throws Exception {
        // sha256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                evalStr("crypto:hash('hello', 'SHA-256', 'hex')"));
        // Bare "SHA256" alias also works
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                evalStr("crypto:hash('hello', 'SHA256', 'hex')"));
    }

    @Test
    void sha256Base64Default() throws Exception {
        // Default encoding is base64
        assertEquals("LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=",
                evalStr("crypto:hash('hello', 'SHA-256')"));
    }

    @Test
    void hmacSha256() throws Exception {
        // HMAC-SHA256(key="key", data="The quick brown fox jumps over the lazy dog")
        // = f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                evalStr("crypto:hmac('The quick brown fox jumps over the lazy dog', 'key', 'SHA-256', 'hex')"));
        // Algorithm alias HmacSHA256 also accepted
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                evalStr("crypto:hmac('The quick brown fox jumps over the lazy dog', 'key', 'HmacSHA256', 'hex')"));
    }

    @Test
    void unknownAlgorithm() {
        assertThrows(SaxonApiException.class,
                () -> evalStr("crypto:hash('hello', 'BOGUS-128', 'hex')"));
    }

    @Test
    void hexBinaryInputAccepted() throws Exception {
        // xs:hexBinary input should hash to the same as the raw bytes
        // hex('deadbeef') = bytes 0xDE,0xAD,0xBE,0xEF
        // sha256(bytes) = 5f78c33274e43fa9de5659265c1d917e25c03722dcb0b8d27db8d5feaa813953
        assertEquals("5f78c33274e43fa9de5659265c1d917e25c03722dcb0b8d27db8d5feaa813953",
                evalStr("crypto:hash(xs:hexBinary('deadbeef'), 'SHA-256', 'hex')"));
    }
}
