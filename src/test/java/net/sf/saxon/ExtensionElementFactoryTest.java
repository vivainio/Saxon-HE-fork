package net.sf.saxon;

import net.sf.saxon.s9api.*;
import net.sf.saxon.style.ExtensionElementFactory;
import net.sf.saxon.style.NoOpExtensionElement;
import net.sf.saxon.style.ExtensionInstruction;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.trans.XPathException;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the extension element factory enhancement.
 * See ENHANCEMENTS.md for documentation.
 */
class ExtensionElementFactoryTest {

    private static final String EXT_NS = "http://example.com/test-extensions";

    @Test
    void noOpExtensionElementIgnoresContent() throws SaxonApiException {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        // Register no-op factory for test namespace
        config.registerExtensionElementFactory(EXT_NS,
            localName -> new NoOpExtensionElement());

        String xslt = "<?xml version=\"1.0\"?>\n"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "    xmlns:ext=\"" + EXT_NS + "\"\n"
            + "    extension-element-prefixes=\"ext\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <output>\n"
            + "      <ext:init param=\"value\"/>\n"
            + "      <xsl:value-of select=\"'hello'\"/>\n"
            + "    </output>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<root/>";

        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
        Xslt30Transformer transformer = executable.load30();

        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.transform(new StreamSource(new StringReader(xml)), serializer);

        String result = output.toString();
        assertTrue(result.contains("hello"), "Transform should produce output");
        assertFalse(result.contains("ext:init"), "Extension element should not appear in output");
    }

    @Test
    void extensionElementWithNestedContent() throws SaxonApiException {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        config.registerExtensionElementFactory(EXT_NS,
            localName -> new NoOpExtensionElement());

        String xslt = "<?xml version=\"1.0\"?>\n"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "    xmlns:ext=\"" + EXT_NS + "\"\n"
            + "    extension-element-prefixes=\"ext\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <output>\n"
            + "      <ext:wrapper>\n"
            + "        <ext:nested>ignored content</ext:nested>\n"
            + "      </ext:wrapper>\n"
            + "      <xsl:text>after</xsl:text>\n"
            + "    </output>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<root/>";

        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
        Xslt30Transformer transformer = executable.load30();

        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.transform(new StreamSource(new StringReader(xml)), serializer);

        String result = output.toString();
        assertTrue(result.contains("after"), "Content after extension should appear");
    }

    @Test
    void multipleExtensionNamespaces() throws SaxonApiException {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        String ns1 = "http://example.com/ext1";
        String ns2 = "http://example.com/ext2";

        config.registerExtensionElementFactory(ns1, localName -> new NoOpExtensionElement());
        config.registerExtensionElementFactory(ns2, localName -> new NoOpExtensionElement());

        String xslt = "<?xml version=\"1.0\"?>\n"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "    xmlns:ext1=\"" + ns1 + "\"\n"
            + "    xmlns:ext2=\"" + ns2 + "\"\n"
            + "    extension-element-prefixes=\"ext1 ext2\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <output>\n"
            + "      <ext1:init/>\n"
            + "      <ext2:setup/>\n"
            + "      <xsl:text>done</xsl:text>\n"
            + "    </output>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<root/>";

        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
        Xslt30Transformer transformer = executable.load30();

        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.transform(new StreamSource(new StringReader(xml)), serializer);

        assertTrue(output.toString().contains("done"));
    }

    @Test
    void unregisteredExtensionElementFails() {
        Processor processor = new Processor(false);
        // Don't register any factory

        String xslt = "<?xml version=\"1.0\"?>\n"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "    xmlns:ext=\"http://unregistered.example.com\"\n"
            + "    extension-element-prefixes=\"ext\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <ext:unknown/>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<root/>";

        XsltCompiler compiler = processor.newXsltCompiler();

        assertThrows(SaxonApiException.class, () -> {
            XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
            Xslt30Transformer transformer = executable.load30();
            transformer.transform(new StreamSource(new StringReader(xml)), processor.newSerializer(new StringWriter()));
        });
    }

    @Test
    void factoryCanReturnDifferentElementsPerLocalName() throws SaxonApiException {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        config.registerExtensionElementFactory(EXT_NS, localName -> {
            // Return NoOp for all elements
            return new NoOpExtensionElement();
        });

        String xslt = "<?xml version=\"1.0\"?>\n"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "    xmlns:ext=\"" + EXT_NS + "\"\n"
            + "    extension-element-prefixes=\"ext\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <output>\n"
            + "      <ext:init/>\n"
            + "      <ext:process/>\n"
            + "      <ext:cleanup/>\n"
            + "      <xsl:text>complete</xsl:text>\n"
            + "    </output>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<root/>";

        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
        Xslt30Transformer transformer = executable.load30();

        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.transform(new StreamSource(new StringReader(xml)), serializer);

        assertTrue(output.toString().contains("complete"));
    }

    @Test
    void isExtensionElementAvailableReturnsTrue() {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        config.registerExtensionElementFactory(EXT_NS,
            localName -> new NoOpExtensionElement());

        assertTrue(config.isExtensionElementAvailable(
            new net.sf.saxon.om.StructuredQName("ext", EXT_NS, "anything")));
    }

    @Test
    void isExtensionElementAvailableReturnsFalseForUnregistered() {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        assertFalse(config.isExtensionElementAvailable(
            new net.sf.saxon.om.StructuredQName("ext", "http://unregistered.example.com", "test")));
    }

    @Test
    void extensionElementWithAttributes() throws SaxonApiException {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();

        config.registerExtensionElementFactory(EXT_NS,
            localName -> new NoOpExtensionElement());

        String xslt = "<?xml version=\"1.0\"?>\n"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
            + "    xmlns:ext=\"" + EXT_NS + "\"\n"
            + "    extension-element-prefixes=\"ext\">\n"
            + "  <xsl:template match=\"/\">\n"
            + "    <output>\n"
            + "      <ext:config name=\"test\" value=\"123\" enabled=\"true\"/>\n"
            + "      <xsl:text>ok</xsl:text>\n"
            + "    </output>\n"
            + "  </xsl:template>\n"
            + "</xsl:stylesheet>";

        String xml = "<root/>";

        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
        Xslt30Transformer transformer = executable.load30();

        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.transform(new StreamSource(new StringReader(xml)), serializer);

        assertTrue(output.toString().contains("ok"));
    }
}
