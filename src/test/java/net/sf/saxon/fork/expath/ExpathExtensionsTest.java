package net.sf.saxon.fork.expath;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test for the EXPath add-on registrar. Verifies the bundled file
 * module registers and its function names resolve at compile time. Does
 * not perform any IO.
 */
class ExpathExtensionsTest {

    @Test
    void fileModuleRegisters() {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();
        int n = ExpathExtensions.registerAll(config);
        assertEquals(1, n, "expected file module to register");
    }

    @Test
    void fileFunctionResolves() throws Exception {
        Processor processor = new Processor(false);
        ExpathExtensions.registerAll(processor.getUnderlyingConfiguration());

        XPathCompiler xp = processor.newXPathCompiler();
        xp.declareNamespace("file", "http://expath.org/ns/file");
        Object result = xp.evaluateSingle(
                "function-lookup(QName('http://expath.org/ns/file', 'exists'), 1)",
                null);
        assertNotNull(result, "file:exists#1 should be registered");
    }
}
