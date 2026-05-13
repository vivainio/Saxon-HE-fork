# Saxon-HE Fork Enhancements

This document indexes all modifications made to upstream Saxon-HE source
code. Each enhancement has its own page under [`docs/enhancements/`](docs/enhancements/)
with full design notes, file lists, usage examples, and tests.

## Index

| Enhancement | Status | Doc |
|---|---|---|
| Extension Element Factory Support | Implemented | [extension-element-factory.md](docs/enhancements/extension-element-factory.md) |
| Dynamic Evaluate Extension Functions (`sk:evaluate`, `saxon:evaluate`) | Implemented | [dynamic-evaluate.md](docs/enhancements/dynamic-evaluate.md) |

## How to Document New Enhancements

When modifying upstream Saxon code or adding fork-only features:

1. Add a new page under `docs/enhancements/<short-slug>.md` describing:
   - Purpose of the change.
   - Files added / modified (with paths and line numbers where helpful).
   - Usage examples.
   - Design / implementation details, including any surprises that future
     maintainers should know about.
2. Add a one-line entry to the **Index** table above.
3. Keep code changes minimal and focused.
4. Prefer adding new files / new methods over modifying existing ones —
   it dramatically reduces upstream-merge friction.
5. Mark in-code changes to existing upstream files with comments like
   `// Fork enhancement: ...` so they're greppable.
