package net.sf.saxon.fork.expath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrMore;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Minimal native implementation of the EXPath File module
 * ({@code http://expath.org/ns/file}) for Saxon HE.
 *
 * <p>Copyright © Ville Vainio. Licensed under the Mozilla Public License
 * 2.0, same as the rest of this fork. Cleanroom implementation written
 * from the public EXPath File specification — not derived from
 * {@code fgeorges/expath-file-java}, BaseX, eXist-db, or any other
 * existing EXPath file-module codebase.</p>
 *
 * Fork add-on, best-effort quality. Self-contained — depends only on
 * {@code java.nio.file} and Saxon's public extension-function API. No
 * third-party jars. Spec reference: https://expath.org/spec/file
 *
 * Implements a pragmatic subset of the spec: the functions that
 * scripting-style XSLT/XQuery workflows actually reach for. Not implemented
 * (out of scope for this fork's "best-effort" tier):
 * <ul>
 *   <li>Atomic write / line-by-line readers ({@code read-text-lines},
 *       fine-grained encoding error reporting).</li>
 *   <li>{@code list} with glob filter argument (3-arity) and recursive flag.</li>
 *   <li>Locking, {@code base-dir} resolution against the XQuery/XSLT static
 *       base URI (we use the JVM working directory instead).</li>
 * </ul>
 * To extend, add another {@code FileFn} nested class and register it in
 * {@link #register(Configuration)}.
 *
 * Error reporting: every IO failure becomes an {@link XPathException} with a
 * QName in the EXPath errors namespace ({@code http://expath.org/ns/error}).
 * The codes mirror the spec ({@code file:not-found}, {@code file:io-error},
 * etc.) so XQuery {@code try/catch} can discriminate.
 */
public final class ExpathFileFunctions {

    private ExpathFileFunctions() {}

    private static final NamespaceUri FILE_NS = NamespaceUri.of("http://expath.org/ns/file");
    private static final NamespaceUri ERR_NS = NamespaceUri.of("http://expath.org/ns/error");

    /** Predicate that allows every path. Default policy for {@link #register(Configuration)}. */
    public static final Predicate<Path> ALLOW_ALL = p -> true;

    /**
     * Register all file functions with no sandbox restriction — every path
     * the JVM can reach is fair game. Equivalent to
     * {@code register(config, ALLOW_ALL)}.
     *
     * <p><b>Security:</b> do not call this in contexts that run untrusted
     * stylesheets. Use the {@link #register(Configuration, Predicate)}
     * overload with a path allowlist instead.</p>
     */
    public static void register(Configuration config) {
        register(config, ALLOW_ALL);
    }

    /**
     * Register all file functions, consulting {@code sandbox} before every
     * filesystem access. Each path (after parsing but before any IO) is
     * passed to {@code sandbox.test(...)}; if the predicate returns
     * {@code false}, a {@code file:access-denied} XPathException is raised.
     *
     * <p>Operations with two paths ({@code copy}, {@code move}) check both.
     * Constant-style accessors ({@code dir-separator}, {@code temp-dir},
     * etc.) are unaffected — they don't take path arguments.</p>
     *
     * <p>Typical usage: confine writes to a known root.</p>
     * <pre>
     *   Path root = Paths.get("/srv/data").toAbsolutePath().normalize();
     *   ExpathFileFunctions.register(config,
     *           p -&gt; p.toAbsolutePath().normalize().startsWith(root));
     * </pre>
     */
    public static void register(Configuration config, Predicate<Path> sandbox) {
        Predicate<Path> policy = sandbox == null ? ALLOW_ALL : sandbox;
        install(config, new Exists(), policy);
        install(config, new IsDir(), policy);
        install(config, new IsFile(), policy);
        install(config, new Size(), policy);
        install(config, new LastModified(), policy);
        install(config, new ReadText(), policy);
        install(config, new ReadTextLines(), policy);
        install(config, new ReadBinary(), policy);
        install(config, new WriteText(), policy);
        install(config, new WriteBinary(), policy);
        install(config, new AppendText(), policy);
        install(config, new ListDir(), policy);
        install(config, new CreateDir(), policy);
        install(config, new Delete(), policy);
        install(config, new Copy(), policy);
        install(config, new Move(), policy);
        install(config, new Name(), policy);
        install(config, new Parent(), policy);
        install(config, new ResolvePath(), policy);
        install(config, new PathToUri(), policy);
        config.registerExtensionFunction(new DirSeparator());
        config.registerExtensionFunction(new LineSeparator());
        config.registerExtensionFunction(new PathSeparator());
        config.registerExtensionFunction(new TempDir());
        config.registerExtensionFunction(new CurrentDir());
    }

    private static void install(Configuration config, FileFn fn, Predicate<Path> policy) {
        fn.policy = policy;
        config.registerExtensionFunction(fn);
    }

    // -- Path / error helpers ---------------------------------------------

    private static Path toPath(String s) throws XPathException {
        try {
            if (s.startsWith("file:")) return Paths.get(new URI(s));
            return Paths.get(s);
        } catch (URISyntaxException | InvalidPathException e) {
            throw err("io-error", "Invalid path: " + s);
        }
    }

    private static XPathException err(String localCode, String message) {
        XPathException e = new XPathException(message);
        e.setErrorCodeQName(new StructuredQName("file", ERR_NS, localCode));
        return e;
    }

    private static XPathException ioErr(String path, IOException e) {
        if (e instanceof NoSuchFileException) {
            return err("not-found", "File does not exist: " + path);
        }
        if (e instanceof FileAlreadyExistsException) {
            return err("exists", "File already exists: " + path);
        }
        if (e instanceof DirectoryNotEmptyException) {
            return err("is-dir", "Directory not empty: " + path);
        }
        return err("io-error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private static Charset charset(String name) throws XPathException {
        if (name == null) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            // covers UnsupportedCharsetException + IllegalCharsetNameException
            throw err("unknown-encoding", "Unknown encoding: " + name);
        }
    }

    // -- Function-definition scaffolding ----------------------------------

    /**
     * Base class boiling the EFD/EFC contract down to a single
     * {@code eval(...)} method per function.
     */
    private abstract static class FileFn extends ExtensionFunctionDefinition {

        Predicate<Path> policy = ALLOW_ALL;

        /** Parse + sandbox-check a path argument. Use this in {@code eval}. */
        protected Path path(String s) throws XPathException {
            Path p = toPath(s);
            if (!policy.test(p)) {
                throw err("access-denied", "Path denied by sandbox policy: " + s);
            }
            return p;
        }

        protected abstract String localName();
        protected abstract SequenceType[] args();
        protected abstract SequenceType result();
        protected int minArgs() { return args().length; }
        protected abstract Sequence eval(Sequence[] args, XPathContext ctx) throws XPathException;

        /**
         * Override to {@code false} for pure introspection functions
         * (separators, current-dir, etc.) so Saxon may constant-fold them.
         */
        protected boolean sideEffects() { return true; }

        @Override public final StructuredQName getFunctionQName() {
            return new StructuredQName("file", FILE_NS, localName());
        }
        @Override public final SequenceType[] getArgumentTypes() { return args(); }
        @Override public final SequenceType getResultType(SequenceType[] s) { return result(); }
        @Override public final int getMinimumNumberOfArguments() { return minArgs(); }
        @Override public final int getMaximumNumberOfArguments() { return args().length; }
        @Override public final boolean hasSideEffects() { return sideEffects(); }
        @Override public final ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override public Sequence call(XPathContext ctx, Sequence[] a) throws XPathException {
                    return FileFn.this.eval(a, ctx);
                }
            };
        }

        protected static String str(Sequence s) throws XPathException {
            return s.head().getStringValue();
        }
        protected static String strOrNull(Sequence[] args, int i) throws XPathException {
            if (i >= args.length || args[i] == null) return null;
            return args[i].head() == null ? null : args[i].head().getStringValue();
        }
    }

    // -- Predicates / metadata --------------------------------------------

    private static final class Exists extends FileFn {
        @Override protected String localName() { return "exists"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected boolean sideEffects() { return true; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return BooleanValue.get(Files.exists(path(str(a[0]))));
        }
    }

    private static final class IsDir extends FileFn {
        @Override protected String localName() { return "is-dir"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return BooleanValue.get(Files.isDirectory(path(str(a[0]))));
        }
    }

    private static final class IsFile extends FileFn {
        @Override protected String localName() { return "is-file"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_BOOLEAN; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return BooleanValue.get(Files.isRegularFile(path(str(a[0]))));
        }
    }

    private static final class Size extends FileFn {
        @Override protected String localName() { return "size"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_INTEGER; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            try { return Int64Value.makeIntegerValue(Files.size(p)); }
            catch (IOException e) { throw ioErr(p.toString(), e); }
        }
    }

    private static final class LastModified extends FileFn {
        @Override protected String localName() { return "last-modified"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.DATE_TIME.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            try {
                Instant inst = Files.getLastModifiedTime(p).toInstant();
                return DateTimeValue.fromJavaInstant(inst);
            } catch (IOException e) { throw ioErr(p.toString(), e); }
        }
    }

    // -- Reads -------------------------------------------------------------

    private static final class ReadText extends FileFn {
        @Override protected String localName() { return "read-text"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            Charset cs = charset(strOrNull(a, 1));
            try { return new StringValue(Files.readString(p, cs)); }
            catch (IOException e) { throw ioErr(p.toString(), e); }
        }
    }

    private static final class ReadTextLines extends FileFn {
        @Override protected String localName() { return "read-text-lines"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return SequenceType.STRING_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            Charset cs = charset(strOrNull(a, 1));
            try {
                List<StringValue> lines = new ArrayList<>();
                for (String line : Files.readAllLines(p, cs)) lines.add(new StringValue(line));
                return new ZeroOrMore<>(lines);
            } catch (IOException e) { throw ioErr(p.toString(), e); }
        }
    }

    private static final class ReadBinary extends FileFn {
        @Override protected String localName() { return "read-binary"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            try { return new Base64BinaryValue(Files.readAllBytes(p)); }
            catch (IOException e) { throw ioErr(p.toString(), e); }
        }
    }

    // -- Writes ------------------------------------------------------------

    private static final class WriteText extends FileFn {
        @Override protected String localName() { return "write-text"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 2; }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            String value = str(a[1]);
            Charset cs = charset(strOrNull(a, 2));
            try {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                Files.writeString(p, value, cs);
            } catch (IOException e) { throw ioErr(p.toString(), e); }
            return EmptySequence.getInstance();
        }
    }

    private static final class WriteBinary extends FileFn {
        @Override protected String localName() { return "write-binary"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_STRING, BuiltInAtomicType.BASE64_BINARY.one()};
        }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            byte[] bytes = ((Base64BinaryValue) a[1].head()).getBinaryValue();
            try {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                Files.write(p, bytes);
            } catch (IOException e) { throw ioErr(p.toString(), e); }
            return EmptySequence.getInstance();
        }
    }

    private static final class AppendText extends FileFn {
        @Override protected String localName() { return "append-text"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 2; }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            String value = str(a[1]);
            Charset cs = charset(strOrNull(a, 2));
            try {
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                Files.writeString(p, value, cs,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) { throw ioErr(p.toString(), e); }
            return EmptySequence.getInstance();
        }
    }

    // -- Directory operations ---------------------------------------------

    private static final class ListDir extends FileFn {
        @Override protected String localName() { return "list"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_BOOLEAN,
                    SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return SequenceType.STRING_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path dir = path(str(a[0]));
            if (!Files.isDirectory(dir)) {
                throw err("no-dir", "Not a directory: " + dir);
            }
            boolean recursive = a.length > 1 && ((BooleanValue) a[1].head()).getBooleanValue();
            PathMatcher matcher = null;
            if (a.length > 2) {
                matcher = dir.getFileSystem().getPathMatcher("glob:" + str(a[2]));
            }
            List<StringValue> names = new ArrayList<>();
            String sep = dir.getFileSystem().getSeparator();
            try (Stream<Path> walk = recursive ? Files.walk(dir) : Files.list(dir)) {
                final PathMatcher fMatcher = matcher;
                walk.forEach(child -> {
                    if (child.equals(dir)) return;
                    Path rel = dir.relativize(child);
                    if (fMatcher != null && !fMatcher.matches(rel)) return;
                    String name = rel.toString();
                    if (Files.isDirectory(child)) name += sep;
                    names.add(new StringValue(name));
                });
            } catch (IOException e) { throw ioErr(dir.toString(), e); }
            return new ZeroOrMore<>(names);
        }
    }

    private static final class CreateDir extends FileFn {
        @Override protected String localName() { return "create-dir"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            try { Files.createDirectories(p); }
            catch (IOException e) { throw ioErr(p.toString(), e); }
            return EmptySequence.getInstance();
        }
    }

    private static final class Delete extends FileFn {
        @Override protected String localName() { return "delete"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_BOOLEAN};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            boolean recursive = a.length > 1 && ((BooleanValue) a[1].head()).getBooleanValue();
            try {
                if (recursive && Files.isDirectory(p)) {
                    Files.walk(p)
                            .sorted(Comparator.reverseOrder())
                            .forEach(child -> {
                                try { Files.delete(child); }
                                catch (IOException ignored) { /* best-effort */ }
                            });
                } else {
                    Files.delete(p);
                }
            } catch (IOException e) { throw ioErr(p.toString(), e); }
            return EmptySequence.getInstance();
        }
    }

    private static final class Copy extends FileFn {
        @Override protected String localName() { return "copy"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path src = path(str(a[0]));
            Path dst = path(str(a[1]));
            try {
                if (Files.isDirectory(src)) {
                    copyDirRecursive(src, dst);
                } else {
                    if (dst.getParent() != null) Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) { throw ioErr(src.toString(), e); }
            return EmptySequence.getInstance();
        }

        private static void copyDirRecursive(Path src, Path dst) throws IOException {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, dst.resolve(src.relativize(file).toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static final class Move extends FileFn {
        @Override protected String localName() { return "move"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected SequenceType result() { return SequenceType.EMPTY_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path src = path(str(a[0]));
            Path dst = path(str(a[1]));
            try {
                if (dst.getParent() != null) Files.createDirectories(dst.getParent());
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { throw ioErr(src.toString(), e); }
            return EmptySequence.getInstance();
        }
    }

    // -- Path manipulation ------------------------------------------------

    private static final class Name extends FileFn {
        @Override protected String localName() { return "name"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected boolean sideEffects() { return false; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0]));
            Path n = p.getFileName();
            return new StringValue(n == null ? "" : n.toString());
        }
    }

    private static final class Parent extends FileFn {
        @Override protected String localName() { return "parent"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.OPTIONAL_STRING; }
        @Override protected boolean sideEffects() { return false; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Path p = path(str(a[0])).toAbsolutePath().normalize();
            Path parent = p.getParent();
            return parent == null
                    ? EmptySequence.getInstance()
                    : new StringValue(parent.toString() + p.getFileSystem().getSeparator());
        }
    }

    private static final class ResolvePath extends FileFn {
        @Override protected String localName() { return "resolve-path"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected boolean sideEffects() { return false; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return new StringValue(path(str(a[0])).toAbsolutePath().normalize().toString());
        }
    }

    private static final class PathToUri extends FileFn {
        @Override protected String localName() { return "path-to-uri"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.SINGLE_STRING}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.ANY_URI.one(); }
        @Override protected boolean sideEffects() { return false; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return new AnyURIValue(path(str(a[0])).toAbsolutePath().toUri().toString());
        }
    }

    // -- Constants --------------------------------------------------------

    private abstract static class StringConst extends FileFn {
        @Override protected SequenceType[] args() { return new SequenceType[0]; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected boolean sideEffects() { return false; }
    }

    private static final class DirSeparator extends StringConst {
        @Override protected String localName() { return "dir-separator"; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) {
            return new StringValue(java.io.File.separator);
        }
    }

    private static final class LineSeparator extends StringConst {
        @Override protected String localName() { return "line-separator"; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) {
            return new StringValue(System.lineSeparator());
        }
    }

    private static final class PathSeparator extends StringConst {
        @Override protected String localName() { return "path-separator"; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) {
            return new StringValue(java.io.File.pathSeparator);
        }
    }

    private static final class TempDir extends StringConst {
        @Override protected String localName() { return "temp-dir"; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) {
            return new StringValue(System.getProperty("java.io.tmpdir"));
        }
    }

    private static final class CurrentDir extends StringConst {
        @Override protected String localName() { return "current-dir"; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) {
            return new StringValue(System.getProperty("user.dir"));
        }
    }
}
