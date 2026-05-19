package net.sf.saxon.extensions.xquery;

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
 * Tests for the saxon:query() XQuery extension function.
 */
class QueryExtensionsTest {

    private String run(String xsl, String xml) throws SaxonApiException {
        Processor processor = new Processor(false);
        QueryExtensions.registerOn(processor);
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable exec = compiler.compile(new StreamSource(new StringReader(xsl)));
        Xslt30Transformer t = exec.load30();
        StringWriter out = new StringWriter();
        Serializer ser = processor.newSerializer(out);
        t.transform(new StreamSource(new StringReader(xml)), ser);
        return out.toString();
    }

    private static final String SAXON_HEADER =
            "<xsl:stylesheet version='3.0' " +
                    "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                    "xmlns:saxon='http://saxon.sf.net/'>";

    @Test
    void arithmetic() throws Exception {
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r><xsl:value-of select=\"saxon:query('1+2')\"/></r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">3</r>"), "Expected 1+2=3, got: " + out);
    }

    @Test
    void flwor() throws Exception {
        // FLWOR is XQuery-only; xsl:evaluate / saxon:evaluate (XPath) cannot run this.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r>" +
                "<xsl:value-of select=\"saxon:query('sum(for $i in 1 to 5 return $i * $i)')\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">55</r>"), "Expected 1+4+9+16+25=55, got: " + out);
    }

    @Test
    void contextItemImplicit() throws Exception {
        // No explicit context arg → caller's current context item is used.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r>" +
                "<xsl:value-of select=\"saxon:query('count(//item)')\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<root><item/><item/><item/><item/></root>");
        assertTrue(out.contains(">4</r>"), "Expected 4, got: " + out);
    }

    @Test
    void contextItemExplicit() throws Exception {
        // Explicit context arg overrides caller's context.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r>" +
                "<xsl:variable name='other'><a><b>hi</b></a></xsl:variable>" +
                "<xsl:value-of select=\"saxon:query('string(b)', $other/a)\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<unrelated/>");
        assertTrue(out.contains(">hi</r>"), "Expected hi, got: " + out);
    }

    @Test
    void bindingsMapNoNamespace() throws Exception {
        // Unprefixed key → no-namespace external variable.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r>" +
                "<xsl:value-of select=\"saxon:query(" +
                "  'declare variable $x external; $x * 10', " +
                "  ., " +
                "  map { 'x' : 7 })\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">70</r>"), "Expected 70, got: " + out);
    }

    @Test
    void bindingsMapInheritsNamespace() throws Exception {
        // Prefixed key 'p:n' resolved against the *caller's* namespace
        // context (xmlns:p declared on the stylesheet element).
        String xsl = "<xsl:stylesheet version='3.0' " +
                "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:saxon='http://saxon.sf.net/' " +
                "xmlns:p='http://example.com/p'>" +
                "<xsl:template match='/'><r>" +
                "<xsl:value-of select=\"saxon:query(" +
                "  'declare namespace p = ''http://example.com/p''; " +
                "   declare variable $p:n external; $p:n + 1', " +
                "  ., " +
                "  map { 'p:n' : 41 })\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">42</r>"), "Expected 42, got: " + out);
    }

    @Test
    void bindingsMapEQName() throws Exception {
        // EQName syntax in the key works without any prefix declaration.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r>" +
                "<xsl:value-of select=\"saxon:query(" +
                "  'declare namespace q = ''urn:x''; " +
                "   declare variable $q:v external; string($q:v)', " +
                "  ., " +
                "  map { 'Q{urn:x}v' : 'hello' })\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">hello</r>"), "Expected hello, got: " + out);
    }

    @Test
    void bindingsMapNodeValue() throws Exception {
        // Bind a node-valued external variable.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'><r>" +
                "<xsl:variable name='doc'><items><i>a</i><i>b</i><i>c</i></items></xsl:variable>" +
                "<xsl:value-of select=\"saxon:query(" +
                "  'declare variable $doc external; count($doc//i)', " +
                "  ., " +
                "  map { 'doc' : $doc })\"/>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains(">3</r>"), "Expected 3, got: " + out);
    }

    @Test
    void directConstructor() throws Exception {
        // XQuery direct element constructors — also XQuery-only.
        String xsl = SAXON_HEADER +
                "<xsl:template match='/'>" +
                "<xsl:copy-of select=\"saxon:query('&lt;wrapped&gt;{1+1}&lt;/wrapped&gt;')\"/>" +
                "</xsl:template>" +
                "</xsl:stylesheet>";
        String out = run(xsl, "<x/>");
        assertTrue(out.contains("<wrapped>2</wrapped>"), "Expected wrapped 2, got: " + out);
    }
}
