# `saxon:query()` Extension Function

**Status:** Implemented

## Purpose

Provide runtime XQuery evaluation from within XSLT and XPath under the
Saxon vendor namespace:

- **`saxon:query()`** — namespace `http://saxon.sf.net/`.

This mirrors Saxon-PE/EE's `saxon:query()`, which is normally locked
behind a PE/EE license. Saxon-HE itself already ships a full XQuery
implementation via the s9api `XQueryCompiler` — `saxon:query()` is just
a thin extension-function wrapper around it.

## Signatures

```
saxon:query($query as xs:string) as item()*
saxon:query($query as xs:string,
            $context as item()?) as item()*
saxon:query($query as xs:string,
            $context as item()?,
            $bindings as map(xs:string, item()*)?) as item()*
```

- One-argument form: compiles and evaluates `$query` using the caller's
  current context item (if any).
- Two-argument form: evaluates `$query` against an explicit `$context`
  item. Pass `()` to evaluate with no context item.
- Three-argument form: also binds external variables in the query. Each
  map entry becomes a `declare variable $... external` binding. Keys are
  resolved as lexical QNames against the **calling expression's static
  namespace context** (so a `xmlns:foo="..."` in scope on the stylesheet
  applies to a key like `"foo:bar"`). Unprefixed keys map to the
  no-namespace; EQName syntax `"Q{uri}local"` is also accepted. This
  matches Saxon-EE's convention.

## Files added

No existing Saxonica source files are modified.

- `src/main/java/net/sf/saxon/extensions/xquery/SaxonQueryDefinition.java`
  — `ExtensionFunctionDefinition` whose `call()` delegates to s9api
  `XQueryCompiler` on a Processor captured at registration time.
- `src/main/java/net/sf/saxon/extensions/xquery/QueryExtensions.java`
  — public `registerOn(Processor)` helper.
- `src/main/java/net/sf/saxon/extensions/xquery/package-info.java`
- `src/test/java/net/sf/saxon/extensions/xquery/QueryExtensionsTest.java`
  — 9 tests covering arithmetic, FLWOR, implicit/explicit context item,
  direct element constructors, and four bindings-map cases (no-namespace
  keys, prefixed keys inheriting the caller's namespace context, EQName
  keys, and node-valued bindings).

## Files modified

- `tools/saxx/src/main/java/saxx/Main.java` — one-line additions calling
  `QueryExtensions.registerOn(processor)` next to the existing
  `EvaluateExtensions.registerOn(...)` calls (one per command that
  builds a Processor).

## Usage

```java
Processor processor = new Processor(false);
QueryExtensions.registerOn(processor);
// Stylesheets that call saxon:query(...) now compile and run.
```

```xml
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:saxon="http://saxon.sf.net/">
  <xsl:template match="/">
    <r>
      <!-- FLWOR — XQuery-only, not expressible in XPath -->
      <xsl:value-of select="saxon:query(
          'sum(for $i in 1 to 5 return $i * $i)')"/>
    </r>
  </xsl:template>
</xsl:stylesheet>
```

With external-variable bindings:

```xml
<xsl:value-of select="saxon:query(
    'declare variable $x external;
     declare variable $y external;
     $x * $y',
    .,
    map { 'x' : 6, 'y' : 7 })"/>
<!-- → 42 -->
```

## Why use this instead of `saxon:evaluate()` / `<xsl:evaluate>`?

`saxon:evaluate()` and `<xsl:evaluate>` compile **XPath** expressions.
`saxon:query()` compiles full **XQuery**, which is a superset:

- **FLWOR expressions** (`for $x in ... let $y := ... where ... order
  by ... return ...`) — not available in XPath.
- **XQuery direct element constructors** —
  `<wrapped>{1+1}</wrapped>` produces a node directly.
- **Module prolog**: `declare namespace`, `declare variable`,
  `declare function`, type imports — letting the query string be
  self-contained without inheriting the caller's static context.

Prefer `<xsl:evaluate>` when an XPath expression suffices; reach for
`saxon:query()` when you genuinely need XQuery features.

## Limitations

- **No static-namespace-context inheritance for the query body itself.**
  Queries must declare any prefixes they use in their own prolog
  (`declare namespace ...`) or use `local-name()`-style patterns. The
  caller's namespace context *is* used for resolving the keys of the
  `$bindings` map (see signatures above), but it does not flow into the
  compiled query.

## Design notes

Same approach as the sibling `DynamicEvaluateDefinition`: wrap s9api
rather than reaching into Saxon's internal expression-tree API. The
extension function captures a `Processor` reference at registration
time and creates a fresh `XQueryCompiler` per call — cheap, thread-safe
across stylesheets, and lets Saxon handle the full XQuery compile and
optimize lifecycle.
