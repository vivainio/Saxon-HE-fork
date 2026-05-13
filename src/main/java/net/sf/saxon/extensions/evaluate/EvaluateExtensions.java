package net.sf.saxon.extensions.evaluate;

import net.sf.saxon.s9api.Processor;

/**
 * One-stop registration of dynamic XPath-evaluation extension functions.
 *
 * <p>Saxon-HE does not ship {@code saxon:evaluate()} (locked behind a
 * PE/EE license) and does not ship the EXSLT {@code dynamic:evaluate()}
 * function at all. This helper registers both on a {@link Processor}:</p>
 *
 * <ul>
 *   <li>{@code Q{http://exslt.org/dynamic}evaluate} — EXSLT-spec function
 *       for runtime XPath evaluation.</li>
 *   <li>{@code Q{http://saxon.sf.net/}evaluate} — Saxon vendor namespace
 *       function with the same semantics.</li>
 * </ul>
 *
 * <p>Both QNames resolve to a single underlying implementation.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *     Processor processor = new Processor(false);
 *     EvaluateExtensions.registerOn(processor);
 * </pre>
 */
public final class EvaluateExtensions {

    private EvaluateExtensions() {}

    /**
     * Register the dynamic evaluate functions on the given Processor under
     * both the EXSLT dynamic namespace and the Saxon vendor namespace.
     */
    public static void registerOn(Processor processor) {
        processor.getUnderlyingConfiguration().registerExtensionFunction(
                new DynamicEvaluateDefinition(DynamicEvaluateDefinition.EXSLT_DYNAMIC, processor));
        processor.getUnderlyingConfiguration().registerExtensionFunction(
                new DynamicEvaluateDefinition(DynamicEvaluateDefinition.SAXON, processor));
    }
}
