package net.sf.saxon;

import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic smoke tests to verify Saxon functionality.
 */
class SmokeTest {

    @Test
    void processorCanBeCreated() {
        Processor processor = new Processor(false);
        assertNotNull(processor);
    }

    @Test
    void xpathEvaluation() throws SaxonApiException {
        Processor processor = new Processor(false);
        XPathCompiler xpath = processor.newXPathCompiler();

        XdmValue result = xpath.evaluate("1 + 2", null);
        assertEquals(3, ((XdmAtomicValue) result).getLongValue());
    }

    @Test
    void xpathStringFunction() throws SaxonApiException {
        Processor processor = new Processor(false);
        XPathCompiler xpath = processor.newXPathCompiler();

        XdmValue result = xpath.evaluate("upper-case('hello')", null);
        assertEquals("HELLO", result.toString());
    }

    @Test
    void basicXsltTransform() throws SaxonApiException {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();

        String xslt = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <output><xsl:value-of select=\"//name\"/></output>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<root><name>Saxon</name></root>";

        XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
        Xslt30Transformer transformer = executable.load30();

        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);

        transformer.transform(new StreamSource(new StringReader(xml)), serializer);

        assertTrue(output.toString().contains("Saxon"));
    }

    @Test
    void xqueryEvaluation() throws SaxonApiException {
        Processor processor = new Processor(false);
        XQueryCompiler compiler = processor.newXQueryCompiler();

        XQueryExecutable executable = compiler.compile("for $i in 1 to 3 return $i * 2");
        XQueryEvaluator evaluator = executable.load();

        XdmValue result = evaluator.evaluate();
        assertEquals(3, result.size());
        assertEquals(2, ((XdmAtomicValue) result.itemAt(0)).getLongValue());
        assertEquals(4, ((XdmAtomicValue) result.itemAt(1)).getLongValue());
        assertEquals(6, ((XdmAtomicValue) result.itemAt(2)).getLongValue());
    }
}
