package net.sf.saxon.extensions.evaluate;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
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
        // Any sequence: the call decides between strict and Xalan-lenient handling.
        return new SequenceType[]{SequenceType.ANY_SEQUENCE};
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
        return new Call(processor, name.getNamespaceUri().equals(EXSLT_DYNAMIC));
    }

    private static final class Call extends ExtensionFunctionCall {
        private final Processor processor;
        private final boolean exslt;
        /** Per call site: true when an EXSLT call is compiled in XSLT 1.0 mode. Strict unless proven legacy. */
        private boolean legacy;

        Call(Processor processor, boolean exslt) {
            this.processor = processor;
            this.exslt = exslt;
        }

        @Override
        public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) {
            legacy = exslt && context.isInBackwardsCompatibleMode();
        }

        // Saxon copies call expressions (inlining, optimisation); the copy must keep the mode.
        @Override
        public void copyLocalData(ExtensionFunctionCall destination) {
            ((Call) destination).legacy = legacy;
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return legacy ? lenient(context, arguments[0]) : strict(context, arguments[0]);
        }

        /** Xalan behaviour: empty, absent or invalid path yields the empty sequence. */
        private Sequence lenient(XPathContext context, Sequence argument) {
            try {
                Item first = argument.head();
                String expr = first == null ? "" : first.getStringValue().trim();
                if (expr.isEmpty()) {
                    return EmptySequence.getInstance();
                }
                return evaluate(context, expr);
            } catch (SaxonApiException | XPathException | RuntimeException e) {
                return EmptySequence.getInstance();
            }
        }

        private Sequence strict(XPathContext context, Sequence argument) throws XPathException {
            GroundedValue value = argument.materialize();
            if (value.getLength() != 1) {
                throw new XPathException("evaluate() requires exactly one path expression, got "
                        + value.getLength(), "XPTY0004");
            }
            String exprText = value.head().getStringValue();
            try {
                return evaluate(context, exprText);
            } catch (SaxonApiException e) {
                throw new XPathException(
                        "Error in dynamic evaluate(): " + e.getMessage() +
                                ". Expression: {" + exprText + "}",
                        "XTDE3160");
            }
        }

        private Sequence evaluate(XPathContext context, String exprText) throws SaxonApiException {
            XPathCompiler compiler = processor.newXPathCompiler();
            XPathSelector selector = compiler.compile(exprText).load();
            if (context.getContextItem() != null) {
                XdmValue wrapped = XdmValue.wrap(context.getContextItem());
                selector.setContextItem((XdmItem) wrapped.itemAt(0));
            }
            XdmValue result = selector.evaluate();
            return SequenceTool.toGroundedValue(result.getUnderlyingValue().iterate());
        }
    }
}
