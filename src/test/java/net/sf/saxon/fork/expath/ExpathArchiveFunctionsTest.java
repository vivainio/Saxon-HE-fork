package net.sf.saxon.fork.expath;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpathArchiveFunctionsTest {

    private Processor processor;
    private XPathCompiler xp;

    @BeforeEach
    void setup() {
        processor = new Processor(false);
        ExpathExtensions.registerAll(processor.getUnderlyingConfiguration());
        xp = processor.newXPathCompiler();
        xp.declareNamespace("arch", "http://expath.org/ns/archive");
    }

    private XdmValue eval(String expr) throws SaxonApiException { return xp.compile(expr).load().evaluate(); }
    private String evalStr(String expr) throws SaxonApiException { return eval(expr).itemAt(0).getStringValue(); }

    @Test
    void createListAndExtract() throws Exception {
        String expr =
                "let $z := arch:create(('a.txt','b.txt'), ('hello','world')) " +
                "return string-join((arch:entries($z), '|', arch:extract-text($z)), ',')";
        // expected: a.txt,b.txt,|,hello,world
        assertEquals("a.txt,b.txt,|,hello,world", evalStr(expr));
    }

    @Test
    void extractByName() throws Exception {
        // Filter to just b.txt
        String expr =
                "let $z := arch:create(('a.txt','b.txt'), ('hello','world')) " +
                "return arch:extract-text($z, 'b.txt')";
        assertEquals("world", evalStr(expr));
    }

    @Test
    void extractBinary() throws Exception {
        String expr =
                "let $z := arch:create('blob.bin', xs:base64Binary('AQID')) " +
                "return arch:extract-binary($z, 'blob.bin')";
        XdmItem item = eval(expr).itemAt(0);
        // Saxon prints xs:base64Binary as its base64 string
        assertEquals("AQID", item.getStringValue());
    }

    @Test
    void deleteEntry() throws Exception {
        String expr =
                "let $z := arch:create(('a.txt','b.txt','c.txt'), ('a','b','c'))," +
                "    $z2 := arch:delete($z, 'b.txt') " +
                "return string-join(arch:entries($z2), ',')";
        assertEquals("a.txt,c.txt", evalStr(expr));
    }

    @Test
    void updateOverwriteAndAppend() throws Exception {
        String expr =
                "let $z := arch:create(('a.txt','b.txt'), ('a','b'))," +
                "    $z2 := arch:update($z, ('a.txt','new.txt'), ('A','N')) " +
                "return string-join((arch:entries($z2), '|', arch:extract-text($z2)), ',')";
        // Order: a.txt (updated), b.txt (unchanged), new.txt (appended)
        assertEquals("a.txt,b.txt,new.txt,|,A,b,N", evalStr(expr));
    }

    @Test
    void options() throws Exception {
        assertEquals("zip",
                evalStr("arch:options(arch:create('x', 'y'))"));
    }
}
