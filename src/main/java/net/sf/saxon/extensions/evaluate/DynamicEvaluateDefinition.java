package net.sf.saxon.extensions.evaluate;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * Dynamic XPath evaluation function: compiles its first argument as an
 * XPath expression at runtime and evaluates it against the calling
 * expression's current context item.
 *
 * <p>Registered under two function QNames:</p>
 * <ul>
 *   <li>{@code Q{http://exslt.org/dynamic}evaluate} — defined by EXSLT.</li>
 *   <li>{@code Q{http://saxon.sf.net/}evaluate} — Saxon vendor equivalent
 *       (normally Saxon-PE/EE only).</li>
 * </ul>
 *
 * <p>Both delegate to the same implementation. The function takes a single
 * {@code xs:string} argument and returns {@code item()*}.</p>
 *
 * <p>Unlike the XSLT 3.0 {@code xsl:evaluate} instruction, this function
 * form does not support {@code with-param} variable injection,
 * schema-awareness, or an explicit context-item operand, and it does not
 * inherit the caller's static namespace context. For those, use
 * {@code xsl:evaluate} directly (already supported by this fork).</p>
 *
 * <p>Implementation: delegates to {@link XPathCompiler} on a Processor
 * captured at registration time. Letting s9api handle compile + type-check
 * + optimize avoids re-implementing those steps from inside the low-level
 * expression-tree API.</p>
 */
public final class DynamicEvaluateDefinition extends ExtensionFunctionDefinition {

    /** EXSLT dynamic namespace. Used by Xalan-J era stylesheets. */
    public static final NamespaceUri EXSLT_DYNAMIC =
            NamespaceUri.of("http://exslt.org/dynamic");

    /** Saxon vendor namespace. Used by Saxon-PE/EE era stylesheets. */
    public static final NamespaceUri SAXON =
            NamespaceUri.of("http://saxon.sf.net/");

    private final StructuredQName name;
    private final Processor processor;

    public DynamicEvaluateDefinition(NamespaceUri namespace, Processor processor) {
        this.name = new StructuredQName("", namespace, "evaluate");
        this.processor = processor;
    }

    @Override
    public StructuredQName getFunctionQName() {
        return name;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_STRING};
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ANY_SEQUENCE;
    }

    @Override
    public boolean dependsOnFocus() {
        return true;
    }

    @Override
    public boolean hasSideEffects() {
        return false;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new Call(processor);
    }

    private static final class Call extends ExtensionFunctionCall {
        private final Processor processor;

        Call(Processor processor) {
            this.processor = processor;
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            String exprText = arguments[0].head().getStringValue();
            try {
                XPathCompiler compiler = processor.newXPathCompiler();
                XPathExecutable exec = compiler.compile(exprText);
                XPathSelector selector = exec.load();
                if (context.getContextItem() != null) {
                    XdmValue wrapped = XdmValue.wrap(context.getContextItem());
                    selector.setContextItem((XdmItem) wrapped.itemAt(0));
                }
                XdmValue result = selector.evaluate();
                return SequenceTool.toGroundedValue(result.getUnderlyingValue().iterate());
            } catch (SaxonApiException e) {
                throw new XPathException(
                        "Error in dynamic evaluate(): " + e.getMessage() +
                                ". Expression: {" + exprText + "}",
                        "XTDE3160");
            }
        }
    }
}
