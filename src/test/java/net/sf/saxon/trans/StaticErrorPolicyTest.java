package net.sf.saxon.trans;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link StaticErrorPolicy}. See ENHANCEMENTS.md. */
class StaticErrorPolicyTest {

    private static String sheet(String version, String test) {
        return sheet(version, test, "true()");
    }

    /** The bad {@code xsl:if} is only evaluated when {@code guard} is true. */
    private static String sheet(String version, String test, String guard) {
        return "<xsl:stylesheet version='" + version + "' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                "<xsl:template match='/'><r><xsl:if test=\"" + guard + "\"><xsl:if test=\"" + test +
                "\">reached</xsl:if></xsl:if>ok</r></xsl:template></xsl:stylesheet>";
    }

    private static final String BAD_UNION = "normalize-space(/x) | /x";

    private static Processor processor(boolean install) {
        Processor p = new Processor(false);
        if (install) {
            StaticErrorPolicy.set(p.getUnderlyingConfiguration(), (err, sc) ->
                    "XPTY0004".equals(err.getErrorCodeQName().getLocalPart()) && sc.isInBackwardsCompatibleMode());
        }
        return p;
    }

    private static XsltExecutable compile(Processor p, String xsl) throws SaxonApiException {
        return p.newXsltCompiler().compile(new StreamSource(new StringReader(xsl)));
    }

    private static String run(Processor p, XsltExecutable exec, String xml) throws SaxonApiException {
        StringWriter out = new StringWriter();
        exec.load30().transform(new StreamSource(new StringReader(xml)), p.newSerializer(out));
        return out.toString();
    }

    @Test
    void defaultPolicyStillFailsCompilation() {
        Processor p = processor(false);
        assertThrows(SaxonApiException.class, () -> compile(p, sheet("1.0", BAD_UNION)));
    }

    @Test
    void legacyModeDefersErrorNotReached() throws Exception {
        Processor p = processor(true);
        XsltExecutable exec = compile(p, sheet("1.0", BAD_UNION, "false()"));
        assertTrue(run(p, exec, "<x/>").contains("ok"));
    }

    @Test
    void deferredErrorStillFailsWhenReached() throws Exception {
        Processor p = processor(true);
        XsltExecutable exec = compile(p, sheet("1.0", BAD_UNION));
        assertThrows(SaxonApiException.class, () -> run(p, exec, "<x/>"));
    }

    @Test
    void modernVersionStaysStrict() {
        Processor p = processor(true);
        assertThrows(SaxonApiException.class, () -> compile(p, sheet("3.0", BAD_UNION)));
    }

    @Test
    void clearingPolicyRestoresStrictCompilation() {
        Processor p = processor(true);
        StaticErrorPolicy.set(p.getUnderlyingConfiguration(), null);
        assertThrows(SaxonApiException.class, () -> compile(p, sheet("1.0", BAD_UNION)));
    }

    @Test
    void policyOnlyDefersErrorsItSelects() {
        Processor p = new Processor(false);
        StaticErrorPolicy.set(p.getUnderlyingConfiguration(), (err, sc) -> "FOO0001".equals(err.getErrorCodeQName().getLocalPart()));
        assertThrows(SaxonApiException.class, () -> compile(p, sheet("1.0", BAD_UNION)));
    }
}
