# Saxon-HE (Unofficial Fork)

Saxon-HE (Home Edition) is an open-source XSLT 3.0, XQuery 3.1, and XPath 3.1 processor for the Java platform.

## Disclaimer

**This is an unauthorized fork for experimental purposes only.**

There is no promise of compatibility, usability, or maintenance. This repository is based on a snapshot of Saxon-HE 12.9 and may or may not have a life of its own.

Saxon-HE is released under the [Mozilla Public License 2.0](LICENSE), which permits this kind of redistribution and modification.

Saxonica only provides source code as zip files without version history or tests. This repository aims to make it easier to work with the codebase and build your own version.

The source code is intended to match Saxon-HE upstream. Any deviating functionality will be maintained in separate repositories, with the exception of tests, scripts, and documentation.

## Building

```bash
mvn package
```

The JAR will be in `target/Saxon-HE-fork-<version>.jar`.

## Testing

```bash
mvn test
```

This repository includes a test suite which Saxon-HE does not provide:

- **XSLT tests** based on [W3C XSLT 3.0 test patterns](https://github.com/w3c/xslt30-test) - covers core XSLT functionality and XSLT 3.0 features including maps, arrays, try/catch, xsl:iterate, and JSON processing
- **XPath tests** from [W3C QT3 test suite](https://github.com/w3c/qt3tests) - 2600+ XPath function tests (cloned on demand)

For official releases and support, use the [official Saxonica repository](https://github.com/Saxonica/Saxon-HE).

Saxon is developed by [Saxonica](https://www.saxonica.com/).

## Releases

Pre-built JARs are available from [GitHub Releases](https://github.com/vivainio/Saxon-HE-fork/releases).

### Versioning

This fork uses the format `X.Y.Z` where:
- `X.Y` matches the upstream Saxon-HE version (e.g., `12.9`)
- `Z` is the fork release number (e.g., `1`, `2`, `3`)

For example, `12.9.1` is the first release of this fork based on Saxon-HE 12.9.

## License

Saxon-HE is released under the [Mozilla Public License 2.0](LICENSE).

## Documentation

- [Saxonica Documentation](https://www.saxonica.com/documentation12/documentation.xml)
- [Official Saxon-HE Repository](https://github.com/Saxonica/Saxon-HE)
