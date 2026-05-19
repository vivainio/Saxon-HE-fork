package net.sf.saxon.fork.expath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.HexBinaryValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Native EXPath Cryptographic hash + HMAC functions for Saxon HE.
 *
 * <p>Copyright © Ville Vainio. Licensed under the Mozilla Public License
 * 2.0, same as the rest of this fork. Cleanroom implementation.</p>
 *
 * <p>Namespace: {@code http://expath.org/ns/crypto} (conventional prefix
 * {@code crypto:}). This implements the EXPath Cryptographic module's
 * {@code crypto:hmac} function plus a {@code crypto:hash} convenience
 * function. The XML-DSig (sign/validate) and encryption parts of the spec
 * are not implemented — they would pull in much larger surface area.</p>
 *
 * <p>Spec reference: https://expath.org/spec/crypto</p>
 *
 * <h3>Functions</h3>
 * <ul>
 *   <li>{@code crypto:hash($data, $algorithm, $encoding?)} — digest of the
 *       input. {@code $data} may be {@code xs:string} (UTF-8 bytes) or
 *       {@code xs:base64Binary} / {@code xs:hexBinary}. {@code $algorithm} is
 *       one of {@code MD5}, {@code SHA-1}, {@code SHA-256}, {@code SHA-384},
 *       {@code SHA-512}. {@code $encoding} is {@code base64} (default),
 *       {@code hex}, or {@code raw} (returns {@code xs:base64Binary}).</li>
 *   <li>{@code crypto:hmac($data, $key, $algorithm, $encoding?)} — HMAC.
 *       Algorithm is the JCE algorithm name ({@code HmacMD5},
 *       {@code HmacSHA1}, {@code HmacSHA256}, {@code HmacSHA384},
 *       {@code HmacSHA512}); the bare digest name ({@code SHA-256}) is also
 *       accepted as a convenience. Encoding semantics match
 *       {@code crypto:hash}.</li>
 * </ul>
 *
 * <p>All inputs and outputs follow EXPath spec polymorphism: strings are
 * UTF-8-encoded; binary types are used as-is.</p>
 */
public final class ExpathCryptoFunctions {

    private ExpathCryptoFunctions() {}

    private static final NamespaceUri CRYPTO_NS = NamespaceUri.of("http://expath.org/ns/crypto");
    private static final NamespaceUri ERR_NS = NamespaceUri.of("http://expath.org/ns/error");

    public static void register(Configuration config) {
        config.registerExtensionFunction(new Hash());
        config.registerExtensionFunction(new Hmac());
    }

    // -- helpers ----------------------------------------------------------

    private static XPathException err(String localCode, String message) {
        XPathException e = new XPathException(message);
        e.setErrorCodeQName(new StructuredQName("crypto", ERR_NS, localCode));
        return e;
    }

    private static byte[] bytesOrUtf8(Sequence s) throws XPathException {
        Item h = s.head();
        if (h == null) throw err("missing-input", "Required input is empty");
        if (h instanceof Base64BinaryValue) return ((Base64BinaryValue) h).getBinaryValue();
        if (h instanceof HexBinaryValue) return ((HexBinaryValue) h).getBinaryValue();
        return h.getStringValue().getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizeDigest(String alg) {
        String u = alg.toUpperCase(java.util.Locale.ROOT).replace("_", "-");
        switch (u) {
            case "SHA1": return "SHA-1";
            case "SHA256": return "SHA-256";
            case "SHA384": return "SHA-384";
            case "SHA512": return "SHA-512";
            default: return u;
        }
    }

    private static String normalizeHmac(String alg) {
        String u = alg.toUpperCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
        if (u.startsWith("HMAC")) return "Hmac" + u.substring(4);
        if (u.equals("MD5") || u.equals("SHA1") || u.equals("SHA256")
                || u.equals("SHA384") || u.equals("SHA512")) {
            return "Hmac" + u;
        }
        return alg; // pass-through; JCE will reject if invalid
    }

    private static Sequence encode(byte[] digest, String encoding) throws XPathException {
        String enc = encoding == null ? "base64" : encoding.toLowerCase(java.util.Locale.ROOT);
        switch (enc) {
            case "base64": return new StringValue(java.util.Base64.getEncoder().encodeToString(digest));
            case "hex": {
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
                return new StringValue(sb.toString());
            }
            case "raw":
            case "binary":
                return new Base64BinaryValue(digest);
            default:
                throw err("unknown-encoding", "Unknown encoding: " + encoding);
        }
    }

    // -- scaffolding ------------------------------------------------------

    private abstract static class CryptoFn extends ExtensionFunctionDefinition {
        protected abstract String localName();
        protected abstract SequenceType[] args();
        protected abstract SequenceType result();
        protected int minArgs() { return args().length; }
        protected abstract Sequence eval(Sequence[] a, XPathContext c) throws XPathException;

        @Override public final StructuredQName getFunctionQName() {
            return new StructuredQName("crypto", CRYPTO_NS, localName());
        }
        @Override public final SequenceType[] getArgumentTypes() { return args(); }
        @Override public final SequenceType getResultType(SequenceType[] s) { return result(); }
        @Override public final int getMinimumNumberOfArguments() { return minArgs(); }
        @Override public final int getMaximumNumberOfArguments() { return args().length; }
        @Override public boolean hasSideEffects() { return false; }
        @Override public final ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override public Sequence call(XPathContext ctx, Sequence[] a) throws XPathException {
                    return CryptoFn.this.eval(a, ctx);
                }
            };
        }
    }

    // -- crypto:hash ------------------------------------------------------

    private static final class Hash extends CryptoFn {
        @Override protected String localName() { return "hash"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    SequenceType.ANY_SEQUENCE, // accept xs:string | xs:base64Binary | xs:hexBinary
                    SequenceType.SINGLE_STRING,
                    SequenceType.SINGLE_STRING};
        }
        @Override protected int minArgs() { return 2; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_ATOMIC; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] data = bytesOrUtf8(a[0]);
            String alg = normalizeDigest(a[1].head().getStringValue());
            String enc = a.length > 2 ? a[2].head().getStringValue() : "base64";
            try {
                MessageDigest md = MessageDigest.getInstance(alg);
                return encode(md.digest(data), enc);
            } catch (NoSuchAlgorithmException e) {
                throw err("unknown-algorithm", "Unsupported digest algorithm: " + alg);
            }
        }
    }

    // -- crypto:hmac ------------------------------------------------------

    private static final class Hmac extends CryptoFn {
        @Override protected String localName() { return "hmac"; }
        @Override protected SequenceType[] args() {
            return new SequenceType[]{
                    SequenceType.ANY_SEQUENCE, // data
                    SequenceType.ANY_SEQUENCE, // key
                    SequenceType.SINGLE_STRING, // algorithm
                    SequenceType.SINGLE_STRING}; // encoding
        }
        @Override protected int minArgs() { return 3; }
        @Override protected SequenceType result() { return SequenceType.SINGLE_ATOMIC; }
        @Override protected Sequence eval(Sequence[] a, XPathContext c) throws XPathException {
            byte[] data = bytesOrUtf8(a[0]);
            byte[] key = bytesOrUtf8(a[1]);
            String alg = normalizeHmac(a[2].head().getStringValue());
            String enc = a.length > 3 ? a[3].head().getStringValue() : "base64";
            try {
                Mac mac = Mac.getInstance(alg);
                mac.init(new SecretKeySpec(key, alg));
                return encode(mac.doFinal(data), enc);
            } catch (NoSuchAlgorithmException e) {
                throw err("unknown-algorithm", "Unsupported HMAC algorithm: " + alg);
            } catch (java.security.InvalidKeyException e) {
                throw err("invalid-key", e.getMessage());
            }
        }
    }
}
