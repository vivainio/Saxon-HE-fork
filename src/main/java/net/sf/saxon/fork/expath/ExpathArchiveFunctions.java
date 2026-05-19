package net.sf.saxon.fork.expath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrMore;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.HexBinaryValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Native EXPath Archive module ({@code http://expath.org/ns/archive}) for
 * Saxon HE — ZIP-only subset.
 *
 * <p>Copyright © Ville Vainio. Licensed under the Mozilla Public License
 * 2.0, same as the rest of this fork. Cleanroom implementation written
 * from the public EXPath Archive specification, backed by
 * {@code java.util.zip}.</p>
 *
 * <p>Fork add-on, best-effort quality. Self-contained — depends only on
 * the JDK and Saxon's public extension-function API. Spec reference:
 * https://expath.org/spec/archive</p>
 *
 * <h3>Scope vs. spec</h3>
 * <ul>
 *   <li>ZIP format only. {@code arch:options} reports {@code zip}.</li>
 *   <li>Entries are passed/returned as plain {@code xs:string} sequences
 *       (entry path / name). The spec allows
 *       {@code <archive:entry>...</archive:entry>} elements with metadata
 *       attributes; this implementation accepts the strings only —
 *       sufficient for the common create / extract / delete workflows.
 *       If an element item is given as an entry name, its string-value is
 *       used as the path.</li>
 *   <li>{@code arch:create}, {@code arch:entries}, {@code arch:extract-text},
 *       {@code arch:extract-binary}, {@code arch:delete},
 *       {@code arch:update}, {@code arch:options} are implemented.</li>
 * </ul>
 *
 * <p>Pairs naturally with {@link ExpathFileFunctions} — read an archive
 * with {@code file:read-binary}, hand it to {@code arch:entries}; write
 * back with {@code file:write-binary}.</p>
 */
public final class ExpathArchiveFunctions {

    private ExpathArchiveFunctions() {}

    private static final NamespaceUri ARCH_NS = NamespaceUri.of("http://expath.org/ns/archive");
    private static final NamespaceUri ERR_NS = NamespaceUri.of("http://expath.org/ns/error");

    public static void register(Configuration config) {
        config.registerExtensionFunction(new Create());
        config.registerExtensionFunction(new Entries());
        config.registerExtensionFunction(new ExtractText());
        config.registerExtensionFunction(new ExtractBinary());
        config.registerExtensionFunction(new Delete());
        config.registerExtensionFunction(new Update());
        config.registerExtensionFunction(new Options());
    }

    // -- helpers ----------------------------------------------------------

    private static XPathException err(String localCode, String message) {
        XPathException e = new XPathException(message);
        e.setErrorCodeQName(new StructuredQName("arch", ERR_NS, localCode));
        return e;
    }

    private static byte[] archiveBytes(Sequence s) throws XPathException {
        Item h = s.head();
        if (h == null) throw err("missing-input", "Archive input is empty");
        if (h instanceof Base64BinaryValue) return ((Base64BinaryValue) h).getBinaryValue();
        if (h instanceof HexBinaryValue) return ((HexBinaryValue) h).getBinaryValue();
        throw err("input-type-error",
                "Archive input must be base64Binary/hexBinary, got " + h.getClass().getSimpleName());
    }

    private static List<String> stringList(Sequence s) throws XPathException {
        List<String> out = new ArrayList<>();
        SequenceIterator it = s.iterate();
        Item item;
        while ((item = it.next()) != null) out.add(item.getStringValue());
        return out;
    }

    private static List<byte[]> contentList(Sequence s) throws XPathException {
        List<byte[]> out = new ArrayList<>();
        SequenceIterator it = s.iterate();
        Item item;
        while ((item = it.next()) != null) {
            if (item instanceof Base64BinaryValue) out.add(((Base64BinaryValue) item).getBinaryValue());
            else if (item instanceof HexBinaryValue) out.add(((HexBinaryValue) item).getBinaryValue());
            else out.add(item.getStringValue().getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private static Charset charset(String name) throws XPathException {
        if (name == null) return StandardCharsets.UTF_8;
        try { return Charset.forName(name); }
        catch (IllegalArgumentException e) { throw err("unknown-encoding", "Unknown encoding: " + name); }
    }

    // -- scaffolding ------------------------------------------------------

    private abstract static class ArchFn extends ExtensionFunctionDefinition {
        protected abstract String localName();
        protected abstract SequenceType[] args();
        protected abstract SequenceType result();
        protected int minArgs() { return args().length; }
        protected abstract Sequence eval(Sequence[] a, XPathContext c) throws XPathException;

        @Override public final StructuredQName getFunctionQName() {
            return new StructuredQName("arch", ARCH_NS, localName());
        }
        @Override public final SequenceType[] getArgumentTypes() { return args(); }
        @Override public final SequenceType getResultType(SequenceType[] s) { return result(); }
        @Override public final int getMinimumNumberOfArguments() { return minArgs(); }
        @Override public final int getMaximumNumberOfArguments() { return args().length; }
        @Override public boolean hasSideEffects() { return false; }
        @Override public final ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override public Sequence call(XPathContext ctx, Sequence[] a) throws XPathException {
                    return ArchFn.this.eval(a, ctx);
                }
            };
        }
    }

    // -- create -----------------------------------------------------------

    private static final class Create extends ArchFn {
        @Override protected String localName() { return "create"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE};
        }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            List<String> entries = stringList(a[0]);
            List<byte[]> contents = contentList(a[1]);
            if (entries.size() != contents.size()) {
                throw err("different-length",
                        "entries and contents must have the same length: " + entries.size() + " vs " + contents.size());
            }
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(bos)) {
                    for (int i = 0; i < entries.size(); i++) {
                        ZipEntry ze = new ZipEntry(entries.get(i));
                        zos.putNextEntry(ze);
                        zos.write(contents.get(i));
                        zos.closeEntry();
                    }
                }
                return new Base64BinaryValue(bos.toByteArray());
            } catch (IOException e) {
                throw err("io-error", e.getMessage());
            }
        }
    }

    // -- entries ----------------------------------------------------------

    private static final class Entries extends ArchFn {
        @Override protected String localName() { return "entries"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return SequenceType.STRING_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] zip = archiveBytes(a[0]);
            List<StringValue> out = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    out.add(new StringValue(ze.getName()));
                    zis.closeEntry();
                }
            } catch (IOException e) {
                throw err("io-error", e.getMessage());
            }
            return new ZeroOrMore<>(out);
        }
    }

    // -- extract-text -----------------------------------------------------

    private static final class ExtractText extends ArchFn {
        @Override protected String localName() { return "extract-text"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.STRING_SEQUENCE,
                    SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return SequenceType.STRING_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] zip = archiveBytes(a[0]);
            Set<String> filter = null;
            if (a.length > 1) {
                List<String> names = stringList(a[1]);
                if (!names.isEmpty()) filter = new HashSet<>(names);
            }
            Charset cs = charset(a.length > 2 ? a[2].head().getStringValue() : null);
            List<StringValue> out = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.isDirectory()) { zis.closeEntry(); continue; }
                    if (filter == null || filter.contains(ze.getName())) {
                        out.add(new StringValue(new String(zis.readAllBytes(), cs)));
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                throw err("io-error", e.getMessage());
            }
            return new ZeroOrMore<>(out);
        }
    }

    // -- extract-binary ---------------------------------------------------

    private static final class ExtractBinary extends ArchFn {
        @Override protected String localName() { return "extract-binary"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.STRING_SEQUENCE};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.zeroOrMore(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] zip = archiveBytes(a[0]);
            Set<String> filter = null;
            if (a.length > 1) {
                List<String> names = stringList(a[1]);
                if (!names.isEmpty()) filter = new HashSet<>(names);
            }
            List<Base64BinaryValue> out = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (ze.isDirectory()) { zis.closeEntry(); continue; }
                    if (filter == null || filter.contains(ze.getName())) {
                        out.add(new Base64BinaryValue(zis.readAllBytes()));
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                throw err("io-error", e.getMessage());
            }
            return new ZeroOrMore<>(out);
        }
    }

    // -- delete -----------------------------------------------------------

    private static final class Delete extends ArchFn {
        @Override protected String localName() { return "delete"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one(), SequenceType.STRING_SEQUENCE};
        }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] zip = archiveBytes(a[0]);
            Set<String> drop = new HashSet<>(stringList(a[1]));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip));
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (drop.contains(ze.getName())) { zis.closeEntry(); continue; }
                    ZipEntry copy = new ZipEntry(ze.getName());
                    zos.putNextEntry(copy);
                    zis.transferTo(zos);
                    zos.closeEntry();
                    zis.closeEntry();
                }
            } catch (IOException e) {
                throw err("io-error", e.getMessage());
            }
            return new Base64BinaryValue(bos.toByteArray());
        }
    }

    // -- update -----------------------------------------------------------

    private static final class Update extends ArchFn {
        @Override protected String localName() { return "update"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.STRING_SEQUENCE,
                    SequenceType.ANY_SEQUENCE};
        }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] zip = archiveBytes(a[0]);
            List<String> names = stringList(a[1]);
            List<byte[]> contents = contentList(a[2]);
            if (names.size() != contents.size()) {
                throw err("different-length",
                        "entries and contents must have the same length: " + names.size() + " vs " + contents.size());
            }
            Map<String, byte[]> overrides = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) overrides.put(names.get(i), contents.get(i));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Set<String> written = new HashSet<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip));
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    String name = ze.getName();
                    ZipEntry copy = new ZipEntry(name);
                    zos.putNextEntry(copy);
                    if (overrides.containsKey(name)) {
                        zos.write(overrides.get(name));
                    } else {
                        zis.transferTo(zos);
                    }
                    zos.closeEntry();
                    zis.closeEntry();
                    written.add(name);
                }
                // Append new entries that weren't already in the archive
                for (Map.Entry<String, byte[]> e : overrides.entrySet()) {
                    if (written.contains(e.getKey())) continue;
                    zos.putNextEntry(new ZipEntry(e.getKey()));
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            } catch (IOException e) {
                throw err("io-error", e.getMessage());
            }
            return new Base64BinaryValue(bos.toByteArray());
        }
    }

    // -- options ----------------------------------------------------------

    /** Returns the archive format. Always {@code "zip"} for this implementation. */
    private static final class Options extends ArchFn {
        @Override protected String localName() { return "options"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] zip = archiveBytes(a[0]);
            if (zip.length >= 2 && zip[0] == 'P' && zip[1] == 'K') return new StringValue("zip");
            throw err("unknown-format", "Not a recognized archive (ZIP magic missing)");
        }
    }
}
