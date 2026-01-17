package saxx;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

/**
 * A mock extension function that returns a configured value.
 * Used during deep checks to mock out Xalan and other processor-specific extensions.
 */
public class MockExtensionFunction extends ExtensionFunctionDefinition {
    private final StructuredQName name;
    private final Object returnValue;
    private final int minArity;
    private final int maxArity;

    public MockExtensionFunction(String namespaceUri, String localName, Object returnValue) {
        this(namespaceUri, localName, returnValue, 0, 20);
    }

    public MockExtensionFunction(String namespaceUri, String localName, Object returnValue, int minArity, int maxArity) {
        this.name = new StructuredQName("", namespaceUri, localName);
        this.returnValue = returnValue;
        this.minArity = minArity;
        this.maxArity = maxArity;
    }

    @Override
    public StructuredQName getFunctionQName() {
        return name;
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return minArity;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return maxArity;
    }

    @Override
    public net.sf.saxon.value.SequenceType[] getArgumentTypes() {
        // Accept any arguments
        net.sf.saxon.value.SequenceType[] types = new net.sf.saxon.value.SequenceType[maxArity];
        for (int i = 0; i < maxArity; i++) {
            types[i] = net.sf.saxon.value.SequenceType.ANY_SEQUENCE;
        }
        return types;
    }

    @Override
    public net.sf.saxon.value.SequenceType getResultType(net.sf.saxon.value.SequenceType[] suppliedArgumentTypes) {
        return net.sf.saxon.value.SequenceType.ANY_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            @Override
            public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
                if (returnValue == null) {
                    return EmptySequence.getInstance();
                } else if (returnValue instanceof Boolean) {
                    return BooleanValue.get((Boolean) returnValue);
                } else if (returnValue instanceof Number) {
                    if (returnValue instanceof Double || returnValue instanceof Float) {
                        return new DoubleValue(((Number) returnValue).doubleValue());
                    } else {
                        return Int64Value.makeIntegerValue(((Number) returnValue).longValue());
                    }
                } else {
                    return new StringValue(returnValue.toString());
                }
            }
        };
    }
}
