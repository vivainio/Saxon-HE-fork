package net.sf.saxon.style;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@code saxon:import-query} top-level XSLT declaration.
 */
class SaxonImportQueryTest {

    private static String runWithModule(Path moduleFile, String stylesheet, String xml)
            throws SaxonApiException {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable exec = compiler.compile(
                new StreamSource(new StringReader(stylesheet),
                        moduleFile.getParent().toUri().toString() + "stylesheet.xsl"));
        Xslt30Transformer t = exec.load30();
        StringWriter out = new StringWriter();
        Serializer ser = processor.newSerializer(out);
        t.transform(new StreamSource(new StringReader(xml)), ser);
        return out.toString();
    }

    @Test
    void importsLibraryFunction(@TempDir Path tmp) throws Exception {
        // XQuery library module declaring a public function.
        Path moduleFile = tmp.resolve("greet.xq");
        Files.writeString(moduleFile,
                "module namespace g = \"http://example.com/greet\";\n" +
                        "declare function g:hello($who as xs:string) as xs:string {\n" +
                        "  concat('hello ', $who)\n" +
                        "};\n");

        String xsl = "<xsl:stylesheet version='3.0' " +
                "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:saxon='http://saxon.sf.net/' " +
                "xmlns:g='http://example.com/greet'>" +
                "<saxon:import-query namespace='http://example.com/greet' href='greet.xq'/>" +
                "<xsl:template match='/'><r><xsl:value-of select=\"g:hello('world')\"/></r></xsl:template>" +
                "</xsl:stylesheet>";

        String out = runWithModule(moduleFile, xsl, "<x/>");
        assertTrue(out.contains(">hello world</r>"), "Expected greeting, got: " + out);
    }

    @Test
    void privateFunctionsAreNotImported(@TempDir Path tmp) throws Exception {
        Path moduleFile = tmp.resolve("priv.xq");
        Files.writeString(moduleFile,
                "module namespace p = \"http://example.com/priv\";\n" +
                        "declare %private function p:secret() as xs:integer { 42 };\n" +
                        "declare function p:wrapped() as xs:integer { p:secret() };\n");

        // p:wrapped should work (calls private via internal binding); calling
        // p:secret directly from XSLT should fail at compile time.
        String okXsl = "<xsl:stylesheet version='3.0' " +
                "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:saxon='http://saxon.sf.net/' " +
                "xmlns:p='http://example.com/priv'>" +
                "<saxon:import-query namespace='http://example.com/priv' href='priv.xq'/>" +
                "<xsl:template match='/'><r><xsl:value-of select='p:wrapped()'/></r></xsl:template>" +
                "</xsl:stylesheet>";
        String out = runWithModule(moduleFile, okXsl, "<x/>");
        assertTrue(out.contains(">42</r>"), "Wrapped call should work, got: " + out);

        String badXsl = "<xsl:stylesheet version='3.0' " +
                "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:saxon='http://saxon.sf.net/' " +
                "xmlns:p='http://example.com/priv'>" +
                "<saxon:import-query namespace='http://example.com/priv' href='priv.xq'/>" +
                "<xsl:template match='/'><r><xsl:value-of select='p:secret()'/></r></xsl:template>" +
                "</xsl:stylesheet>";
        assertThrows(SaxonApiException.class,
                () -> runWithModule(moduleFile, badXsl, "<x/>"),
                "Calling a %private function should be a compile error");
    }

    @Test
    void multipleArities(@TempDir Path tmp) throws Exception {
        Path moduleFile = tmp.resolve("math.xq");
        Files.writeString(moduleFile,
                "module namespace m = \"http://example.com/math\";\n" +
                        "declare function m:add($a as xs:integer) as xs:integer { $a + 1 };\n" +
                        "declare function m:add($a as xs:integer, $b as xs:integer) as xs:integer { $a + $b };\n");

        String xsl = "<xsl:stylesheet version='3.0' " +
                "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:saxon='http://saxon.sf.net/' " +
                "xmlns:m='http://example.com/math'>" +
                "<saxon:import-query namespace='http://example.com/math' href='math.xq'/>" +
                "<xsl:template match='/'><r>" +
                "<a><xsl:value-of select='m:add(10)'/></a>" +
                "<b><xsl:value-of select='m:add(10, 20)'/></b>" +
                "</r></xsl:template>" +
                "</xsl:stylesheet>";

        String out = runWithModule(moduleFile, xsl, "<x/>");
        assertTrue(out.contains("<a>11</a>"), "1-arg add, got: " + out);
        assertTrue(out.contains("<b>30</b>"), "2-arg add, got: " + out);
    }

    @Test
    void missingHrefIsCompileError(@TempDir Path tmp) throws Exception {
        String xsl = "<xsl:stylesheet version='3.0' " +
                "xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:saxon='http://saxon.sf.net/'>" +
                "<saxon:import-query namespace='http://example.com/nope'/>" +
                "<xsl:template match='/'><r/></xsl:template>" +
                "</xsl:stylesheet>";
        Path dummy = tmp.resolve("dummy.xq");
        Files.writeString(dummy, "module namespace d = \"d\"; declare function d:f(){()};");
        SaxonApiException ex = assertThrows(SaxonApiException.class,
                () -> runWithModule(dummy, xsl, "<x/>"));
        assertFalse(ex.getMessage() == null);
    }
}
