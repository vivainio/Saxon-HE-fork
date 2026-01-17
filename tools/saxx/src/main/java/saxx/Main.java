package saxx;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.*;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.*;
import java.util.stream.Collectors;

@Command(
    name = "saxx",
    mixinStandardHelpOptions = true,
    version = "saxx 0.1.0",
    description = "XSLT validation and transformation tool powered by Saxon"
)
public class Main implements Callable<Integer> {

    @Command(name = "check", description = "Check/compile XSLT stylesheets")
    int check(
        @Parameters(paramLabel = "PATH", description = "File or directory to check")
        Path path,
        @Option(names = {"-r", "--recursive"}, description = "Recurse into subdirectories")
        boolean recursive,
        @Option(names = {"--skip-fragments"}, description = "Skip files that are imported/included by other stylesheets")
        boolean skipFragments,
        @Option(names = {"--deep"}, description = "Deep check: also attempt transform to catch runtime errors (e.g., unknown extensions)")
        boolean deep,
        @Option(names = {"--mocks"}, description = "JSON file with mock extension function definitions")
        Path mocksFile,
        @Option(names = {"--ignore-extension-elements"}, description = "Treat unknown extension elements (e.g., <service:init/>) as warnings, not errors")
        boolean ignoreExtensionElements
    ) throws Exception {
        Processor processor = new Processor(false);

        if (mocksFile != null) {
            registerMocks(processor, mocksFile);
        }

        XsltCompiler compiler = processor.newXsltCompiler();

        int errors = 0;
        int warnings = 0;
        int checked = 0;
        int skipped = 0;
        int[] result;  // [0] = error (0 or 1), [1] = warning (0 or 1)

        if (Files.isDirectory(path)) {
            int maxDepth = recursive ? Integer.MAX_VALUE : 1;
            List<Path> files = Files.walk(path, maxDepth)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".xsl") || name.endsWith(".xslt");
                })
                .collect(Collectors.toList());

            Set<Path> fragments = skipFragments ? findFragments(files) : Collections.emptySet();

            for (Path file : files) {
                if (fragments.contains(file.toAbsolutePath().normalize())) {
                    System.out.println("SKIP (fragment): " + file);
                    skipped++;
                    continue;
                }
                result = checkFile(processor, compiler, file, deep, ignoreExtensionElements);
                errors += result[0];
                warnings += result[1];
                checked++;
            }
        } else {
            result = checkFile(processor, compiler, path, deep, ignoreExtensionElements);
            errors += result[0];
            warnings += result[1];
            checked++;
        }

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("%nChecked %d file(s), %d error(s)", checked, errors));
        if (warnings > 0) {
            summary.append(String.format(", %d warning(s)", warnings));
        }
        if (skipped > 0) {
            summary.append(String.format(", %d skipped (fragments)", skipped));
        }
        System.out.println(summary);
        return errors > 0 ? 1 : 0;
    }

    private Set<Path> findFragments(List<Path> files) {
        Set<Path> fragments = new HashSet<>();
        Pattern importPattern = Pattern.compile("<xsl:(import|include)\\s+href\\s*=\\s*[\"']([^\"']+)[\"']");

        for (Path file : files) {
            try {
                String content = Files.readString(file);
                Matcher matcher = importPattern.matcher(content);
                while (matcher.find()) {
                    String href = matcher.group(2);
                    Path resolved = file.getParent().resolve(href).toAbsolutePath().normalize();
                    fragments.add(resolved);
                }
            } catch (Exception e) {
                // Ignore files we can't read
            }
        }
        return fragments;
    }

    private Set<String> ignoredElements = new HashSet<>();

    /**
     * Register mock extension functions from a JSON file.
     * JSON format:
     * {
     *   "namespace-uri": {
     *     "_elements": ["init", "otherElement"],  // optional: list of extension elements to ignore
     *     "functionName": returnValue,            // mock function returns
     *     ...
     *   },
     *   ...
     * }
     * Where returnValue can be: null, true, false, number, or "string"
     *
     * The "_elements" array lists extension element local names that should be
     * treated as warnings instead of errors during deep checks.
     */
    private void registerMocks(Processor processor, Path mocksFile) throws Exception {
        String json = Files.readString(mocksFile);
        Configuration config = processor.getUnderlyingConfiguration();

        // Simple JSON parsing for our specific format
        Pattern nsPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{([^}]+)\\}");
        Pattern fnPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(null|true|false|\"[^\"]*\"|[-+]?\\d+\\.?\\d*)");
        Pattern elemListPattern = Pattern.compile("\"_elements\"\\s*:\\s*\\[([^\\]]+)\\]");
        Pattern elemNamePattern = Pattern.compile("\"([^\"]+)\"");

        Matcher nsMatcher = nsPattern.matcher(json);
        int mockCount = 0;

        while (nsMatcher.find()) {
            String namespace = nsMatcher.group(1);
            String content = nsMatcher.group(2);

            // Check for _elements: ["elem1", "elem2"] directive
            Matcher elemListMatcher = elemListPattern.matcher(content);
            if (elemListMatcher.find()) {
                String elemList = elemListMatcher.group(1);
                Matcher elemNameMatcher = elemNamePattern.matcher(elemList);
                while (elemNameMatcher.find()) {
                    ignoredElements.add(elemNameMatcher.group(1));
                }
            }

            Matcher fnMatcher = fnPattern.matcher(content);
            while (fnMatcher.find()) {
                String funcName = fnMatcher.group(1);
                if (funcName.startsWith("_")) {
                    continue;  // Skip directives like _elements
                }
                String valueStr = fnMatcher.group(2);

                Object value = parseJsonValue(valueStr);
                config.registerExtensionFunction(new MockExtensionFunction(namespace, funcName, value));
                mockCount++;
            }
        }

        if (mockCount > 0 || !ignoredElements.isEmpty()) {
            System.out.printf("Registered %d mock function(s), %d ignored element(s)%n",
                mockCount, ignoredElements.size());
        }
    }

    private Object parseJsonValue(String valueStr) {
        if (valueStr.equals("null")) {
            return null;
        } else if (valueStr.equals("true")) {
            return Boolean.TRUE;
        } else if (valueStr.equals("false")) {
            return Boolean.FALSE;
        } else if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
            return valueStr.substring(1, valueStr.length() - 1);
        } else if (valueStr.contains(".")) {
            return Double.parseDouble(valueStr);
        } else {
            return Long.parseLong(valueStr);
        }
    }

    private static final Pattern ROOT_TEMPLATE_PATTERN = Pattern.compile(
        "<xsl:template[^>]+match\\s*=\\s*[\"']([^\"'/][^\"']*)[\"']",
        Pattern.MULTILINE
    );

    private String findMinimalXml(Path file) {
        try {
            String content = Files.readString(file);
            Matcher m = ROOT_TEMPLATE_PATTERN.matcher(content);
            if (m.find()) {
                String match = m.group(1).trim();
                // Extract simple element name from match pattern
                // Handle patterns like "Invoice", "BusinessDocument", "ns:Invoice", etc.
                String elemName = match.replaceAll("^[a-zA-Z0-9_-]+:", "")  // remove namespace prefix
                                       .replaceAll("[\\[\\]|@*].*", "")     // remove predicates/wildcards
                                       .trim();
                if (!elemName.isEmpty() && elemName.matches("[a-zA-Z_][a-zA-Z0-9_-]*")) {
                    return "<" + elemName + "/>";
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return "<_/>";
    }

    private static final int[] OK = {0, 0};
    private static final int[] ERROR = {1, 0};
    private static final int[] WARNING = {0, 1};

    private int[] checkFile(Processor processor, XsltCompiler compiler, Path file, boolean deep, boolean ignoreExtensionElements) {
        try {
            XsltExecutable executable = compiler.compile(new StreamSource(file.toFile()));

            if (deep) {
                // Attempt a transform with minimal input to catch runtime errors
                String minimalXml = findMinimalXml(file);
                Xslt30Transformer transformer = executable.load30();
                // Suppress error output if we have ignored elements (we'll handle errors ourselves)
                if (!ignoredElements.isEmpty() || ignoreExtensionElements) {
                    transformer.setErrorReporter(err -> {});  // Suppress Saxon's error output
                }
                ByteArrayOutputStream devNull = new ByteArrayOutputStream();
                Serializer serializer = processor.newSerializer(devNull);
                StreamSource minimalInput = new StreamSource(new StringReader(minimalXml));
                transformer.transform(minimalInput, serializer);
            }

            System.out.println("OK: " + file);
            return OK;
        } catch (SaxonApiException e) {
            String msg = e.getMessage();
            // Check if this is an extension element error that should be treated as warning
            if (msg != null && msg.contains("Unknown extension instruction")) {
                boolean shouldIgnore = ignoreExtensionElements;
                String localName = null;
                // Extract element name from error: "Unknown extension instruction <prefix:localname>"
                Pattern p = Pattern.compile("<([^:>]+:)?([^>]+)>");
                Matcher m = p.matcher(msg);
                if (m.find()) {
                    localName = m.group(2);
                    if (!shouldIgnore && !ignoredElements.isEmpty()) {
                        shouldIgnore = ignoredElements.contains(localName);
                    }
                }
                if (shouldIgnore) {
                    // Silently pass - element is mocked/ignored
                    System.out.println("OK: " + file);
                    return OK;
                }
                // Show hint about adding to mocks
                System.err.println("FAIL: " + file);
                System.err.println("  " + msg);
                if (localName != null) {
                    System.err.println("  Hint: to ignore this element, add \"" + localName + "\" to _elements array in mocks JSON");
                }
                return ERROR;
            }
            System.err.println("FAIL: " + file);
            if (msg != null) {
                System.err.println("  " + msg);
            }
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().equals(msg)) {
                System.err.println("  " + cause.getMessage());
            }
            return ERROR;
        } catch (Exception e) {
            System.err.println("FAIL: " + file);
            System.err.println("  " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ERROR;
        }
    }

    @Command(name = "transform", description = "Transform XML using XSLT")
    int transform(
        @Option(names = {"-s", "--stylesheet"}, required = true, description = "XSLT stylesheet")
        Path stylesheet,
        @Parameters(paramLabel = "INPUT", description = "Input XML file")
        Path input,
        @Option(names = {"-o", "--output"}, description = "Output file (stdout if omitted)")
        Path output,
        @Option(names = {"--trace"}, description = "Trace XSLT execution (node, instruction, location)")
        boolean trace
    ) throws Exception {
        Processor processor = new Processor(false);

        if (trace) {
            processor.getUnderlyingConfiguration().setCompileWithTracing(true);
        }

        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(stylesheet.toFile()));
        Xslt30Transformer transformer = executable.load30();

        if (trace) {
            transformer.setTraceListener(new CompactTraceListener(System.err));
        }

        Serializer serializer = output != null
            ? processor.newSerializer(output.toFile())
            : processor.newSerializer(System.out);

        transformer.transform(new StreamSource(input.toFile()), serializer);
        return 0;
    }

    @Command(name = "map", description = "Extract all XPath paths and values from XML")
    int map(
        @Parameters(paramLabel = "INPUT", description = "Input XML file")
        Path input,
        @Option(names = {"--include-text"}, description = "Include text nodes")
        boolean includeText,
        @Option(names = {"--include-attrs"}, description = "Include attributes (default: true)", defaultValue = "true", negatable = true)
        boolean includeAttrs
    ) throws Exception {
        Processor processor = new Processor(false);
        DocumentBuilder builder = processor.newDocumentBuilder();
        XdmNode doc = builder.build(input.toFile());

        System.out.println("# XPath mapping for: " + input.getFileName());
        System.out.println("# xpath\ttype\tvalue");

        walkNode(doc, includeText, includeAttrs);
        return 0;
    }

    private void walkNode(XdmNode node, boolean includeText, boolean includeAttrs) {
        net.sf.saxon.om.NodeInfo info = node.getUnderlyingNode();
        String xpath = net.sf.saxon.tree.util.Navigator.getPath(info);
        int kind = info.getNodeKind();

        // Output this node
        if (kind == net.sf.saxon.type.Type.ELEMENT) {
            String directText = getDirectTextContent(node);
            System.out.printf("%s\telem\t%s%n", xpath, truncate(directText, 80));

            // Output attributes
            if (includeAttrs) {
                for (XdmNode attr : node.children()) {
                    // No direct way to iterate attrs in s9api, use underlying
                }
                net.sf.saxon.om.AttributeMap attrs = info.attributes();
                for (net.sf.saxon.om.AttributeInfo attr : attrs) {
                    String attrPath = xpath + "/@" + attr.getNodeName().getLocalPart();
                    System.out.printf("%s\tattr\t%s%n", attrPath, truncate(attr.getValue(), 80));
                }
            }
        } else if (kind == net.sf.saxon.type.Type.TEXT && includeText) {
            String val = info.getStringValue().trim();
            if (!val.isEmpty()) {
                System.out.printf("%s\ttext\t%s%n", xpath, truncate(val, 80));
            }
        } else if (kind == net.sf.saxon.type.Type.DOCUMENT) {
            // Skip document node output, just recurse
        }

        // Recurse to children (elements only in s9api iteration)
        for (XdmNode child : node.children()) {
            walkNode(child, includeText, includeAttrs);
        }
    }

    private String getDirectTextContent(XdmNode elem) {
        StringBuilder sb = new StringBuilder();
        for (XdmNode child : elem.children()) {
            if (child.getUnderlyingNode().getNodeKind() == net.sf.saxon.type.Type.TEXT) {
                sb.append(child.getStringValue());
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
