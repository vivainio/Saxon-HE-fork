# `saxon:import-query` Top-Level Declaration

**Status:** Implemented (Saxon-HE: `href` form only)

## Purpose

Import the public functions of an XQuery library module into an XSLT
stylesheet's static function scope, so the stylesheet can call them as
ordinary XPath function calls. Mirrors the Saxon-PE/EE extension of the
same name.

Saxon-HE already ships full XQuery, including library-module
compilation via `import module` at-hints. This enhancement wires that
plumbing through to XSLT compile so the imported functions resolve at
stylesheet-compile time with no per-call overhead — every call site
binds statically through the normal function-library chain, the same
shape as any other extension function.

## Syntax

Top-level declaration, only inside `xsl:stylesheet` / `xsl:transform`
/ `xsl:package`:

```xml
<saxon:import-query namespace="<module-namespace-uri>"
                    href="<location-of-library-source>"/>
```

| Attribute    | Required        | Meaning                                                   |
|--------------|-----------------|-----------------------------------------------------------|
| `namespace`  | yes             | The module namespace declared by the XQuery library.      |
| `href`       | yes (in HE)     | Location of the library module source, resolved relative to the stylesheet's base URI. |

> **HE limitation.** Saxon-EE additionally lets you call
> `XsltCompiler.importXQueryEnvironment(xqueryCompiler)` to attach
> pre-compiled libraries, then reference them by namespace alone (no
> `href`). That programmatic path goes through
> `StaticQueryContext.compileLibrary`, which is an EE-only stub in
> Saxon-HE. In HE, `href` is therefore required. The `compileLibrary`
> EE entry point could be implemented in this fork if needed; the
> declarative `href` form covers the common case.

## Semantics

- The public (non-`%private`) functions declared in the library module
  become callable from any XPath expression in the stylesheet under
  the module's namespace.
- `%private` functions are not imported.
- If the same namespace is imported more than once, only the first
  declaration takes effect; subsequent ones are silently ignored
  (matches the EE rule).
- Transitively imported XQuery modules are not re-imported into the
  stylesheet — only the direct module's declarations.

## Example

**`greet.xq`** — an XQuery library module:

```xquery
module namespace g = "http://example.com/greet";

declare function g:hello($who as xs:string) as xs:string {
    concat("hello ", $who)
};

declare function g:shout($who as xs:string) as xs:string {
    upper-case(g:hello($who))
};

declare %private function g:internal-only() as xs:string {
    "this is not visible from XSLT"
};
```

**`hello.xsl`** — a stylesheet that imports and calls it:

```xml
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:saxon="http://saxon.sf.net/"
                xmlns:g="http://example.com/greet">

    <saxon:import-query namespace="http://example.com/greet"
                        href="greet.xq"/>

    <xsl:template match="/">
        <result>
            <plain>{g:hello('world')}</plain>
            <loud>{g:shout('world')}</loud>
        </result>
    </xsl:template>
</xsl:stylesheet>
```

Running `hello.xsl` against any input produces:

```xml
<result>
   <plain>hello world</plain>
   <loud>HELLO WORLD</loud>
</result>
```

Calling `g:internal-only()` from the stylesheet would be a compile
error — that function is `%private` and not visible across the module
boundary.

## How it works

1. The XSLT parser (`StyleNodeFactory`) recognises
   `{http://saxon.sf.net/}import-query` at top level and instantiates
   the fork's `SaxonImportQuery` `StyleElement` instead of the inert
   `DataElement`.
2. During the `index` phase of `PrincipalStylesheetModule.preprocess`
   (which runs before XPath fixup), `SaxonImportQuery.index()` compiles
   a synthetic main module:

   ```xquery
   import module namespace m = "<ns>" at "<href>";
   ()
   ```

   via `StaticQueryContext.compileQuery`. This is the HE-functional
   way to compile an XQuery library — it reuses the parser's existing
   `import module` machinery.

3. The synthetic compile produces an `Executable` whose
   `getQueryLibraryModules(<ns>)` lists the imported library's
   `QueryModule`(s). For each one, the implementation enumerates the
   library's *local* function library and:
   - skips `%private` functions,
   - calls `XQueryFunctionLibrary.declareFunction(...)` on the
     stylesheet package's pre-existing query-function slot (already
     wired into the function-library chain by
     `StylesheetPackage.createFunctionLibrary` at line 855),
   - registers each compiled `UserFunction` as a `Component` in the
     stylesheet package, so XSLT's link phase
     (`Actor.processComponentReference`) can resolve calls.
4. Later in compilation, `PrincipalStylesheetModule.compile` already
   calls `fixupReferences()` on each XQueryFunction in that slot, so
   any `UserFunctionCall`s produced by `bind()` get their target
   `UserFunction` filled in automatically.

Net result: when the stylesheet body is compiled, `g:hello('world')`
resolves via the normal XPath function-resolution chain, binds
statically to the imported `UserFunction`, and runs at the same cost
as any built-in or `xsl:function` call.

## Library cache

Library compiles are cached per `Configuration` keyed by
`(namespace, absolute-href, file-mtime)`. When N stylesheets in the
same JVM import the same library file:

- First import: XQuery parser + analyzer run.
- Subsequent imports: cache hit, no parse, no analyze.
- Source file mtime changes (deploy, dev hot-reload, `touch`): cache
  miss; the library is recompiled.
- Non-`file:` URIs (`http:`, `classpath:` etc.) use mtime `-1` and are
  treated as immutable for the JVM lifetime — don't use this caching
  path for volatile remote libraries.

Each importing stylesheet package gets its own `Component` wrapping
the shared `UserFunction`. This is safe because XSLT's binding-slot
allocator gates on `packageData.isXSLT()`, which is `false` for
XQuery-compiled functions — the allocator never tries to recurse into
their bodies. Each package's `componentIndex` therefore has its own
entry, but the function body is parsed and held once.

The cache is keyed on `Configuration` via a `WeakHashMap`, so shutting
down a Configuration releases its cached libraries.

## Files added

- `src/main/java/net/sf/saxon/extensions/xquery/SaxonImportQuery.java` —
  the top-level `StyleElement` that compiles the library and merges
  its functions into the stylesheet package. Lives in the fork's
  extension package (alongside `SaxonQueryDefinition`), not in
  upstream's `net.sf.saxon.style`, to minimise the surface modified
  in upstream source files.
- `src/test/java/net/sf/saxon/extensions/xquery/SaxonImportQueryTest.java` —
  4 tests:
  1. basic import + call of a public function with a relative `href`
     pointing at a real `.xq` file on disk,
  2. `%private` functions are not imported (call is rejected) but
     remain callable internally via another module function,
  3. multiple arities of the same function name both bind correctly,
  4. missing `href` raises a compile error with a clear message.

## Files modified

- `src/main/java/net/sf/saxon/style/StyleNodeFactory.java` —
  in `makeElementNode`, before falling through to the inert
  `DataElement` for non-XSLT top-level elements, consult a new
  helper `makeSaxonTopLevelDeclaration(localName)` for elements in
  `http://saxon.sf.net/`. The helper returns a `SaxonImportQuery`
  instance for `import-query` and `null` for unknown local names
  (preserving existing `DataElement` behaviour). Marked with a
  "Fork enhancement" comment.

## Limitations / future work

- No support yet for `XsltCompiler.importXQueryEnvironment(...)` — see
  the HE limitation note above. Adding it would mean implementing
  `StaticQueryContext.compileLibrary` in HE (currently an EE-only
  stub) and reading back `CompilerInfo.queryLibraries` during XSLT
  compile.
- No modelling of `xsl:function override="yes|no"` interaction with
  imported XQuery functions. The current chain order is whatever
  `StylesheetPackage.createFunctionLibrary` set up — review if you
  intend to shadow imported names with stylesheet-defined `xsl:function`s.
- Stylesheets using `saxon:import-query` cannot be exported to SEF
  (same restriction as EE — the imported XQuery code is not part of
  the export format).
