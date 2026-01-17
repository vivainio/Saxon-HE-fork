# Saxon-HE (Unofficial Fork)

Saxon-HE (Home Edition) is an open-source XSLT 3.0, XQuery 3.1, and XPath 3.1 processor for the Java platform.

## Disclaimer

**This is an unauthorized fork for experimental purposes only.**

There is no promise of compatibility, usability, or maintenance. This repository is based on a snapshot of Saxon-HE 12.9 and may or may not have a life of its own.

Saxon-HE is released under the [Mozilla Public License 2.0](LICENSE), which permits this kind of redistribution and modification.

Saxonica only provides source code as zip files without version history or tests. This repository aims to make it easier to work with the codebase and build your own version.

## Building

```bash
mvn package
```

The JAR will be in `target/Saxon-HE-fork-12.9-SNAPSHOT.jar`.

## Testing

```bash
mvn test
```

This repository includes a test suite based on [W3C XSLT 3.0 test patterns](https://github.com/w3c/xslt30-test), which Saxon-HE does not provide. Tests cover core XSLT functionality and XSLT 3.0 features including maps, arrays, try/catch, xsl:iterate, and JSON processing.

For official releases and support, use the [official Saxonica repository](https://github.com/Saxonica/Saxon-HE).

Saxon is developed by [Saxonica](https://www.saxonica.com/).

## License

Saxon-HE is released under the [Mozilla Public License 2.0](LICENSE).

## Documentation

- [Saxonica Documentation](https://www.saxonica.com/documentation12/documentation.xml)
- [Official Saxon-HE Repository](https://github.com/Saxonica/Saxon-HE)
