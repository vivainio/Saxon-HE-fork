# Extension Element Factory Support

**Status:** Implemented

## Purpose

Allow registration of custom extension element handlers, enabling mocked or
real implementations of extension elements like `<service:init/>`.

## Files modified

- `src/main/java/net/sf/saxon/style/ExtensionElementFactory.java` — New
  interface for creating extension elements.
- `src/main/java/net/sf/saxon/style/NoOpExtensionElement.java` — No-op
  implementation for ignoring elements.
- `src/main/java/net/sf/saxon/Configuration.java` — Added factory
  registration (as of Saxon 13.0.1: field at line 206, methods at
  lines 3775-3792).
- `src/main/java/net/sf/saxon/style/StyleNodeFactory.java` — Use
  registered factories (as of Saxon 13.0.1: lines 242-256, 271-272).

## Usage

```java
// Register a no-op handler for all elements in a namespace
Configuration config = processor.getUnderlyingConfiguration();
config.registerExtensionElementFactory("xalan://com.example.extensions",
    localName -> new NoOpExtensionElement());

// Register a custom handler for specific elements
config.registerExtensionElementFactory("http://example.com/ext",
    localName -> {
        if ("init".equals(localName)) {
            return new MyInitElement();
        }
        return new NoOpExtensionElement();  // default for others
    });
```

## Design

1. `ExtensionElementFactory` interface with `makeExtensionElement(String localName)` method.
2. `Configuration` stores a `Map<String, ExtensionElementFactory>` for namespace → factory.
3. `StyleNodeFactory.makeElementNode()` checks for registered factory before creating `AbsentExtensionElement`.
4. If factory returns non-null, the element is used without setting a validation error.
5. `NoOpExtensionElement` provides a simple no-op implementation that compiles to null.

## Tests

`src/test/java/net/sf/saxon/ExtensionElementFactoryTest.java` — 8 tests
covering registration, no-op elements, multiple namespaces, and error cases.
