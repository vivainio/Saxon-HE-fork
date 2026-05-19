package net.sf.saxon.extensions.xquery;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * Dynamic XQuery evaluation function: compiles its first argument as an
 * XQuery query at runtime and evaluates it.
 *
 * <p>Registered as {@code Q{http://saxon.sf.net/}query}. This mirrors
 * Saxon-PE/EE's {@code saxon:query()} extension function, which is
 * normally absent from Saxon-HE.</p>
 *
 * <p>Arity:</p>
 * <ul>
 *   <li>{@code saxon:query($query as xs:string) as item()*} —
 *       evaluates the query with the caller's current context item
 *       (if any) bound as the query context item.</li>
 *   <li>{@code saxon:query($query as xs:string,
 *                          $context as item()?) as item()*} —
 *       evaluates the query against an explicit context item.</li>
 * </ul>
 *
 * <p>Implementation: delegates to s9api {@link XQueryCompiler} on a
 * Processor captured at registration time. Letting s9api handle
 * compile + type-check + optimize avoids re-implementing those steps
 * from inside the low-level expression-tree API. Same pattern as the
 * sibling {@code DynamicEvaluateDefinition}.</p>
 */
public final class SaxonQueryDefinition extends ExtensionFunctionDefinition {

    /** Saxon vendor namespace. */
    public static final NamespaceUri SAXON =
            NamespaceUri.of("http://saxon.sf.net/");

    private static final StructuredQName NAME =
            new StructuredQName("", SAXON, "query");

    private final Processor processor;

    public SaxonQueryDefinition(Processor processor) {
        this.processor = processor;
    }

    @Override
    public StructuredQName getFunctionQName() {
        return NAME;
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{
                SequenceType.SINGLE_STRING,
                SequenceType.OPTIONAL_ITEM
        };
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
            String queryText = arguments[0].head().getStringValue();
            try {
                XQueryCompiler compiler = processor.newXQueryCompiler();
                XQueryExecutable exec = compiler.compile(queryText);
                XQueryEvaluator evaluator = exec.load();

                XdmItem ctxItem = null;
                if (arguments.length >= 2) {
                    Sequence ctxSeq = arguments[1];
                    if (ctxSeq != null) {
                        net.sf.saxon.om.Item head = ctxSeq.head();
                        if (head != null) {
                            ctxItem = (XdmItem) XdmValue.wrap(head).itemAt(0);
                        }
                    }
                } else if (context.getContextItem() != null) {
                    ctxItem = (XdmItem) XdmValue.wrap(context.getContextItem()).itemAt(0);
                }
                if (ctxItem != null) {
                    evaluator.setContextItem(ctxItem);
                }

                XdmValue result = evaluator.evaluate();
                return SequenceTool.toGroundedValue(result.getUnderlyingValue().iterate());
            } catch (SaxonApiException e) {
                throw new XPathException(
                        "Error in saxon:query(): " + e.getMessage() +
                                ". Query: {" + queryText + "}",
                        "XQDY0027");
            }
        }
    }
}
