package net.sf.saxon.fork.expath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrMore;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.HexBinaryValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal native implementation of the EXPath Binary module
 * ({@code http://expath.org/ns/binary}) for Saxon HE.
 *
 * <p>Copyright © Ville Vainio. Licensed under the Mozilla Public License
 * 2.0, same as the rest of this fork. Cleanroom implementation written
 * from the public EXPath Binary specification.</p>
 *
 * <p>Fork add-on, best-effort quality. Self-contained — depends only on the
 * JDK and Saxon's public extension-function API. Spec reference:
 * https://expath.org/spec/binary</p>
 *
 * <p>Functions take and return {@code xs:base64Binary} for binary data
 * throughout, matching the spec. Integer offsets/lengths are zero-based
 * (also spec-conformant). Error reporting uses the
 * {@code http://expath.org/ns/error} namespace with prefix {@code bin}.</p>
 *
 * <p>Pairs naturally with {@link ExpathFileFunctions#register} — read bytes
 * with {@code file:read-binary}, slice/munge with {@code bin:*}, write
 * back with {@code file:write-binary}.</p>
 */
public final class ExpathBinaryFunctions {

    private ExpathBinaryFunctions() {}

    private static final NamespaceUri BIN_NS = NamespaceUri.of("http://expath.org/ns/binary");
    private static final NamespaceUri ERR_NS = NamespaceUri.of("http://expath.org/ns/error");

    public static void register(Configuration config) {
        config.registerExtensionFunction(new Length());
        config.registerExtensionFunction(new Part());
        config.registerExtensionFunction(new Join());
        config.registerExtensionFunction(new Hex());
        config.registerExtensionFunction(new Bin());
        config.registerExtensionFunction(new Octal());
        config.registerExtensionFunction(new ToOctets());
        config.registerExtensionFunction(new FromOctets());
        config.registerExtensionFunction(new And());
        config.registerExtensionFunction(new Or());
        config.registerExtensionFunction(new Xor());
        config.registerExtensionFunction(new Not());
        config.registerExtensionFunction(new Shift());
        config.registerExtensionFunction(new PackInteger());
        config.registerExtensionFunction(new UnpackInteger());
        config.registerExtensionFunction(new DecodeString());
        config.registerExtensionFunction(new EncodeString());
        config.registerExtensionFunction(new PadLeft());
        config.registerExtensionFunction(new PadRight());
        config.registerExtensionFunction(new Find());
        config.registerExtensionFunction(new Hex2());
        config.registerExtensionFunction(new Bin2());
    }

    // -- helpers ----------------------------------------------------------

    private static XPathException err(String localCode, String message) {
        XPathException e = new XPathException(message);
        e.setErrorCodeQName(new StructuredQName("bin", ERR_NS, localCode));
        return e;
    }

    private static byte[] bytes(Sequence s) throws XPathException {
        Item h = s.head();
        if (h == null) throw err("differing-length-arguments", "Expected base64Binary, got empty");
        if (h instanceof Base64BinaryValue) return ((Base64BinaryValue) h).getBinaryValue();
        if (h instanceof HexBinaryValue) return ((HexBinaryValue) h).getBinaryValue();
        throw err("input-type-error", "Expected base64Binary/hexBinary, got " + h.getClass().getSimpleName());
    }

    private static int intArg(Sequence s) throws XPathException {
        long v = ((IntegerValue) s.head()).longValue();
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw err("index-out-of-range", "Integer argument out of int range: " + v);
        }
        return (int) v;
    }

    private static Charset charset(String name) throws XPathException {
        if (name == null) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            throw err("unknown-encoding", "Unknown encoding: " + name);
        }
    }

    private static Sequence b64(byte[] b) { return new Base64BinaryValue(b); }

    // -- function-definition scaffolding ----------------------------------

    private abstract static class BinFn extends ExtensionFunctionDefinition {

        protected abstract String localName();
        protected abstract SequenceType[] args();
        protected abstract SequenceType result();
        protected int minArgs() { return args().length; }
        protected abstract Sequence eval(Sequence[] a, XPathContext c) throws XPathException;

        @Override public final StructuredQName getFunctionQName() {
            return new StructuredQName("bin", BIN_NS, localName());
        }
        @Override public final SequenceType[] getArgumentTypes() { return args(); }
        @Override public final SequenceType getResultType(SequenceType[] s) { return result(); }
        @Override public final int getMinimumNumberOfArguments() { return minArgs(); }
        @Override public final int getMaximumNumberOfArguments() { return args().length; }
        @Override public boolean hasSideEffects() { return false; }
        @Override public final ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override public Sequence call(XPathContext ctx, Sequence[] a) throws XPathException {
                    return BinFn.this.eval(a, ctx);
                }
            };
        }
    }

    // -- core -------------------------------------------------------------

    private static final class Length extends BinFn {
        @Override protected String localName() { return "length"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_INTEGER; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            return Int64Value.makeIntegerValue(bytes(a[0]).length);
        }
    }

    /** {@code bin:part($in, $offset, $size?)} — slice. */
    private static final class Part extends BinFn {
        @Override protected String localName() { return "part"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.SINGLE_INTEGER,
                    SequenceType.SINGLE_INTEGER};
        }
        @Override protected int minArgs() { return 2; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            int off = intArg(a[1]);
            int size = a.length > 2 ? intArg(a[2]) : in.length - off;
            if (off < 0 || size < 0 || off + size > in.length) {
                throw err("index-out-of-range", "Slice [" + off + "," + size + ") out of range for length " + in.length);
            }
            byte[] out = new byte[size];
            System.arraycopy(in, off, out, 0, size);
            return b64(out);
        }
    }

    private static final class Join extends BinFn {
        @Override protected String localName() { return "join"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.zeroOrMore()}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            int total = 0;
            List<byte[]> parts = new ArrayList<>();
            Sequence seq = a[0];
            net.sf.saxon.om.SequenceIterator it = seq.iterate();
            Item item;
            while ((item = it.next()) != null) {
                byte[] b;
                if (item instanceof Base64BinaryValue) b = ((Base64BinaryValue) item).getBinaryValue();
                else if (item instanceof HexBinaryValue) b = ((HexBinaryValue) item).getBinaryValue();
                else throw err("input-type-error", "Expected base64Binary item, got " + item.getClass().getSimpleName());
                parts.add(b);
                total += b.length;
            }
            byte[] out = new byte[total];
            int o = 0;
            for (byte[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
            return b64(out);
        }
    }

    // -- constructors / inspectors ---------------------------------------

    /** {@code bin:hex($in as xs:string?)} — parse hex string. */
    private static final class Hex extends BinFn {
        @Override protected String localName() { return "hex"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.OPTIONAL_STRING}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.zeroOrOne(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Item h = a[0].head();
            if (h == null) return EmptySequence.getInstance();
            String s = h.getStringValue().trim().replaceAll("\\s+", "");
            if ((s.length() & 1) != 0) s = "0" + s;
            try {
                byte[] out = new byte[s.length() / 2];
                for (int i = 0; i < out.length; i++) {
                    out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
                }
                return b64(out);
            } catch (NumberFormatException e) {
                throw err("non-numeric-character", "Invalid hex: " + s);
            }
        }
    }

    /** {@code bin:bin($in as xs:string?)} — parse binary string ("0101..."). */
    private static final class Bin extends BinFn {
        @Override protected String localName() { return "bin"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.OPTIONAL_STRING}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.zeroOrOne(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Item h = a[0].head();
            if (h == null) return EmptySequence.getInstance();
            String s = h.getStringValue().trim().replaceAll("\\s+", "");
            if (s.isEmpty()) return b64(new byte[0]);
            int pad = (8 - (s.length() % 8)) % 8;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pad; i++) sb.append('0');
            sb.append(s);
            String padded = sb.toString();
            byte[] out = new byte[padded.length() / 8];
            for (int i = 0; i < out.length; i++) {
                try {
                    out[i] = (byte) Integer.parseInt(padded.substring(i * 8, i * 8 + 8), 2);
                } catch (NumberFormatException e) {
                    throw err("non-numeric-character", "Invalid binary digit in: " + s);
                }
            }
            return b64(out);
        }
    }

    /** {@code bin:octal($in as xs:string?)} — parse octal string. */
    private static final class Octal extends BinFn {
        @Override protected String localName() { return "octal"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.OPTIONAL_STRING}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.zeroOrOne(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Item h = a[0].head();
            if (h == null) return EmptySequence.getInstance();
            String s = h.getStringValue().trim().replaceAll("\\s+", "");
            if (s.isEmpty()) return b64(new byte[0]);
            try {
                BigInteger n = new BigInteger(s, 8);
                byte[] raw = n.toByteArray();
                // strip leading sign byte if zero-padded
                if (raw.length > 1 && raw[0] == 0) {
                    byte[] trimmed = new byte[raw.length - 1];
                    System.arraycopy(raw, 1, trimmed, 0, trimmed.length);
                    return b64(trimmed);
                }
                return b64(raw);
            } catch (NumberFormatException e) {
                throw err("non-numeric-character", "Invalid octal: " + s);
            }
        }
    }

    /** {@code bin:to-octets($in)} — base64Binary → integer sequence (unsigned). */
    private static final class ToOctets extends BinFn {
        @Override protected String localName() { return "to-octets"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return SequenceType.INTEGER_SEQUENCE; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            List<IntegerValue> out = new ArrayList<>(in.length);
            for (byte b : in) out.add(Int64Value.makeIntegerValue(b & 0xff));
            return new ZeroOrMore<>(out);
        }
    }

    /** {@code bin:from-octets($in)} — integer sequence → base64Binary. */
    private static final class FromOctets extends BinFn {
        @Override protected String localName() { return "from-octets"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{SequenceType.INTEGER_SEQUENCE}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            List<Byte> tmp = new ArrayList<>();
            net.sf.saxon.om.SequenceIterator it = a[0].iterate();
            Item item;
            while ((item = it.next()) != null) {
                long v = ((IntegerValue) item).longValue();
                if (v < 0 || v > 255) throw err("octet-out-of-range", "Octet out of range [0,255]: " + v);
                tmp.add((byte) v);
            }
            byte[] out = new byte[tmp.size()];
            for (int i = 0; i < out.length; i++) out[i] = tmp.get(i);
            return b64(out);
        }
    }

    // -- bitwise ----------------------------------------------------------

    private abstract static class BitOp extends BinFn {
        @Override protected SequenceType[] args() {
            return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one(), BuiltInAtomicType.BASE64_BINARY.one()};
        }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        protected abstract byte op(byte x, byte y);
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] x = bytes(a[0]);
            byte[] y = bytes(a[1]);
            if (x.length != y.length) {
                throw err("differing-length-arguments", "bitwise ops require equal-length operands (" + x.length + " vs " + y.length + ")");
            }
            byte[] out = new byte[x.length];
            for (int i = 0; i < x.length; i++) out[i] = op(x[i], y[i]);
            return b64(out);
        }
    }
    private static final class And extends BitOp {
        @Override protected String localName() { return "and"; }
        @Override protected byte op(byte x, byte y) { return (byte) (x & y); }
    }
    private static final class Or extends BitOp {
        @Override protected String localName() { return "or"; }
        @Override protected byte op(byte x, byte y) { return (byte) (x | y); }
    }
    private static final class Xor extends BitOp {
        @Override protected String localName() { return "xor"; }
        @Override protected byte op(byte x, byte y) { return (byte) (x ^ y); }
    }
    private static final class Not extends BinFn {
        @Override protected String localName() { return "not"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            byte[] out = new byte[in.length];
            for (int i = 0; i < in.length; i++) out[i] = (byte) ~in[i];
            return b64(out);
        }
    }

    /** {@code bin:shift($in, $by)} — left shift by $by bits (negative = right). */
    private static final class Shift extends BinFn {
        @Override protected String localName() { return "shift"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one(), SequenceType.SINGLE_INTEGER};
        }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            int by = intArg(a[1]);
            if (in.length == 0 || by == 0) return b64(in.clone());
            int totalBits = in.length * 8;
            byte[] out = new byte[in.length];
            for (int i = 0; i < totalBits; i++) {
                int src = i + by;
                if (src < 0 || src >= totalBits) continue;
                int bit = (in[src / 8] >> (7 - (src % 8))) & 1;
                out[i / 8] |= (byte) (bit << (7 - (i % 8)));
            }
            return b64(out);
        }
    }

    // -- pack / unpack ----------------------------------------------------

    /** {@code bin:pack-integer($n, $size, $octet-order?)} — big-endian by default. */
    private static final class PackInteger extends BinFn {
        @Override protected String localName() { return "pack-integer"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    SequenceType.SINGLE_INTEGER, SequenceType.SINGLE_INTEGER, SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 2; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            BigInteger n = ((IntegerValue) a[0].head()).asBigInteger();
            int size = intArg(a[1]);
            if (size < 0) throw err("negative-size", "Size must be non-negative: " + size);
            boolean little = a.length > 2 && "least-significant-first".equals(a[2].head().getStringValue());
            byte[] out = new byte[size];
            BigInteger mod = BigInteger.ONE.shiftLeft(size * 8);
            BigInteger v = n.signum() < 0 ? n.add(mod) : n;
            for (int i = size - 1; i >= 0; i--) {
                int b = v.and(BigInteger.valueOf(0xff)).intValueExact();
                v = v.shiftRight(8);
                int idx = little ? (size - 1 - i) : i;
                out[idx] = (byte) b;
            }
            return b64(out);
        }
    }

    /** {@code bin:unpack-integer($in, $offset, $size, $octet-order?)} — signed, big-endian default. */
    private static final class UnpackInteger extends BinFn {
        @Override protected String localName() { return "unpack-integer"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.SINGLE_INTEGER,
                    SequenceType.SINGLE_INTEGER,
                    SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 3; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_INTEGER; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            int off = intArg(a[1]);
            int size = intArg(a[2]);
            boolean little = a.length > 3 && "least-significant-first".equals(a[3].head().getStringValue());
            if (off < 0 || size < 0 || off + size > in.length) {
                throw err("index-out-of-range", "Slice out of range");
            }
            if (size == 0) return Int64Value.makeIntegerValue(0);
            byte[] be;
            if (little) {
                be = new byte[size];
                for (int i = 0; i < size; i++) be[i] = in[off + size - 1 - i];
            } else {
                be = new byte[size];
                System.arraycopy(in, off, be, 0, size);
            }
            return IntegerValue.makeIntegerValue(new BigInteger(be));
        }
    }

    // -- string conversion -----------------------------------------------

    private static final class DecodeString extends BinFn {
        @Override protected String localName() { return "decode-string"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.zeroOrOne(),
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_INTEGER,
                    SequenceType.SINGLE_INTEGER};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return SequenceType.OPTIONAL_STRING; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Item h = a[0].head();
            if (h == null) return EmptySequence.getInstance();
            byte[] in;
            if (h instanceof Base64BinaryValue) in = ((Base64BinaryValue) h).getBinaryValue();
            else if (h instanceof HexBinaryValue) in = ((HexBinaryValue) h).getBinaryValue();
            else throw err("input-type-error",
                    "Expected base64Binary/hexBinary, got " + h.getClass().getSimpleName());
            Charset cs = charset(a.length > 1 ? a[1].head().getStringValue() : null);
            int off = a.length > 2 ? intArg(a[2]) : 0;
            int size = a.length > 3 ? intArg(a[3]) : in.length - off;
            if (off < 0 || size < 0 || off + size > in.length) {
                throw err("index-out-of-range", "Slice out of range");
            }
            return new StringValue(new String(in, off, size, cs));
        }
    }

    private static final class EncodeString extends BinFn {
        @Override protected String localName() { return "encode-string"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{SequenceType.OPTIONAL_STRING, SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 1; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.zeroOrOne(); }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            Item h = a[0].head();
            if (h == null) return EmptySequence.getInstance();
            Charset cs = charset(a.length > 1 ? a[1].head().getStringValue() : null);
            return b64(h.getStringValue().getBytes(cs));
        }
    }

    // -- padding ----------------------------------------------------------

    private abstract static class Pad extends BinFn {
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.SINGLE_INTEGER,
                    SequenceType.SINGLE_INTEGER};
        }
        @Override protected int minArgs() { return 2; }
        @Override protected SequenceType result() { return BuiltInAtomicType.BASE64_BINARY.one(); }
        protected abstract boolean left();
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            int size = intArg(a[1]);
            if (size < 0) throw err("negative-size", "Negative pad: " + size);
            byte octet = (byte) (a.length > 2 ? (intArg(a[2]) & 0xff) : 0);
            byte[] out = new byte[in.length + size];
            if (left()) {
                java.util.Arrays.fill(out, 0, size, octet);
                System.arraycopy(in, 0, out, size, in.length);
            } else {
                System.arraycopy(in, 0, out, 0, in.length);
                java.util.Arrays.fill(out, in.length, in.length + size, octet);
            }
            return b64(out);
        }
    }
    private static final class PadLeft extends Pad {
        @Override protected String localName() { return "pad-left"; }
        @Override protected boolean left() { return true; }
    }
    private static final class PadRight extends Pad {
        @Override protected String localName() { return "pad-right"; }
        @Override protected boolean left() { return false; }
    }

    // -- find -------------------------------------------------------------

    /** {@code bin:find($in, $offset, $search)} — returns byte offset of first match, or empty. */
    private static final class Find extends BinFn {
        @Override protected String localName() { return "find"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    BuiltInAtomicType.BASE64_BINARY.one(),
                    SequenceType.SINGLE_INTEGER,
                    BuiltInAtomicType.BASE64_BINARY.one()};
        }
        @Override protected SequenceType result() { return SequenceType.OPTIONAL_INTEGER; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            int off = intArg(a[1]);
            byte[] needle = bytes(a[2]);
            if (off < 0 || off > in.length) throw err("index-out-of-range", "offset out of range");
            if (needle.length == 0) return Int64Value.makeIntegerValue(off);
            outer:
            for (int i = off; i <= in.length - needle.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (in[i + j] != needle[j]) continue outer;
                }
                return Int64Value.makeIntegerValue(i);
            }
            return EmptySequence.getInstance();
        }
    }

    // -- hex/bin two-arity (with separator) -------------------------------
    // (kept minimal — these are no-arg-friendly extras, not in the spec)

    /** Convenience: {@code bin:hex($in as xs:base64Binary)} → hex string. Not in the spec — extension. */
    private static final class Hex2 extends BinFn {
        @Override protected String localName() { return "hex-string"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            StringBuilder sb = new StringBuilder(in.length * 2);
            for (byte b : in) sb.append(String.format("%02x", b & 0xff));
            return new StringValue(sb.toString());
        }
    }

    /** Convenience: {@code bin:bin-string($in)} → binary digit string. Not in the spec — extension. */
    private static final class Bin2 extends BinFn {
        @Override protected String localName() { return "bin-string"; }
        @Override protected SequenceType[] args() { return new SequenceType[]{BuiltInAtomicType.BASE64_BINARY.one()}; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_STRING; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] in = bytes(a[0]);
            StringBuilder sb = new StringBuilder(in.length * 8);
            for (byte b : in) {
                for (int i = 7; i >= 0; i--) sb.append(((b >> i) & 1) == 1 ? '1' : '0');
            }
            return new StringValue(sb.toString());
        }
    }
}
