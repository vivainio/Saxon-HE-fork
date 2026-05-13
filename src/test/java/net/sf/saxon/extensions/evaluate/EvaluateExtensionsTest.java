package net.sf.saxon.extensions.evaluate;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.Xslt30Transformer;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the dynamic evaluate extension functions.
 * See ENHANCEMENTS.md for documentation.
 */
class EvaluateExtensionsTest {

    private String run(String xsl, String xml) throws SaxonApiException {
        Processor processor = new Processor(false);
        EvaluateExtensions.registerOn(processor);
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable exec = compiler.compile(new StreamSource(new StringReader(xsl)));
        Xslt30Transformer t = exec.load30();
        StringWriter out = new StringWriter();
        Serializer ser = processor.newSerializer(out);
        t.transform(new StreamSource(new StringReader(xml)), ser);
        return out.toString();
    }

    private static final String EXSLT_HEADER =
            "<xsl:stylesheet version='2.0' " +
                    "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                    "xmlns:sk='http://exslt.org/dynamic'>";

    private static final String SAXON_HEADER =
            "<xsl:stylesheet version='2.0' " +
                    "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                    "xmlns:saxon='http://saxon.sf.net/'>";

    @Test
    void exsltArithmetic() throws Exception {
        String xsl = EXSLT_HEADER +
                "<xsl:template match='/'><r><xsl:value-of select=\"sk:evaluate('1+2')\"/></r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">3</r>"), "Expected 1+2=3, got: " + out);
    }

    @Test
    void saxonArithmetic() throws Exception {
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r><xsl:value-of select=\"saxon:evaluate('3*4')\"/></r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">12</r>"), "Expected 3*4=12, got: " + out);
    }

    @Test
    void exsltNodeSet() throws Exception {
        String xsl = EXSLT_HEADER +
                "<xsl:template match='/'><r><xsl:value-of select=\"sk:evaluate('//item[1]')\"/></r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<root><item>alpha</item><item>beta</item></root>");
        assertTrue(out.contains(">alpha</r>"), "Expected first item alpha, got: " + out);
    }

    @Test
    void saxonCountFn() throws Exception {
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r><xsl:value-of select=\"saxon:evaluate('count(//item)')\"/></r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<root><item/><item/><item/></root>");
        assertTrue(out.contains(">3</r>"), "Expected count=3, got: " + out);
    }

    @Test
    void xpathStoredInNode() throws Exception {
        // Evaluate an XPath string read from another document node against
        // the current source-doc context.
        String xsl = EXSLT_HEADER +
                "<xsl:template match='/'><r>" +
                "  <xsl:variable name='path'><cell>/INVOICE/HEADER/TYPE</cell></xsl:variable>" +
                "  <xsl:value-of select=\"sk:evaluate($path/cell)\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<INVOICE><HEADER><TYPE>380</TYPE></HEADER></INVOICE>");
        assertTrue(out.contains(">380</r>"), "Expected 380, got: " + out);
    }
}
