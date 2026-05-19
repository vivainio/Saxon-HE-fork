# EXPath File Module

**Status:** Implemented (best-effort)
**Author:** Copyright © Ville Vainio
**License:** Mozilla Public License 2.0 — same as the rest of this fork.
**Origin:** Cleanroom implementation. Not derived from
`fgeorges/expath-file-java`, BaseX, eXist-db, or any other existing
EXPath file-module codebase. Written from the public
[EXPath File specification](https://expath.org/spec/file) against
`java.nio.file` and Saxon's public extension-function API.

## Purpose

Provide the [EXPath File module](https://expath.org/spec/file)
(`http://expath.org/ns/file`) on Saxon-HE. The file module is the most
commonly-missed EXPath module on HE — functions like `file:read-text`,
`file:write-text`, `file:list`, and `file:exists` are what make
scripting-style XSLT/XQuery viable for build pipelines, code generation,
and report rendering. Saxonica ships these only in PE/EE.

This fork's implementation is a pragmatic native subset: pure Java, no
third-party jars, no Maven Central dependencies. The intent is "useful
defaults" rather than full spec coverage — see [Limitations](#limitations)
below.

## Why native (not the EXPath reference impl)

The reference `fgeorges/expath-file-java` was last touched in 2016 and
targets a pre-12 Saxon API. Reviving it would tie this fork to a dead
upstream forever. A ~400-line native implementation against
`java.nio.file` covers the common-use surface and has no maintenance
dependency on an external project.

## Functions implemented

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
| `file:read-binary` | `($path) as xs:base64Binary` | |
| `file:write-text` | `($path, $value) / ($path, $value, $enc)` | Creates parents |
| `file:write-binary` | `($path, $value as xs:base64Binary)` | Creates parents |
| `file:append-text` | `($path, $value) / ($path, $value, $enc)` | Creates parents |
| `file:list` | `($dir) as xs:string*` | Directory entries get trailing `/` per spec |
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

## Files added

No existing files in `src/main/java/net/sf/saxon/` are modified.

- `src/main/java/net/sf/saxon/fork/expath/ExpathFileFunctions.java` —
  all 24 function definitions as static nested classes of
  `ExpathFileFunctions`, sharing a `FileFn` base that boils the
  `ExtensionFunctionDefinition` + `ExtensionFunctionCall` contract down
  to a single `eval(Sequence[], XPathContext)` method per function.
  Public entry point: `register(Configuration)`.
- `src/main/java/net/sf/saxon/fork/expath/ExpathExtensions.java` —
  fork-add-on registrar. Currently just calls
  `ExpathFileFunctions.register(config)`; future EXPath modules would
  be wired in here. Also provides an `AutoInit` inner class implementing
  `net.sf.saxon.lib.Initializer` for users who prefer the Saxon
  initializer-SPI route.
- `src/test/java/net/sf/saxon/fork/expath/ExpathFileFunctionsTest.java`
  — 11 tests exercising reads, writes (text + binary + encoding +
  append), predicates, directory listing, create/delete (recursive),
  copy/move, path accessors, constants, and not-found error reporting.
  All run against a JUnit `@TempDir` so they're hermetic.
- `src/test/java/net/sf/saxon/fork/expath/ExpathExtensionsTest.java` —
  registration smoke test.

## Usage

```java
Processor processor = new Processor(false);
net.sf.saxon.fork.expath.ExpathExtensions.registerAll(
        processor.getUnderlyingConfiguration());
// Stylesheets and XQuery using file:* now compile and run.
```

### Sandboxed registration (untrusted stylesheets)

The default `registerAll` / `register(Configuration)` grants the full
filesystem permissions the JVM itself has. For multi-tenant or
user-uploaded stylesheets, use the predicate overload to confine paths
to an allowlist:

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

Or via Saxon's initializer SPI (e.g. from a configuration file):

```
saxon -init:net.sf.saxon.fork.expath.ExpathExtensions$AutoInit ...
```

XSLT example:

```xml
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:file="http://expath.org/ns/file">
  <xsl:template match="/">
    <xsl:variable name="payload" select="file:read-text('input.json')"/>
    <xsl:sequence select="file:write-text('out/result.txt', upper-case($payload))"/>
  </xsl:template>
</xsl:stylesheet>
```

XQuery example:

```xquery
declare namespace file = "http://expath.org/ns/file";
for $f in file:list('reports')
where ends-with($f, '.xml')
return file:read-text('reports/' || $f)
```

## Error handling

Failures are raised as `XPathException` with an error QName in the
EXPath errors namespace `http://expath.org/ns/error`, mirroring the spec
so XQuery `try/catch` can discriminate:

| Code | When |
|---|---|
| `file:not-found` | Path does not exist |
| `file:exists` | Destination already exists where it shouldn't |
| `file:no-dir` | Operation requires a directory but path is not one |
| `file:is-dir` | Cannot operate on a directory (e.g. non-empty on delete) |
| `file:unknown-encoding` | `Charset.forName` rejected the encoding name |
| `file:access-denied` | Path was rejected by the sandbox predicate (see [Sandboxed registration](#sandboxed-registration-untrusted-stylesheets)) |
| `file:io-error` | Catch-all for other `IOException`s |

```xquery
try { file:read-text('missing.txt') }
catch file:not-found { 'absent' }
```

## Limitations

Intentionally out of scope for this best-effort implementation. PRs
welcome, but none are blocking common use cases:

- **`file:list` filtering and recursion.** Only the 1-arity form
  (non-recursive, no glob) is implemented. For now, do recursive walks
  via XSLT/XQuery recursion over `file:list`, or filter with
  `ends-with` / `matches` on the returned strings.
- **`file:read-text-lines`.** Use `tokenize(file:read-text(...),
  '\r?\n')` until this is added.
- **`file:base-dir`** is not implemented; it requires resolving against
  the XQuery/XSLT static base URI, which the current scaffolding
  doesn't thread through. `file:current-dir` (JVM `user.dir`) is
  available as a workaround.
- **No atomic / locked writes.** `write-text` truncates and rewrites;
  concurrent writers can interleave.
- **Encoding errors are coarse.** Malformed input under a specified
  encoding raises `file:io-error`, not a finer-grained spec code.
- **No symlink-specific behavior.** Operations follow links by default.

## Isolation guarantee

Everything lives in `net.sf.saxon.fork.expath`. No other class in the
codebase references this package. To remove the module entirely:

```bash
rm -rf src/main/java/net/sf/saxon/fork/expath \
       src/test/java/net/sf/saxon/fork/expath
```

There are no `pom.xml` dependencies to clean up — the implementation
uses only `java.nio.file` and Saxon's existing public extension API.

## Design notes

- **One outer class, nested-class-per-function.** The EXPath file
  surface is large (24 functions) but each is small. Keeping them as
  nested classes of `ExpathFileFunctions` puts the entire module on one
  screen and makes the `FileFn` base — which collapses the EFD/EFC
  contract into a single `eval(...)` — visible inline.
- **Side-effect flag.** `hasSideEffects()` returns `true` for all IO
  operations (so Saxon won't constant-fold or reorder them) and
  `false` for the pure-introspection helpers
  (`dir-separator`, `name`, `path-to-uri`, etc.) so they remain
  foldable in static contexts.
- **Path normalization on `parent`.** `Paths.get("foo.txt").getParent()`
  returns `null` for a bare filename; we call `toAbsolutePath()` first
  so `file:parent('foo.txt')` returns the working directory rather than
  the empty sequence. This matches user expectation more often than the
  raw `nio` behavior.
