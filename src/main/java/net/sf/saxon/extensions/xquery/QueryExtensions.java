package net.sf.saxon.extensions.xquery;

import net.sf.saxon.s9api.Processor;

/**
 * One-stop registration of dynamic XQuery extension functions.
 *
 * <p>Saxon-HE does not ship {@code saxon:query()} — it is locked behind
 * a PE/EE license. Saxon-HE does however include full XQuery support
 * via s9api ({@link net.sf.saxon.s9api.XQueryCompiler}), so the
 * extension function is just a thin wrapper around it.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *     Processor processor = new Processor(false);
 *     QueryExtensions.registerOn(processor);
 *     // Stylesheets that call saxon:query(...) now compile and run.
 * </pre>
 */
public final class QueryExtensions {

    private QueryExtensions() {}

    /**
     * Register {@code saxon:query()} on the given Processor.
     */
    public static void registerOn(Processor processor) {
        processor.getUnderlyingConfiguration().registerExtensionFunction(
                new SaxonQueryDefinition(processor));
    }
}
