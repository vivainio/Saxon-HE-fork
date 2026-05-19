package net.sf.saxon.fork.expath;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the native EXPath File module via the Saxon XPath runtime,
 * not via direct Java calls. Tests run against a per-test temp dir so they
 * are hermetic.
 */
class ExpathFileFunctionsTest {

    @TempDir
    Path tmp;

    private Processor processor;
    private XPathCompiler xp;

    @BeforeEach
    void setup() {
        processor = new Processor(false);
        ExpathExtensions.registerAll(processor.getUnderlyingConfiguration());
        xp = processor.newXPathCompiler();
        xp.declareNamespace("file", "http://expath.org/ns/file");
    }

    private XdmValue eval(String expr) throws SaxonApiException {
        XPathSelector s = xp.compile(expr).load();
        return s.evaluate();
    }

    private String evalStr(String expr) throws SaxonApiException {
        XdmItem item = eval(expr).itemAt(0);
        return item.getStringValue();
    }

    private boolean evalBool(String expr) throws SaxonApiException {
        return ((XdmAtomicValue) eval(expr).itemAt(0)).getBooleanValue();
    }

    private String p(String name) {
        // Use forward slashes so the literal is portable in XPath
        return tmp.resolve(name).toString().replace('\\', '/');
    }

    @Test
    void writeAndReadText() throws Exception {
        String path = p("hello.txt");
        eval("file:write-text('" + path + "', 'hello, world')");
        assertTrue(Files.exists(tmp.resolve("hello.txt")));
        assertEquals("hello, world", evalStr("file:read-text('" + path + "')"));
        assertEquals(12L, ((XdmAtomicValue) eval("file:size('" + path + "')").itemAt(0)).getLongValue());
    }

    @Test
    void writeTextWithEncoding() throws Exception {
        String path = p("latin.txt");
        eval("file:write-text('" + path + "', 'café', 'ISO-8859-1')");
        // Bytes should be 4 (c, a, f, 0xE9) not 5 (UTF-8 of é = 2 bytes)
        assertEquals(4, Files.size(tmp.resolve("latin.txt")));
        assertEquals("café", evalStr("file:read-text('" + path + "', 'ISO-8859-1')"));
    }

    @Test
    void writeAndReadBinary() throws Exception {
        String path = p("blob.bin");
        // xs:base64Binary literal: "abc" -> YWJj
        eval("file:write-binary('" + path + "', xs:base64Binary('YWJj'))");
        assertEquals(3, Files.size(tmp.resolve("blob.bin")));
        String b64 = evalStr("file:read-binary('" + path + "')");
        assertEquals("YWJj", b64);
    }

    @Test
    void appendText() throws Exception {
        String path = p("log.txt");
        eval("file:write-text('" + path + "', 'one\n')");
        eval("file:append-text('" + path + "', 'two\n')");
        assertEquals("one\ntwo\n", evalStr("file:read-text('" + path + "')"));
    }

    @Test
    void existsAndPredicates() throws Exception {
        Files.writeString(tmp.resolve("f.txt"), "x");
        Files.createDirectory(tmp.resolve("d"));
        assertTrue(evalBool("file:exists('" + p("f.txt") + "')"));
        assertFalse(evalBool("file:exists('" + p("nope.txt") + "')"));
        assertTrue(evalBool("file:is-file('" + p("f.txt") + "')"));
        assertFalse(evalBool("file:is-file('" + p("d") + "')"));
        assertTrue(evalBool("file:is-dir('" + p("d") + "')"));
    }

    @Test
    void listDir() throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "");
        Files.writeString(tmp.resolve("b.txt"), "");
        Files.createDirectory(tmp.resolve("sub"));
        XdmValue names = eval("file:list('" + p(".") + "')");
        assertEquals(3, names.size());
        // Directory entries get a trailing separator per EXPath spec
        boolean hasDir = false;
        for (XdmItem i : names) {
            if (i.getStringValue().equals("sub/")) hasDir = true;
        }
        assertTrue(hasDir, "expected 'sub/' with trailing slash");
    }

    @Test
    void listDirRecursive() throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "");
        Files.createDirectory(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("sub/b.txt"), "");
        XdmValue flat = eval("file:list('" + p(".") + "', false())");
        assertEquals(2, flat.size());
        XdmValue deep = eval("file:list('" + p(".") + "', true())");
        // a.txt, sub/, sub/b.txt
        assertEquals(3, deep.size());
    }

    @Test
    void listDirWithGlob() throws Exception {
        Files.writeString(tmp.resolve("keep.xml"), "");
        Files.writeString(tmp.resolve("skip.txt"), "");
        Files.createDirectory(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("sub/nested.xml"), "");
        XdmValue xmlOnly = eval("file:list('" + p(".") + "', true(), '**.xml')");
        // keep.xml + sub/nested.xml
        assertEquals(2, xmlOnly.size());
        for (XdmItem i : xmlOnly) {
            assertTrue(i.getStringValue().endsWith(".xml"));
        }
    }

    @Test
    void readTextLines() throws Exception {
        String path = p("lines.txt");
        Files.writeString(tmp.resolve("lines.txt"), "alpha\nbeta\ngamma\n");
        XdmValue lines = eval("file:read-text-lines('" + path + "')");
        assertEquals(3, lines.size());
        assertEquals("alpha", lines.itemAt(0).getStringValue());
        assertEquals("beta", lines.itemAt(1).getStringValue());
        assertEquals("gamma", lines.itemAt(2).getStringValue());
    }

    @Test
    void readTextLinesWithEncoding() throws Exception {
        String path = p("latin-lines.txt");
        Files.write(tmp.resolve("latin-lines.txt"),
                "café\nrésumé\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        XdmValue lines = eval("file:read-text-lines('" + path + "', 'ISO-8859-1')");
        assertEquals(2, lines.size());
        assertEquals("café", lines.itemAt(0).getStringValue());
        assertEquals("résumé", lines.itemAt(1).getStringValue());
    }

    @Test
    void createAndDeleteDir() throws Exception {
        eval("file:create-dir('" + p("a/b/c") + "')");
        assertTrue(Files.isDirectory(tmp.resolve("a/b/c")));
        eval("file:delete('" + p("a") + "', true())");
        assertFalse(Files.exists(tmp.resolve("a")));
    }

    @Test
    void copyAndMove() throws Exception {
        Files.writeString(tmp.resolve("src.txt"), "payload");
        eval("file:copy('" + p("src.txt") + "', '" + p("copy.txt") + "')");
        assertEquals("payload", Files.readString(tmp.resolve("copy.txt")));
        eval("file:move('" + p("copy.txt") + "', '" + p("moved.txt") + "')");
        assertFalse(Files.exists(tmp.resolve("copy.txt")));
        assertTrue(Files.exists(tmp.resolve("moved.txt")));
    }

    @Test
    void pathAccessors() throws Exception {
        assertEquals("hello.txt", evalStr("file:name('" + p("hello.txt") + "')"));
        assertNotNull(evalStr("file:parent('" + p("hello.txt") + "')"));
        String uri = evalStr("file:path-to-uri('" + p("x") + "')");
        assertTrue(uri.startsWith("file:"));
    }

    @Test
    void constants() throws Exception {
        assertNotNull(evalStr("file:dir-separator()"));
        assertNotNull(evalStr("file:line-separator()"));
        assertNotNull(evalStr("file:path-separator()"));
        assertNotNull(evalStr("file:temp-dir()"));
        assertNotNull(evalStr("file:current-dir()"));
    }

    @Test
    void notFoundRaises() {
        SaxonApiException ex = assertThrows(SaxonApiException.class,
                () -> eval("file:read-text('" + p("does-not-exist.txt") + "')"));
        assertTrue(ex.getMessage().contains("does-not-exist.txt") || ex.getMessage().contains("not exist"),
                "expected helpful error, got: " + ex.getMessage());
    }

    @Test
    void sandboxAllowsInsideRoot() throws Exception {
        Processor sandboxed = new Processor(false);
        Path root = tmp.toAbsolutePath().normalize();
        Predicate<Path> policy = path -> path.toAbsolutePath().normalize().startsWith(root);
        ExpathFileFunctions.register(sandboxed.getUnderlyingConfiguration(), policy);
        XPathCompiler s = sandboxed.newXPathCompiler();
        s.declareNamespace("file", "http://expath.org/ns/file");

        Files.writeString(tmp.resolve("ok.txt"), "data");
        String out = s.evaluateSingle("file:read-text('" + p("ok.txt") + "')", null).getStringValue();
        assertEquals("data", out);
    }

    @Test
    void sandboxDeniesOutsideRoot() {
        Processor sandboxed = new Processor(false);
        Path root = tmp.toAbsolutePath().normalize();
        Predicate<Path> policy = path -> path.toAbsolutePath().normalize().startsWith(root);
        ExpathFileFunctions.register(sandboxed.getUnderlyingConfiguration(), policy);
        XPathCompiler s = sandboxed.newXPathCompiler();
        s.declareNamespace("file", "http://expath.org/ns/file");

        // Pick a path that's guaranteed outside the temp dir
        String outside = "/etc/passwd";
        SaxonApiException ex = assertThrows(SaxonApiException.class,
                () -> s.evaluateSingle("file:read-text('" + outside + "')", null));
        assertTrue(ex.getMessage().contains("denied"),
                "expected sandbox denial, got: " + ex.getMessage());
    }

    @Test
    void sandboxDeniesWriteOutsideRoot() {
        Processor sandboxed = new Processor(false);
        Path root = tmp.toAbsolutePath().normalize();
        Predicate<Path> policy = path -> path.toAbsolutePath().normalize().startsWith(root);
        ExpathFileFunctions.register(sandboxed.getUnderlyingConfiguration(), policy);
        XPathCompiler s = sandboxed.newXPathCompiler();
        s.declareNamespace("file", "http://expath.org/ns/file");

        SaxonApiException ex = assertThrows(SaxonApiException.class,
                () -> s.evaluateSingle("file:write-text('/tmp/should-not-exist-sandbox-test', 'oops')", null));
        assertTrue(ex.getMessage().contains("denied"),
                "expected sandbox denial on write, got: " + ex.getMessage());
        assertFalse(Files.exists(Path.of("/tmp/should-not-exist-sandbox-test")));
    }
}
