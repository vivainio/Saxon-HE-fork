package net.sf.saxon.fork.expath;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpathBinaryFunctionsTest {

    private Processor processor;
    private XPathCompiler xp;

    @BeforeEach
    void setup() {
        processor = new Processor(false);
        ExpathExtensions.registerAll(processor.getUnderlyingConfiguration());
        xp = processor.newXPathCompiler();
        xp.declareNamespace("bin", "http://expath.org/ns/binary");
    }

    private XdmValue eval(String expr) throws SaxonApiException { return xp.compile(expr).load().evaluate(); }
    private String evalStr(String expr) throws SaxonApiException { return eval(expr).itemAt(0).getStringValue(); }
    private long evalLong(String expr) throws SaxonApiException {
        return ((XdmAtomicValue) eval(expr).itemAt(0)).getLongValue();
    }
    private byte[] evalBytes(String expr) throws SaxonApiException {
        return Base64.getDecoder().decode(((XdmAtomicValue) eval(expr).itemAt(0)).getStringValue());
    }

    @Test
    void hexRoundtrip() throws Exception {
        assertEquals("deadbeef", evalStr("bin:hex-string(bin:hex('deadbeef'))"));
        assertEquals("0102", evalStr("bin:hex-string(bin:hex('0102'))"));
    }

    @Test
    void binRoundtrip() throws Exception {
        // 0x55 = 01010101
        assertEquals("01010101", evalStr("bin:bin-string(bin:bin('01010101'))"));
    }

    @Test
    void length() throws Exception {
        assertEquals(4L, evalLong("bin:length(bin:hex('deadbeef'))"));
    }

    @Test
    void partSlice() throws Exception {
        // deadbeef → part(_, 1, 2) → adbe
        assertEquals("adbe", evalStr("bin:hex-string(bin:part(bin:hex('deadbeef'), 1, 2))"));
        // 2-arg form: tail
        assertEquals("beef", evalStr("bin:hex-string(bin:part(bin:hex('deadbeef'), 2))"));
    }

    @Test
    void joinConcat() throws Exception {
        assertEquals("dead0000beef",
                evalStr("bin:hex-string(bin:join((bin:hex('dead'), bin:hex('0000'), bin:hex('beef'))))"));
    }

    @Test
    void octetsRoundtrip() throws Exception {
        XdmValue v = eval("bin:to-octets(bin:hex('deadbeef'))");
        assertEquals(4, v.size());
        assertEquals(0xde, ((XdmAtomicValue) v.itemAt(0)).getLongValue());
        assertEquals("deadbeef", evalStr("bin:hex-string(bin:from-octets((222,173,190,239)))"));
    }

    @Test
    void bitwiseOps() throws Exception {
        assertEquals("0f", evalStr("bin:hex-string(bin:and(bin:hex('ff'), bin:hex('0f')))"));
        assertEquals("ff", evalStr("bin:hex-string(bin:or(bin:hex('f0'), bin:hex('0f')))"));
        assertEquals("ff", evalStr("bin:hex-string(bin:xor(bin:hex('aa'), bin:hex('55')))"));
        assertEquals("00", evalStr("bin:hex-string(bin:not(bin:hex('ff')))"));
    }

    @Test
    void bitwiseLengthMismatch() {
        assertThrows(SaxonApiException.class, () -> eval("bin:and(bin:hex('ff'), bin:hex('0000'))"));
    }

    @Test
    void shiftLeft() throws Exception {
        // 0x01 << 1 = 0x02
        assertEquals("02", evalStr("bin:hex-string(bin:shift(bin:hex('01'), 1))"));
        // 0x80 >> 1 = 0x40
        assertEquals("40", evalStr("bin:hex-string(bin:shift(bin:hex('80'), -1))"));
    }

    @Test
    void packUnpackInteger() throws Exception {
        // 0x12345678 big-endian as 4 bytes
        assertEquals("12345678", evalStr("bin:hex-string(bin:pack-integer(305419896, 4))"));
        assertEquals(305419896L, evalLong("bin:unpack-integer(bin:hex('12345678'), 0, 4)"));
        // little-endian
        assertEquals("78563412",
                evalStr("bin:hex-string(bin:pack-integer(305419896, 4, 'least-significant-first'))"));
    }

    @Test
    void encodeDecodeString() throws Exception {
        // UTF-8 default: 'A' = 0x41
        assertEquals("41", evalStr("bin:hex-string(bin:encode-string('A'))"));
        assertEquals("hello",
                evalStr("bin:decode-string(bin:encode-string('hello'))"));
        // ISO-8859-1: é = 0xE9
        assertEquals("e9", evalStr("bin:hex-string(bin:encode-string('é', 'ISO-8859-1'))"));
    }

    @Test
    void padding() throws Exception {
        assertEquals("0000ff",
                evalStr("bin:hex-string(bin:pad-left(bin:hex('ff'), 2))"));
        assertEquals("ff0000",
                evalStr("bin:hex-string(bin:pad-right(bin:hex('ff'), 2))"));
        assertEquals("aaaaff",
                evalStr("bin:hex-string(bin:pad-left(bin:hex('ff'), 2, 170))"));
    }

    @Test
    void find() throws Exception {
        // needle "beef" in "deadbeef00" at offset 2
        assertEquals(2L, evalLong("bin:find(bin:hex('deadbeef00'), 0, bin:hex('beef'))"));
        // not found → empty sequence
        assertEquals(0, eval("bin:find(bin:hex('deadbeef'), 0, bin:hex('0000'))").size());
    }
}
