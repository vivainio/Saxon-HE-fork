package net.sf.saxon.fork.jnode;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.Initializer;
import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrMore;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON structures navigable as generalized nodes (JNode), built directly on
 * {@code net.sf.saxon.ma.jnode.*} — the data structures Saxon-HE 13.0 ships
 * for the draft XPath/XQuery 4.0 "JNode" feature.
 *
 * <p>Copyright © Ville Vainio. Licensed under the Mozilla Public License
 * 2.0, same as the rest of this fork.</p>
 *
 * <p>Fork add-on. Everything in {@code net.sf.saxon.fork.jnode} can be
 * deleted without affecting any other Saxon functionality — no other class
 * in the codebase references it.</p>
 *
 * <p><b>Why this exists:</b> Saxon-HE 13.0 includes the JNode classes
 * ({@code ma.jnode.JNode}/{@code RootJNode}/{@code ChildJNode}, ...) that
 * back XPath 4.0's "maps and arrays are navigable like nodes" feature, but
 * the native {@code /} operator only routes through them when the static
 * XPath language level is 40, and raising the language level to 40 calls
 * {@code Configuration.checkLicensedFeature}, which requires Saxon-PE.
 * That check is Saxonica's actual commercial HE/PE boundary — it is not a
 * bug, and this fork does not patch around it.
 *
 * <p>What <em>isn't</em> gated is the JNode object model itself: its
 * axis-walking methods ({@code getParent()}, {@code iterateChildAxis()},
 * {@code getContent()}, {@code getSelector()}, {@code getPosition()}) are
 * ordinary public methods with no license check anywhere in the call path
 * (verified by inspection — grep the {@code ma.jnode} package for
 * {@code checkLicensedFeature}/{@code LicenseException}: nothing). This
 * class is an independent, function-call-based navigation surface built on
 * top of that object model, in place of the native {@code /} syntax.</p>
 *
 * <p>Functions (all in namespace {@link #JN_NS}, conventional prefix
 * {@code jn}):</p>
 * <ul>
 *   <li>{@code jn:node($value as item()) as item()} — wrap a map or array
 *       as a root JNode.</li>
 *   <li>{@code jn:children($node as item()) as item()*} — child JNodes
 *       (map entries or array members).</li>
 *   <li>{@code jn:parent($node as item()) as item()?} — parent JNode, or
 *       the empty sequence for a root node.</li>
 *   <li>{@code jn:root($node as item()) as item()} — the root JNode of the
 *       tree containing this node.</li>
 *   <li>{@code jn:selector($node as item()) as xs:anyAtomicType?} — the
 *       map key or array index that reached this node from its parent, or
 *       the empty sequence for a root node.</li>
 *   <li>{@code jn:position($node as item()) as xs:integer?} — 1-based
 *       sibling position, or the empty sequence for a root node.</li>
 *   <li>{@code jn:value($node as item()) as item()*} — the JSON value
 *       (map, array, or atomic) wrapped by this node.</li>
 *   <li>{@code jn:has-children($node as item()) as xs:boolean} — true if
 *       the wrapped value is a non-empty map or array.</li>
 *   <li>{@code jn:is-node($item as item()?) as xs:boolean} — true if the
 *       argument is a JNode produced by {@code jn:node()}/{@code jn:children()}
 *       /etc.</li>
 *   <li>{@code jn:key-is($node as item(), $name as xs:string) as xs:boolean}
 *       — type-safe map-key test; {@code false} (not an error) if the
 *       node's selector isn't a string.</li>
 *   <li>{@code jn:index-is($node as item(), $n as xs:integer) as xs:boolean}
 *       — type-safe array-index test; {@code false} (not an error) if the
 *       node's selector isn't an integer.</li>
 *   <li>{@code jn:descendant-or-self($node as item()) as item()*} — this
 *       node, then every descendant, depth-first document order. The
 *       primitive for emulating {@code //name} via
 *       {@code jn:descendant-or-self($n)[jn:key-is(., 'name')]}.</li>
 * </ul>
 *
 * <p>Not implemented (out of scope for this fork's "pragmatic subset"
 * tier): sibling axes, a path-string convenience (e.g. {@code jn:get($n,
 * 'a/b/2')}), and node identity/equality helpers beyond what
 * {@code jn:is-node} gives you. Add another nested class and register it
 * in {@link #register(Configuration)} to extend.</p>
 */
public final class JNodeFunctions {

    private JNodeFunctions() {}

    /** Fork-original vendor namespace — not a Saxonica or spec namespace. */
    public static final NamespaceUri JN_NS =
            NamespaceUri.of("http://github.com/vivainio/saxon-he-fork/jnode");

    private static final NamespaceUri ERR_NS = JN_NS;

    /**
     * Register all jn: functions on the given Configuration.
     */
    public static void register(Configuration config) {
        config.registerExtensionFunction(new NodeFn());
        config.registerExtensionFunction(new Children());
        config.registerExtensionFunction(new Parent());
        config.registerExtensionFunction(new Root());
        config.registerExtensionFunction(new Selector());
        config.registerExtensionFunction(new Position());
        config.registerExtensionFunction(new Value());
        config.registerExtensionFunction(new HasChildren());
        config.registerExtensionFunction(new IsNode());
        config.registerExtensionFunction(new KeyIs());
        config.registerExtensionFunction(new IndexIs());
        config.registerExtensionFunction(new DescendantOrSelf());
    }

    /**
     * Saxon {@link Initializer} that calls {@link #register(Configuration)}.
     * Enable by setting the Saxon config property
     * {@code http://saxon.sf.net/feature/initializer} to
     * {@code net.sf.saxon.fork.jnode.JNodeFunctions$AutoInit}.
     */
    public static final class AutoInit implements Initializer {
        @Override
        public void initialize(Configuration config) throws TransformerException {
            register(config);
        }
    }

    // -- helpers ------------------------------------------------------

    private static JNode asJNode(Sequence s) throws XPathException {
        Item item = s.head();
        if (!(item instanceof JNode)) {
            throw err("not-a-jnode", "Expected a jn:node() result, got: " +
                    (item == null ? "()" : item.getClass().getSimpleName()));
        }
        return (JNode) item;
    }

    private static XPathException err(String localCode, String message) {
        XPathException e = new XPathException(message);
        e.setErrorCodeQName(new StructuredQName("jn", ERR_NS, localCode));
        return e;
    }

    private abstract static class JNodeFn extends ExtensionFunctionDefinition {
        protected abstract String localName();
        protected abstract SequenceType[] args();
        protected abstract SequenceType result();
        protected abstract Sequence eval(Sequence[] args, XPathContext ctx) throws XPathException;

        @Override public final StructuredQName getFunctionQName() {
            return new StructuredQName("jn", JN_NS, localName());
        }
        @Override public final SequenceType[] getArgumentTypes() { return args(); }
        @Override public final SequenceType getResultType(SequenceType[] suppliedArgumentTypes) { return result(); }
        @Override public final boolean hasSideEffects() { return false; }
        @Override public final ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override public Sequence call(XPathContext ctx, Sequence[] a) throws XPathException {
                    return JNodeFn.this.eval(a, ctx);
                }
            };
        }
    }

    private static final class NodeFn extends JNodeFn {
        @Override protected String localName() { return "node"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_ITEM; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Item item = a[0].head();
            if (!(item instanceof MapOrArray)) {
                throw err("not-a-map-or-array", "jn:node() requires a map or array, got: " +
                        (item == null ? "()" : item.getClass().getSimpleName()));
            }
            return RootJNode.obtainRootJNode((MapOrArray) item);
        }
    }

    private static final class Children extends JNodeFn {
        @Override protected String localName() { return "children"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.ANY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            JNode node = asJNode(a[0]);
            return SequenceTool.toGroundedValue(node.iterateChildAxis(null));
        }
    }

    private static final class Parent extends JNodeFn {
        @Override protected String localName() { return "parent"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.OPTIONAL_ITEM; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            JNode parent = asJNode(a[0]).getParent();
            return parent == null ? EmptySequence.getInstance() : parent;
        }
    }

    private static final class Root extends JNodeFn {
        @Override protected String localName() { return "root"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_ITEM; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return asJNode(a[0]).getRoot();
        }
    }

    private static final class Selector extends JNodeFn {
        @Override protected String localName() { return "selector"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.OPTIONAL_ATOMIC; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            AtomicValue selector = asJNode(a[0]).getSelector();
            return selector == null ? EmptySequence.getInstance() : selector;
        }
    }

    private static final class Position extends JNodeFn {
        @Override protected String localName() { return "position"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.OPTIONAL_INTEGER; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            int position = asJNode(a[0]).getPosition();
            return position < 0 ? EmptySequence.getInstance() : Int64Value.makeIntegerValue(position);
        }
    }

    private static final class Value extends JNodeFn {
        @Override protected String localName() { return "value"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.ANY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return asJNode(a[0]).getContent();
        }
    }

    private static final class HasChildren extends JNodeFn {
        @Override protected String localName() { return "has-children"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return BooleanValue.get(asJNode(a[0]).hasChildNodes());
        }
    }

    private static final class IsNode extends JNodeFn {
        @Override protected String localName() { return "is-node"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.OPTIONAL_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return BooleanValue.get(a[0].head() instanceof JNode);
        }
    }

    /**
     * Type-safe map-key test. Plain {@code jn:selector(.) = 'x'} throws
     * ("Cannot compare xs:integer to xs:string") when applied to a mixed
     * sequence of map-entry and array-member nodes; this returns {@code
     * false} instead, so it's safe to use as a filter over
     * {@code jn:descendant-or-self()} or any other mixed node sequence.
     */
    private static final class KeyIs extends JNodeFn {
        @Override protected String localName() { return "key-is"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_ITEM, SequenceType.SINGLE_STRING};
        }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            AtomicValue selector = asJNode(a[0]).getSelector();
            String name = a[1].head().getStringValue();
            return BooleanValue.get(selector instanceof StringValue && selector.getStringValue().equals(name));
        }
    }

    /**
     * Type-safe array-index test — the array-member counterpart of
     * {@link KeyIs}. Compares by canonical string form so it doesn't need
     * to pick between {@code Int64Value}/{@code BigIntegerValue}.
     */
    private static final class IndexIs extends JNodeFn {
        @Override protected String localName() { return "index-is"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_ITEM, SequenceType.SINGLE_INTEGER};
        }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            AtomicValue selector = asJNode(a[0]).getSelector();
            String index = a[1].head().getStringValue();
            return BooleanValue.get(selector instanceof IntegerValue && selector.getStringValue().equals(index));
        }
    }

    /**
     * {@code descendant-or-self::} for JNodes, in document order (this node
     * first, then each child's own descendant-or-self, depth-first) — the
     * primitive needed to emulate {@code //name} via
     * {@code jn:descendant-or-self($n)[jn:key-is(., 'name')]}.
     */
    private static final class DescendantOrSelf extends JNodeFn {
        @Override protected String localName() { return "descendant-or-self"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_ITEM}; }
        @Override protected SequenceType result() { return SequenceType.ANY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            List<Item> nodes = new ArrayList<>();
            collect(asJNode(a[0]), nodes);
            return new ZeroOrMore<>(nodes);
        }

        private void collect(JNode node, List<Item> into) throws XPathException {
            into.add(node);
            for (Item child : SequenceTool.toGroundedValue(node.iterateChildAxis(null)).asIterable()) {
                collect((JNode) child, into);
            }
        }
    }
}
