# JNode Navigation Functions

**Status:** Implemented
**Author:** Copyright © Ville Vainio
**License:** Mozilla Public License 2.0 — same as the rest of this fork.
**Origin:** Independent implementation built on top of Saxon-HE 13.0's
own `net.sf.saxon.ma.jnode.*` classes (see [Background](#background)).

## Purpose

Saxon-HE 13.0 ships the data structures behind the draft XPath/XQuery
4.0 "JNode" feature — treating a parsed-JSON map/array as a tree of
navigable, identity-bearing nodes with parent links and positions,
much like an XML tree. But the native `/` path operator only routes
through them when the static XPath language level is raised to 40, and
doing that calls `Configuration.checkLicensedFeature`, which requires
Saxon-PE. This module exposes the same underlying JNode object model
as ordinary extension functions instead of `/` syntax, so it works on
Saxon-HE.

## Background

This started from pulling Saxon 13.0 in as this fork's vendored base
and checking which parts of Saxon 13's "XPath 4.0" feature list are
genuinely present in the open-source HE tree. Some are (e.g. regex
lookahead/lookbehind — see `RegexLookaroundTest`); JNode navigation via
`/` specifically is not, because of the license check above.

That check is Saxonica's real commercial HE/PE boundary, not a bug,
and **this fork does not patch around it** — see the commit history /
conversation that led here for the reasoning. What *is* fair game is
the JNode object model itself: its axis-walking methods
(`getParent()`, `iterateChildAxis()`, `getContent()`, `getSelector()`,
`getPosition()`, `hasChildNodes()`) are ordinary public methods with
no license check anywhere in the call path — confirmed by grepping the
entire `net.sf.saxon.ma.jnode` package for
`checkLicensedFeature`/`LicenseException`/`LicenseFeature`: nothing.
Saxonica even ships a ready s9api wrapper for it, `XdmJNode` (`@since
13.0`), as a public class.

So this module is a from-scratch, independent navigation surface (a
family of extension functions) built on top of those already-open,
already-MPL-licensed data structures — not a reuse or rewiring of the
gated `/`-operator feature.

## Functions

All under namespace `http://github.com/vivainio/saxon-he-fork/jnode`
(a fork-original vendor namespace — not a Saxonica or spec namespace).
Conventional prefix `jn:`.

| Function | Signature | Notes |
|---|---|---|
| `jn:node` | `($value as item()) as item()` | Wrap a map or array as a root JNode. Raises `jn:not-a-map-or-array` for anything else. |
| `jn:children` | `($node as item()) as item()*` | Child JNodes: map entries or array members. Empty if the node wraps an atomic value or empty map/array. |
| `jn:parent` | `($node as item()) as item()?` | Parent JNode, or the empty sequence for a root node. |
| `jn:root` | `($node as item()) as item()` | The root JNode of the tree containing this node (walks up via `getParent()`). |
| `jn:selector` | `($node as item()) as xs:anyAtomicType?` | The map key (`xs:string`) or 1-based array index (`xs:integer`) that reached this node from its parent. Empty for a root node. |
| `jn:position` | `($node as item()) as xs:integer?` | Sibling position (see [Design notes](#design-notes) for what "position" means here). Empty for a root node. |
| `jn:value` | `($node as item()) as item()*` | The JSON value wrapped by this node: a map, an array, or an atomic value. |
| `jn:has-children` | `($node as item()) as xs:boolean` | True if the wrapped value is a non-empty map or array. |
| `jn:is-node` | `($item as item()?) as xs:boolean` | True if the argument is a JNode produced by this module. Empty sequence and non-JNode items both give `false`. |

All arguments/results that carry a JNode are typed `item()`, not a
dedicated JNode item type — see [Design notes](#design-notes).

## Registration

```java
Processor processor = new Processor(false);
net.sf.saxon.fork.jnode.JNodeFunctions.register(
        processor.getUnderlyingConfiguration());
```

Or via Saxon's initializer SPI:

```
saxon -init:net.sf.saxon.fork.jnode.JNodeFunctions$AutoInit ...
```

Not wired into `saxx` automatically (same as the EXPath modules) —
opt in per-application.

## Example

```xquery
declare namespace jn = "http://github.com/vivainio/saxon-he-fork/jnode";

let $root := jn:node(parse-json('{"a":1,"b":[10,20,30],"c":{"d":"hi"}}')),
    $b    := jn:children($root)[jn:selector(.) = 'b'],
    $b2   := jn:children($b)[jn:selector(.) = 2]
return (
  jn:selector($b),                (: "b" :)
  jn:value($b) instance of array(*),  (: true :)
  jn:selector($b2),               (: 2 :)
  jn:value($b2),                  (: 20 :)
  jn:root($b2) is $root           (: true :)
)
```

### Recursive descent: flattening JSON to dotted paths

There's no native `/` or `//` shortcut (that's the gated part), but
recursive descent is a normal recursive function. This flattens any
JSON value to `path = value` lines, mixing `.key` for map entries and
`[index]` for array members:

```xquery
declare namespace jn = "http://github.com/vivainio/saxon-he-fork/jnode";

declare function local:flatten($node as item(), $path as xs:string) as xs:string* {
  if (jn:has-children($node))
  then
    for $child in jn:children($node)
    let $sel := jn:selector($child)
    let $childPath :=
      if ($sel instance of xs:integer)
      then $path || '[' || $sel || ']'
      else if ($path = '') then xs:string($sel)
      else $path || '.' || $sel
    return local:flatten($child, $childPath)
  else
    $path || ' = ' || jn:value($node)
};

let $json := parse-json(
    '{"user":{"name":"Ada","tags":["admin","dev"]},"active":true}')
return string-join(local:flatten(jn:node($json), ''), '&#10;')

(: →
user.name = Ada
user.tags[1] = admin
user.tags[2] = dev
active = true
:)
```

## Error codes

Raised as `XPathException` with a QName in this module's own
namespace, prefix `jn`:

| Code | When |
|---|---|
| `jn:not-a-map-or-array` | `jn:node()` called on something other than a map or array |
| `jn:not-a-jnode` | Any other function called with an argument that isn't a JNode |

## Files

No existing files in `src/main/java/net/sf/saxon/` are modified.

- `src/main/java/net/sf/saxon/fork/jnode/JNodeFunctions.java` —
  fork-add-on: one outer class, one nested static class per function,
  `register(Configuration)`, and an `AutoInit` implementing
  `net.sf.saxon.lib.Initializer`.
- `src/test/java/net/sf/saxon/fork/jnode/JNodeFunctionsTest.java` — 11
  tests covering wrapping, rejection of non-map/array input, root vs.
  child properties, map-key vs. array-index selectors, parent/root
  navigation, leaf nodes, and `jn:is-node`.

Follows the same shape as the EXPath modules
([expath-modules.md](expath-modules.md)): a shared private `JNodeFn`
base class collapses the `ExtensionFunctionDefinition`/
`ExtensionFunctionCall` contract into a single
`eval(Sequence[], XPathContext)` method per nested class.

## Isolation guarantee

Everything lives in `net.sf.saxon.fork.jnode`. No other class in the
codebase references this package. To remove it entirely:

```bash
rm -rf src/main/java/net/sf/saxon/fork/jnode \
       src/test/java/net/sf/saxon/fork/jnode
```

No `pom.xml` dependencies to clean up — pure Java against Saxon's
existing `ma.jnode`/`s9api`/extension-function API, already part of
the vendored 13.0 source.

## Design notes

- **Why `item()` instead of a dedicated JNode type.** Saxon 13's own
  static type for this (`AnyGNodeType` / `gnode()`) only exists at
  language level 40, i.e. behind the same license check this module is
  built to avoid touching. Declaring arguments/results as `item()` and
  doing an `instanceof JNode` check at runtime inside `eval(...)` keeps
  every function's static type-checking path completely independent of
  Saxon's XPath-version machinery — no `SlashExpression`,
  `GNodeSequenceConverter`, or `StaticQueryContext.setLanguageVersion`
  is ever invoked.
- **What "position" means.** `jn:position()` returns
  `JNode.getPosition()` as-is, which is the 1-based position of the
  node's *containing value* within its parent's content sequence —
  not the node's index within a JSON array. For a plain
  `parse-json`-produced tree (where every map/array value is a single
  item, never a multi-item sequence), this is `1` for essentially
  every non-root node. Use `jn:selector()` to get the actual array
  index or map key; `jn:position()` is exposed mainly for completeness
  and for maps/arrays containing genuine multi-item sequence values.
- **`jn:node()` memoizes.** It delegates to
  `MapOrArray.obtainRootJNode()`, which caches one `RootJNode` per
  map/array instance. Calling `jn:node()` twice on the same map value
  returns nodes that satisfy `is` (identity), which is what makes
  `jn:root($b2) is $root` in the example above true.
- **No path-string convenience.** Deliberately out of scope for this
  first pass — see [ENHANCEMENTS.md](../../ENHANCEMENTS.md)'s "prefer
  new files/methods, keep changes minimal" guidance. A `jn:get($node,
  'a/b/2')` convenience would be a natural, purely-additive follow-up
  if the recursive-function style proves too verbose in practice.
