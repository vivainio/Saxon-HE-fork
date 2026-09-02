package net.sf.saxon.fork.jnode;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JNodeFunctionsTest {

    private Processor processor;
    private XPathCompiler xp;

    @BeforeEach
    void setup() {
        processor = new Processor(false);
        JNodeFunctions.register(processor.getUnderlyingConfiguration());
        xp = processor.newXPathCompiler();
        xp.declareNamespace("jn", JNodeFunctions.JN_NS.toString());
    }

    private XdmValue eval(String expr) throws SaxonApiException {
        return xp.compile(expr).load().evaluate();
    }

    private String evalStr(String expr) throws SaxonApiException {
        return eval(expr).itemAt(0).getStringValue();
    }

    private static final String JSON = "{\"a\":1,\"b\":[10,20,30],\"c\":{\"d\":\"hi\"}}";

    @Test
    void nodeWrapsMap() throws Exception {
        assertEquals("true", evalStr(
                "jn:is-node(jn:node(parse-json('" + JSON + "')))"));
    }

    @Test
    void nodeRejectsNonMapOrArray() {
        SaxonApiException e = assertThrows(SaxonApiException.class,
                () -> eval("jn:node(42)"));
        assertTrue(e.getMessage().contains("map or array"), e.getMessage());
    }

    @Test
    void rootHasNoParentSelectorOrPosition() throws Exception {
        assertEquals("true true true", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')) " +
                        "return string-join((" +
                        "  empty(jn:parent($root))," +
                        "  empty(jn:selector($root))," +
                        "  empty(jn:position($root))" +
                        ") ! string(.), ' ')"));
    }

    @Test
    void rootHasChildrenReflectsContent() throws Exception {
        assertEquals("true", evalStr(
                "jn:has-children(jn:node(parse-json('" + JSON + "')))"));
        assertEquals("false", evalStr(
                "jn:has-children(jn:node(parse-json('[]')))"));
    }

    @Test
    void mapEntrySelectorIsTheKey() throws Exception {
        assertEquals("b", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')) " +
                        "return jn:selector(jn:children($root)[jn:selector(.) = 'b'])"));
    }

    @Test
    void arrayMemberSelectorIsThe1BasedIndex() throws Exception {
        assertEquals("2 20", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $b := jn:children($root)[jn:selector(.) = 'b'], " +
                        "    $b2 := jn:children($b)[jn:selector(.) = 2] " +
                        "return jn:selector($b2) || ' ' || jn:value($b2)"));
    }

    @Test
    void valueOfArrayEntryIsTheArrayItself() throws Exception {
        assertEquals("true", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $b := jn:children($root)[jn:selector(.) = 'b'] " +
                        "return jn:value($b) instance of array(*)"));
    }

    @Test
    void parentAndRootNavigateBack() throws Exception {
        assertEquals("true true", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $b := jn:children($root)[jn:selector(.) = 'b'], " +
                        "    $b2 := jn:children($b)[jn:selector(.) = 2] " +
                        "return string-join((" +
                        "  jn:value(jn:parent($b2)) instance of array(*)," +
                        "  jn:root($b2) is $root" +
                        ") ! string(.), ' ')"));
    }

    @Test
    void childrenOfLeafValueIsEmpty() throws Exception {
        assertEquals("0", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $a := jn:children($root)[jn:selector(.) = 'a'] " +
                        "return count(jn:children($a))"));
    }

    @Test
    void isNodeDistinguishesJNodesFromOrdinaryItems() throws Exception {
        assertEquals("false", evalStr("jn:is-node(42)"));
        assertEquals("false", evalStr("jn:is-node(())"));
        assertEquals("true", evalStr("jn:is-node(jn:node(parse-json('" + JSON + "')))"));
    }

    @Test
    void nestedObjectRoundTrips() throws Exception {
        assertEquals("hi", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $c := jn:children($root)[jn:selector(.) = 'c'], " +
                        "    $d := jn:children($c)[jn:selector(.) = 'd'] " +
                        "return jn:value($d)"));
    }

    @Test
    void keyIsMatchesMapEntrySelector() throws Exception {
        assertEquals("hi", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $c := jn:children($root)[jn:key-is(., 'c')], " +
                        "    $d := jn:children($c)[jn:key-is(., 'd')] " +
                        "return jn:value($d)"));
    }

    @Test
    void keyIsIsFalseNotAnErrorOnArrayMember() throws Exception {
        // jn:selector(.) = 'x' would throw here (integer vs string);
        // jn:key-is must not.
        assertEquals("false", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $b1 := jn:children(jn:children($root)[jn:key-is(., 'b')])[1] " +
                        "return jn:key-is($b1, 'anything')"));
    }

    @Test
    void indexIsMatchesArrayMemberSelector() throws Exception {
        assertEquals("20", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $b  := jn:children($root)[jn:key-is(., 'b')], " +
                        "    $b2 := jn:children($b)[jn:index-is(., 2)] " +
                        "return jn:value($b2)"));
    }

    @Test
    void indexIsIsFalseNotAnErrorOnMapEntry() throws Exception {
        assertEquals("false", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')), " +
                        "    $c := jn:children($root)[jn:key-is(., 'c')] " +
                        "return jn:index-is($c, 1)"));
    }

    @Test
    void descendantOrSelfIncludesSelfAndAllDescendants() throws Exception {
        // root + a + b + b[1] + b[2] + b[3] + c + c/d = 8
        assertEquals("8", evalStr(
                "count(jn:descendant-or-self(jn:node(parse-json('" + JSON + "'))))"));
    }

    @Test
    void descendantOrSelfEmulatesDoubleSlashSearch() throws Exception {
        // //d, Balisage-paper style: descendant-or-self + key filter
        assertEquals("hi", evalStr(
                "let $root := jn:node(parse-json('" + JSON + "')) " +
                        "return jn:value(jn:descendant-or-self($root)[jn:key-is(., 'd')])"));
    }
}
