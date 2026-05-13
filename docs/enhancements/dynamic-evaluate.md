# Dynamic Evaluate Extension Functions

**Status:** Implemented

## Purpose

Provide runtime XPath-evaluation functions under two namespaces:

- **`dynamic:evaluate()`** — namespace `http://exslt.org/dynamic`,
  defined by [EXSLT](http://exslt.org/dynamic/).
- **`saxon:evaluate()`** — namespace `http://saxon.sf.net/`, Saxon's
  vendor equivalent.

Both are normally absent from Saxon-HE: `saxon:evaluate()` is locked
behind a PE/EE license, and Saxon never shipped the EXSLT one at all.
This enhancement makes both available on HE.

A single `DynamicEvaluateDefinition` is registered under both QNames so
either spelling Just Works.

## Files added

No existing files in `src/main/java/net/sf/saxon/` are modified.

- `src/main/java/net/sf/saxon/extensions/evaluate/DynamicEvaluateDefinition.java`
  — `ExtensionFunctionDefinition` whose `call()` delegates to s9api
  `XPathCompiler` on a Processor captured at registration time. Letting
  s9api handle compile + type-check + optimize avoids re-implementing
  those steps from inside the low-level expression-tree API.
- `src/main/java/net/sf/saxon/extensions/evaluate/EvaluateExtensions.java`
  — public `registerOn(Processor)` helper that installs both namespace
  variants in one call.
- `src/main/java/net/sf/saxon/extensions/evaluate/package-info.java`
- `src/test/java/net/sf/saxon/extensions/evaluate/EvaluateExtensionsTest.java`
  — 5 tests covering arithmetic, node-sets, `count()`, both namespaces,
  and the common pattern of evaluating an XPath string read from another
  document.

## Files modified

- `tools/saxx/src/main/java/saxx/Main.java` — two one-line additions
  calling `EvaluateExtensions.registerOn(processor)` at Processor init
  (one per command that builds a Processor).

## Usage

```java
Processor processor = new Processor(false);
EvaluateExtensions.registerOn(processor);
// Stylesheets that call sk:evaluate(...) or saxon:evaluate(...) now
// compile and run on this Processor.
```

```xml
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:sk="http://exslt.org/dynamic">
  <xsl:template match="/">
    <r><xsl:value-of select="sk:evaluate('1+2')"/></r>
  </xsl:template>
</xsl:stylesheet>
```

## Why use these instead of `<xsl:evaluate>`?

The XSLT 3.0 `<xsl:evaluate>` instruction already covers dynamic XPath
compilation and is supported by this fork. The function forms exist
alongside it because:

- **Existing stylesheets.** Large bodies of legacy XSL already call
  `sk:evaluate(...)` or `saxon:evaluate(...)`. They can run unchanged on
  HE without rewriting.
- **Composable in any XPath context.** `<xsl:evaluate>` is an
  instruction — only valid inside a sequence constructor. The function
  forms work inside predicates, function arguments, and `select`
  expressions: `sum(items/saxon:evaluate(@formula))`,
  `rows[sk:evaluate(@filter)]`, etc.
- **Works in any XSLT version.** `<xsl:evaluate>` is XSLT 3.0 only.
  Stylesheets declaring `version="1.0"` or `version="2.0"` literally
  cannot use the instruction; the function forms work wherever extension
  functions are supported.
- **Lighter syntax for the common case.** When you just want to evaluate
  a stored string and use the value, a one-token function call is
  shorter than the multi-attribute instruction.

Prefer `<xsl:evaluate>` when you control the stylesheet, can target
XSLT 3.0, and need its richer feature set: `with-param` injection,
explicit `context-item`, explicit `namespace-context`, or
schema-awareness.

## Limitations

- **No static-namespace-context inheritance** from the calling expression.
  Stored XPaths must either avoid namespace prefixes (e.g. by using
  `*[local-name()='Foo']` patterns) or be self-contained. Stylesheets
  that rely on inheriting caller-side namespace bindings into the
  dynamically-compiled expression should use the full XSLT 3.0
  `<xsl:evaluate>` instruction instead — already supported by this fork.
- **No `xsl:with-param` parameter injection** — same reasoning; use
  `<xsl:evaluate>` for that.

## Design notes

The function is implemented as a wrapper around `XPathCompiler` rather
than reaching directly into Saxon's `EvaluateInstr` machinery.
Empirically, hand-rolling the `ExpressionTool.make` + `typeCheck` +
`optimize` pipeline produced expressions whose elaborators were missing
operator wiring (notably the `Calculator` field of
`ArithmeticExpression` was left null, producing runtime NPEs on `1+2`).
Delegating to s9api lets Saxon handle the full compile lifecycle
correctly, at the cost of needing a `Processor` reference at
registration time (captured in the function definition's constructor).
