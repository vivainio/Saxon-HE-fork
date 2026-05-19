# EXPath Modules

**Status:** Implemented (best-effort)
**Author:** Copyright © Ville Vainio
**License:** Mozilla Public License 2.0 — same as the rest of this fork.
**Origin:** Cleanroom implementations. Not derived from
`fgeorges/expath-*-java`, BaseX, eXist-db, or any other existing EXPath
codebase. Written from the public EXPath specifications against the JDK
and Saxon's public extension-function API.

## Purpose

Provide a pragmatic native implementation of the most-missed EXPath
modules on Saxon-HE. These are the functions that make scripting-style
XSLT/XQuery viable for build pipelines, code generation, and report
rendering. Saxonica ships their equivalents only in PE/EE.

All modules in this page live in `net.sf.saxon.fork.expath` and are
pure-Java, no third-party jars, no Maven Central dependencies. The
intent is "useful defaults" rather than full spec coverage — see the
[Limitations](#limitations-file-module) sections below.

| Module | Namespace | Prefix | Backed by |
|---|---|---|---|
| File | `http://expath.org/ns/file` | `file:` | `java.nio.file` |
| Binary | `http://expath.org/ns/binary` | `bin:` | `byte[]`, `BigInteger` |
| Archive (ZIP) | `http://expath.org/ns/archive` | `arch:` | `java.util.zip` |
| Crypto (hash + HMAC) | `http://expath.org/ns/crypto` | `crypto:` | `java.security.MessageDigest`, `javax.crypto.Mac` |

The HTTP-client module is intentionally **not** bundled here — it has a
larger surface (multipart, auth, response dispatching) and is left for a
future pass.

## Why native (not the EXPath reference impls)

The `fgeorges/expath-*-java` projects target a pre-12 Saxon API and
have been dormant since 2016. Reviving them would tie this fork to a
dead upstream forever. A few hundred lines of native code against the
JDK covers the common-use surface and has no maintenance dependency on
external projects.

## Registration

All four modules are wired by a single call:

```java
Processor processor = new Processor(false);
net.sf.saxon.fork.expath.ExpathExtensions.registerAll(
        processor.getUnderlyingConfiguration());
// Stylesheets and XQuery using file:*, bin:*, arch:*, crypto:*
// now compile and run.
```

Or via Saxon's initializer SPI (e.g. from a configuration file):

```
saxon -init:net.sf.saxon.fork.expath.ExpathExtensions$AutoInit ...
```

Individual modules can be registered separately if you only want a
subset:

```java
ExpathFileFunctions.register(config);     // file:*
ExpathBinaryFunctions.register(config);   // bin:*
ExpathArchiveFunctions.register(config);  // arch:*
ExpathCryptoFunctions.register(config);   // crypto:*
```

---

## File module

Spec reference: <https://expath.org/spec/file>.

### Functions

All under namespace `http://expath.org/ns/file`. Conventional prefix
`file:`.

| Function | Signature | Notes |
|---|---|---|
| `file:exists` | `($path as xs:string) as xs:boolean` | |
| `file:is-file` | `($path) as xs:boolean` | |
| `file:is-dir` | `($path) as xs:boolean` | |
| `file:size` | `($path) as xs:integer` | |
| `file:last-modified` | `($path) as xs:dateTime` | |
| `file:read-text` | `($path) / ($path, $enc)` | Default UTF-8 |
| `file:read-text-lines` | `($path) / ($path, $enc) as xs:string*` | Default UTF-8 |
| `file:read-binary` | `($path) as xs:base64Binary` | |
| `file:write-text` | `($path, $value) / ($path, $value, $enc)` | Creates parents |
| `file:write-binary` | `($path, $value as xs:base64Binary)` | Creates parents |
| `file:append-text` | `($path, $value) / ($path, $value, $enc)` | Creates parents |
| `file:list` | `($dir) / ($dir, $recursive) / ($dir, $recursive, $glob) as xs:string*` | Paths relative to `$dir`. Directory entries get trailing `/` per spec. Glob via `FileSystem.getPathMatcher("glob:…")` — `*.xml` matches one segment, `**.xml` matches across descendants. |
| `file:create-dir` | `($dir)` | Recursive (`mkdir -p` semantics) |
| `file:delete` | `($path) / ($path, $recursive as xs:boolean)` | |
| `file:copy` | `($src, $dst)` | Recursive for directories; overwrites |
| `file:move` | `($src, $dst)` | Overwrites |
| `file:name` | `($path) as xs:string` | Last path segment |
| `file:parent` | `($path) as xs:string?` | Empty sequence if no parent |
| `file:resolve-path` | `($path) as xs:string` | Absolute, normalized |
| `file:path-to-uri` | `($path) as xs:anyURI` | `file://...` form |
| `file:dir-separator` | `() as xs:string` | |
| `file:line-separator` | `() as xs:string` | |
| `file:path-separator` | `() as xs:string` | |
| `file:temp-dir` | `() as xs:string` | `java.io.tmpdir` |
| `file:current-dir` | `() as xs:string` | `user.dir` |

Paths accept either native form (`/tmp/x`, `C:\Users\x`) or `file://`
URIs.

### Sandboxed registration (untrusted stylesheets)

The default `registerAll` / `ExpathFileFunctions.register(Configuration)`
grants the full filesystem permissions the JVM itself has. For
multi-tenant or user-uploaded stylesheets, use the predicate overload
to confine paths to an allowlist:

```java
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import net.sf.saxon.fork.expath.ExpathFileFunctions;

Path root = Paths.get("/srv/data").toAbsolutePath().normalize();
Predicate<Path> sandbox =
        p -> p.toAbsolutePath().normalize().startsWith(root);
ExpathFileFunctions.register(processor.getUnderlyingConfiguration(), sandbox);
```

The predicate is consulted on every path argument **before any IO**.
Paths that fail the check raise `file:access-denied`. Operations with
two paths (`file:copy`, `file:move`) check both. Constant-style
accessors (`file:dir-separator`, `file:temp-dir`, …) do not take path
arguments and are unaffected.

If you don't need a sandbox and **only run stylesheets you control**,
the default registration is fine — it's the same trust boundary HE
already assumes for `xsl:result-document href="file:..."`.

### Error codes (file module)

Failures raise `XPathException` with a QName in
`http://expath.org/ns/error`, prefix `file`:

| Code | When |
|---|---|
| `file:not-found` | Path does not exist |
| `file:exists` | Destination already exists where it shouldn't |
| `file:no-dir` | Operation requires a directory but path is not one |
| `file:is-dir` | Cannot operate on a directory (e.g. non-empty on delete) |
| `file:unknown-encoding` | `Charset.forName` rejected the encoding name |
| `file:access-denied` | Path was rejected by the sandbox predicate |
| `file:io-error` | Catch-all for other `IOException`s |

```xquery
try { file:read-text('missing.txt') }
catch file:not-found { 'absent' }
```

### Limitations (file module)

- **`file:base-dir`** is not implemented; it requires resolving against
  the XQuery/XSLT static base URI, which the current scaffolding
  doesn't thread through. `file:current-dir` (JVM `user.dir`) is
  available as a workaround.
- **No atomic / locked writes.** `write-text` truncates and rewrites;
  concurrent writers can interleave.
- **Encoding errors are coarse.** Malformed input under a specified
  encoding raises `file:io-error`, not a finer-grained spec code.
- **No symlink-specific behavior.** Operations follow links by default.

---

## Binary module

Spec reference: <https://expath.org/spec/binary>.

Functions operate on `xs:base64Binary` for binary data throughout
(matching the spec). `xs:hexBinary` is also accepted as input wherever
binary is taken. Integer offsets are zero-based.

> **Note — "base64Binary" is not encoded data.** XDM's binary atomic
> types (`xs:base64Binary`, `xs:hexBinary`) wrap a raw `byte[]`
> internally; the "base64" / "hex" in the name refers only to the
> *lexical* form you get when you serialize one to a string. A pipeline
> like `file:read-binary → bin:part → crypto:hash → arch:create →
> file:write-binary` passes the same `byte[]` end-to-end with no encode
> / decode steps. You only pay the 33 % base64 inflation if you
> explicitly stringify (e.g. `string($bytes)`) or embed the value in
> XML. `bin:hex-string` and `bin:bin-string` are the convenience
> escape hatches for when you do want the text form.

### Functions

All under namespace `http://expath.org/ns/binary`. Conventional prefix
`bin:`.

| Function | Signature | Notes |
|---|---|---|
| `bin:length` | `($in as xs:base64Binary) as xs:integer` | Byte length |
| `bin:part` | `($in, $offset) / ($in, $offset, $size) as xs:base64Binary` | Slice |
| `bin:join` | `($parts as xs:base64Binary*) as xs:base64Binary` | Concat |
| `bin:hex` | `($in as xs:string?) as xs:base64Binary?` | Parse hex digits |
| `bin:bin` | `($in as xs:string?) as xs:base64Binary?` | Parse binary digits |
| `bin:octal` | `($in as xs:string?) as xs:base64Binary?` | Parse octal digits |
| `bin:to-octets` | `($in) as xs:integer*` | Bytes as `[0,255]` |
| `bin:from-octets` | `($octets as xs:integer*) as xs:base64Binary` | Octets `[0,255]` |
| `bin:and` / `bin:or` / `bin:xor` | `($x, $y as xs:base64Binary) as xs:base64Binary` | Bitwise, equal lengths required |
| `bin:not` | `($in) as xs:base64Binary` | Bitwise NOT |
| `bin:shift` | `($in, $by as xs:integer) as xs:base64Binary` | Positive = left shift, negative = right. Fixed width. |
| `bin:pack-integer` | `($n, $size) / ($n, $size, $order)` | Two's-complement, big-endian by default. `$order` is `'least-significant-first'` for little-endian. |
| `bin:unpack-integer` | `($in, $offset, $size) / (..., $order)` | Signed read; same `$order` flag |
| `bin:encode-string` | `($s) / ($s, $enc) as xs:base64Binary?` | Default UTF-8 |
| `bin:decode-string` | `($in) / ($in, $enc) / ($in, $enc, $off) / ($in, $enc, $off, $size) as xs:string?` | Default UTF-8 |
| `bin:pad-left` / `bin:pad-right` | `($in, $size) / ($in, $size, $octet)` | Octet defaults to `0`. `$size` is the number of pad bytes to add. |
| `bin:find` | `($in, $offset, $search) as xs:integer?` | Byte-offset of first match, empty if absent |

#### Extensions (not in the spec)

For convenience when piping to other tools / writing tests:

| Function | Returns |
|---|---|
| `bin:hex-string($in as xs:base64Binary) as xs:string` | Lowercase hex digits |
| `bin:bin-string($in as xs:base64Binary) as xs:string` | `0`/`1` digits, MSB-first |

### Error codes (binary module)

In `http://expath.org/ns/error`, prefix `bin`:

| Code | When |
|---|---|
| `bin:differing-length-arguments` | Bitwise op on unequal-length inputs |
| `bin:input-type-error` | Wrong input atomic type |
| `bin:index-out-of-range` | Bad offset / size for slice / shift / pack |
| `bin:non-numeric-character` | Bad digit in `hex`/`bin`/`octal` input |
| `bin:octet-out-of-range` | Octet outside `[0,255]` in `from-octets` |
| `bin:unknown-encoding` | `Charset.forName` rejected the encoding name |
| `bin:negative-size` | Negative size in `pack-integer` / `pad-*` |

### Example

```xquery
declare namespace bin = "http://expath.org/ns/binary";
let $bytes := bin:hex('deadbeef'),
    $hi   := bin:part($bytes, 0, 2),
    $lo   := bin:part($bytes, 2, 2)
return (bin:hex-string($hi), bin:hex-string($lo))
(: → ("dead", "beef") :)
```

---

## Archive module (ZIP)

Spec reference: <https://expath.org/spec/archive>.

ZIP format only. The spec also defines TAR / GZip variants — those
would need `commons-compress` and are out of scope for the zero-deps
goal here.

### Functions

All under namespace `http://expath.org/ns/archive`. Conventional prefix
`arch:`.

| Function | Signature | Notes |
|---|---|---|
| `arch:create` | `($entries as xs:string*, $contents) as xs:base64Binary` | Contents may be strings (UTF-8) or `xs:base64Binary` |
| `arch:entries` | `($archive as xs:base64Binary) as xs:string*` | Entry paths |
| `arch:extract-text` | `($archive) / ($archive, $entries) / ($archive, $entries, $enc) as xs:string*` | Default UTF-8; empty `$entries` sequence means "all" |
| `arch:extract-binary` | `($archive) / ($archive, $entries) as xs:base64Binary*` | |
| `arch:delete` | `($archive, $entries as xs:string*) as xs:base64Binary` | Returns the trimmed archive |
| `arch:update` | `($archive, $entries, $contents) as xs:base64Binary` | Overwrites matching entries in place; appends new ones |
| `arch:options` | `($archive) as xs:string` | Returns `"zip"` |

### Entries representation

The EXPath Archive spec defines entries as
`<archive:entry>name</archive:entry>` elements with metadata attributes
(`size`, `compressed-size`, `last-modified`, `encoding`,
`compression-level`). This implementation accepts plain `xs:string`
entry paths and emits `xs:string*` from `arch:entries` — sufficient for
the common create / extract / delete workflows. If an element item is
given as an entry argument, its string-value is used as the path.

### Error codes (archive module)

In `http://expath.org/ns/error`, prefix `arch`:

| Code | When |
|---|---|
| `arch:missing-input` | Empty archive input |
| `arch:input-type-error` | Wrong input atomic type |
| `arch:different-length` | `entries` and `contents` differ in length |
| `arch:io-error` | Underlying `IOException` (corrupt ZIP, etc.) |
| `arch:unknown-encoding` | `Charset.forName` rejected the encoding name |
| `arch:unknown-format` | `arch:options` couldn't recognize the archive |

### Example

```xquery
declare namespace arch = "http://expath.org/ns/archive";
declare namespace file = "http://expath.org/ns/file";

(: bundle two files into report.zip :)
let $zip := arch:create(
        ('cover.txt', 'data.xml'),
        ('Report 2026', '<root><n>42</n></root>'))
return file:write-binary('report.zip', $zip)
```

### Limitations (archive module)

- ZIP only — no TAR, no GZip, no 7z, no compression-algorithm choice.
- No per-entry metadata (size, mtime, compression level) on
  create/update — entries inherit `ZipOutputStream` defaults.
- No streaming API: archives are held entirely in memory. Practical for
  hundreds of MB; not for multi-GB blobs.

---

## Crypto module (hash + HMAC)

Spec reference: <https://expath.org/spec/crypto>.

This implements the **digest and HMAC** subset of the EXPath
Cryptographic module — the parts that wrap one-line calls into
`java.security.MessageDigest` and `javax.crypto.Mac`. The XML-DSig
(sign / validate) and encryption parts of the spec are not implemented;
they would pull in a much larger surface.

### Functions

All under namespace `http://expath.org/ns/crypto`. Conventional prefix
`crypto:`.

| Function | Signature | Notes |
|---|---|---|
| `crypto:hash` | `($data, $algorithm) / ($data, $algorithm, $encoding) as xs:anyAtomicType` | See "data polymorphism" below |
| `crypto:hmac` | `($data, $key, $algorithm) / (..., $encoding) as xs:anyAtomicType` | |

**Algorithms** (`$algorithm`):
- For `crypto:hash`: `MD5`, `SHA-1`, `SHA-256`, `SHA-384`, `SHA-512`.
  Bare forms (`SHA256`, `SHA512`) are accepted.
- For `crypto:hmac`: the JCE name (`HmacMD5`, `HmacSHA1`, `HmacSHA256`,
  `HmacSHA384`, `HmacSHA512`). The bare digest name (`SHA-256`) is
  accepted as a convenience and auto-prefixed.

**`$encoding`** (default `base64`):
- `base64` → returns `xs:string` (Base64-encoded digest).
- `hex` → returns `xs:string` (lowercase hex).
- `raw` (also `binary`) → returns `xs:base64Binary` (raw digest bytes).

**Data polymorphism.** `$data` and `$key` accept:
- `xs:string` — encoded as UTF-8 bytes.
- `xs:base64Binary` — used as-is.
- `xs:hexBinary` — used as-is.

### Error codes (crypto module)

In `http://expath.org/ns/error`, prefix `crypto`:

| Code | When |
|---|---|
| `crypto:missing-input` | Required `$data` / `$key` empty |
| `crypto:unknown-algorithm` | JCE doesn't know the named algorithm |
| `crypto:unknown-encoding` | `$encoding` not one of `base64`/`hex`/`raw` |
| `crypto:invalid-key` | JCE rejected the HMAC key |

### Example

```xquery
declare namespace crypto = "http://expath.org/ns/crypto";

(: ETag from response body :)
crypto:hash($response/body/text(), 'SHA-256', 'hex')

(: HMAC-SHA256 for signing a webhook payload :)
crypto:hmac($payload, $secret, 'SHA-256', 'hex')
```

### Limitations (crypto module)

- No XML signature / validation.
- No symmetric or asymmetric encryption.
- Algorithm availability follows the JVM. FIPS-restricted runtimes may
  refuse MD5 / SHA-1 — `crypto:unknown-algorithm` will surface that.

---

## Files

No existing files in `src/main/java/net/sf/saxon/` are modified.

- `src/main/java/net/sf/saxon/fork/expath/ExpathExtensions.java` —
  fork-add-on registrar. Calls each module's `register(config)`. Also
  provides an `AutoInit` inner class implementing
  `net.sf.saxon.lib.Initializer` for users who prefer the Saxon
  initializer-SPI route.
- `src/main/java/net/sf/saxon/fork/expath/ExpathFileFunctions.java`
- `src/main/java/net/sf/saxon/fork/expath/ExpathBinaryFunctions.java`
- `src/main/java/net/sf/saxon/fork/expath/ExpathArchiveFunctions.java`
- `src/main/java/net/sf/saxon/fork/expath/ExpathCryptoFunctions.java`
- `src/test/java/net/sf/saxon/fork/expath/*.java` — one test class per
  module, plus a registrar smoke test. All run hermetically against
  JUnit `@TempDir` (file module) or in-memory only (others).

Each `Expath*Functions` class follows the same shape: a single outer
class with one nested static class per function, sharing a small base
that collapses the EFD/EFC contract into a single
`eval(Sequence[], XPathContext)` method.

## Isolation guarantee

Everything lives in `net.sf.saxon.fork.expath`. No other class in the
codebase references this package. To remove the modules entirely:

```bash
rm -rf src/main/java/net/sf/saxon/fork/expath \
       src/test/java/net/sf/saxon/fork/expath
```

There are no `pom.xml` dependencies to clean up — all four modules use
only the JDK and Saxon's existing public extension API.

## Design notes

- **One outer class, nested-class-per-function.** Each module has
  10–25 functions but each function body is small. Nesting them keeps
  the whole module on one screen and makes the shared `*Fn` base —
  which collapses the EFD/EFC contract into a single `eval(...)` —
  visible inline.
- **Side-effect flag.** File-module IO operations report
  `hasSideEffects() = true` so Saxon won't constant-fold or reorder
  them. Pure-introspection helpers and the bin/arch/crypto modules
  return `false` — they're referentially transparent.
- **Path normalization on `file:parent`.**
  `Paths.get("foo.txt").getParent()` returns `null` for a bare
  filename; we call `toAbsolutePath()` first so `file:parent('foo.txt')`
  returns the working directory rather than the empty sequence. Matches
  user expectation more often than the raw `nio` behavior.
- **Polymorphic data inputs.** `crypto:*` and `arch:*` accept strings,
  base64Binary, and hexBinary at the same argument position. Saxon
  signature declares `item()*` and the implementation dispatches at
  runtime. Slightly looser than the spec's strict-union types, but
  spares callers from constructor noise (`xs:hexBinary(...)`,
  `xs:base64Binary(...)`) for trivial cases.
