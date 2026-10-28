# Static Error Deferral Policy

**Status:** Implemented

## Purpose

Xalan reports XPath type errors only when the expression is evaluated. Saxon
rejects them while compiling the whole stylesheet, even in a branch that never
runs (e.g. `normalize-space(x) | y`, a union with a string). Legacy stylesheets
written for Xalan therefore fail to compile on Saxon.

Saxon already has a deferral mechanism: when a static error is not
"reportable statically", the failing expression is replaced by an
`ErrorExpression` that raises the error only if it is evaluated. This
enhancement lets an embedding application decide, per `Configuration`, which
static errors take that path. The default defers nothing.

## Files added

- `src/main/java/net/sf/saxon/trans/StaticErrorPolicy.java` — per-`Configuration`
  `Policy` registry (`WeakHashMap`), with `set(config, policy)` and
  `shouldDefer(config, error, staticContext)`.
- `src/test/java/net/sf/saxon/trans/StaticErrorPolicyTest.java`

## Files modified

- `src/main/java/net/sf/saxon/style/StyleElement.java`, `typeCheck(String, Expression)`
  — one added condition in the `catch`: a reportable error is rethrown unless
  `StaticErrorPolicy.shouldDefer(...)` says to defer it. Marked
  `// Fork enhancement`.

## Usage

```java
StaticErrorPolicy.set(processor.getUnderlyingConfiguration(), (err, sc) ->
        "XPTY0004".equals(err.getErrorCodeQName().getLocalPart())
                && sc.isInBackwardsCompatibleMode());   // version="1.0" modules only
```

`sc.isInBackwardsCompatibleMode()` is true for expressions in a
`version="1.0"` stylesheet module, so 2.0/3.0 modules stay strict. Further
gating (e.g. the module declares the Xalan namespace) can be done in the policy.

## Limitations

- Deferral is per `select`/`test`/etc. attribute: the whole attribute
  expression becomes the error expression, so `false() and (<bad>)` still
  fails at run time. A bad expression in a branch that does not run is fine.
- Only attributes type-checked through `StyleElement.typeCheck` are covered.
