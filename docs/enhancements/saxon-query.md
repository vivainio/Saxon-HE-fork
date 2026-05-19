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
saxon:query($query as xs:string, $context as item()?) as item()*
```

- One-argument form: compiles and evaluates `$query` using the caller's
  current context item (if any).
- Two-argument form: evaluates `$query` against an explicit `$context`
  item. Pass `()` to evaluate with no context item.

## Files added

No existing Saxonica source files are modified.

- `src/main/java/net/sf/saxon/extensions/xquery/SaxonQueryDefinition.java`
  — `ExtensionFunctionDefinition` whose `call()` delegates to s9api
  `XQueryCompiler` on a Processor captured at registration time.
- `src/main/java/net/sf/saxon/extensions/xquery/QueryExtensions.java`
  — public `registerOn(Processor)` helper.
- `src/main/java/net/sf/saxon/extensions/xquery/package-info.java`
- `src/test/java/net/sf/saxon/extensions/xquery/QueryExtensionsTest.java`
  — 5 tests covering arithmetic, FLWOR, implicit context item, explicit
  context item, and XQuery direct element constructors.

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

- **No external-variable / parameter map.** Saxon-EE's `saxon:query()`
  accepts a third `map(xs:string, item()*)` argument that binds
  external variables in the query. That form is not implemented here;
  encode parameters into the query string instead, or build the query
  with a small wrapper that calls `XQueryEvaluator.setExternalVariable`
  directly.
- **No static-namespace-context inheritance** from the calling
  expression. Queries must declare any prefixes they use in their own
  prolog or use `local-name()`-style patterns.

## Design notes

Same approach as the sibling `DynamicEvaluateDefinition`: wrap s9api
rather than reaching into Saxon's internal expression-tree API. The
extension function captures a `Processor` reference at registration
time and creates a fresh `XQueryCompiler` per call — cheap, thread-safe
across stylesheets, and lets Saxon handle the full XQuery compile and
optimize lifecycle.
