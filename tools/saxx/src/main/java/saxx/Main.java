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
        Path mocksFile
    ) throws Exception {
        Processor processor = new Processor(false);

        if (mocksFile != null) {
            registerMocks(processor, mocksFile);
        }

        XsltCompiler compiler = processor.newXsltCompiler();

        int errors = 0;
        int checked = 0;
        int skipped = 0;

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
                errors += checkFile(processor, compiler, file, deep) ? 0 : 1;
                checked++;
            }
        } else {
            errors += checkFile(processor, compiler, path, deep) ? 0 : 1;
            checked++;
        }

        if (skipped > 0) {
            System.out.printf("%nChecked %d file(s), %d error(s), %d skipped (fragments)%n", checked, errors, skipped);
        } else {
            System.out.printf("%nChecked %d file(s), %d error(s)%n", checked, errors);
        }
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

    /**
     * Register mock extension functions from a JSON file.
     * JSON format:
     * {
     *   "namespace-uri": {
     *     "functionName": returnValue,
     *     ...
     *   },
     *   ...
     * }
     * Where returnValue can be: null, true, false, number, or "string"
     *
     * Note: This only mocks extension FUNCTIONS (XPath calls like ns:func()).
     * Extension ELEMENTS (XSLT instructions like <ns:init/>) cannot be easily
     * mocked in Saxon and will still fail during deep checks.
     */
    private void registerMocks(Processor processor, Path mocksFile) throws Exception {
        String json = Files.readString(mocksFile);
        Configuration config = processor.getUnderlyingConfiguration();

        // Simple JSON parsing for our specific format
        Pattern nsPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{([^}]+)\\}");
        Pattern fnPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(null|true|false|\"[^\"]*\"|[-+]?\\d+\\.?\\d*)");

        Matcher nsMatcher = nsPattern.matcher(json);
        int mockCount = 0;

        while (nsMatcher.find()) {
            String namespace = nsMatcher.group(1);
            String functions = nsMatcher.group(2);

            Matcher fnMatcher = fnPattern.matcher(functions);
            while (fnMatcher.find()) {
                String funcName = fnMatcher.group(1);
                String valueStr = fnMatcher.group(2);

                Object value = parseJsonValue(valueStr);
                config.registerExtensionFunction(new MockExtensionFunction(namespace, funcName, value));
                mockCount++;
            }
        }

        if (mockCount > 0) {
            System.out.printf("Registered %d mock extension function(s)%n", mockCount);
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

    private boolean checkFile(Processor processor, XsltCompiler compiler, Path file, boolean deep) {
        try {
            XsltExecutable executable = compiler.compile(new StreamSource(file.toFile()));

            if (deep) {
                // Attempt a transform with minimal input to catch runtime errors
                String minimalXml = findMinimalXml(file);
                Xslt30Transformer transformer = executable.load30();
                ByteArrayOutputStream devNull = new ByteArrayOutputStream();
                Serializer serializer = processor.newSerializer(devNull);
                StreamSource minimalInput = new StreamSource(new StringReader(minimalXml));
                transformer.transform(minimalInput, serializer);
            }

            System.out.println("OK: " + file);
            return true;
        } catch (SaxonApiException e) {
            System.err.println("FAIL: " + file);
            String msg = e.getMessage();
            if (msg != null) {
                System.err.println("  " + msg);
            }
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().equals(msg)) {
                System.err.println("  " + cause.getMessage());
            }
            return false;
        } catch (Exception e) {
            System.err.println("FAIL: " + file);
            System.err.println("  " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    @Command(name = "transform", description = "Transform XML using XSLT")
    int transform(
        @Option(names = {"-s", "--stylesheet"}, required = true, description = "XSLT stylesheet")
        Path stylesheet,
        @Parameters(paramLabel = "INPUT", description = "Input XML file")
        Path input,
        @Option(names = {"-o", "--output"}, description = "Output file (stdout if omitted)")
        Path output
    ) throws Exception {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(stylesheet.toFile()));
        Xslt30Transformer transformer = executable.load30();

        Serializer serializer = output != null
            ? processor.newSerializer(output.toFile())
            : processor.newSerializer(System.out);

        transformer.transform(new StreamSource(input.toFile()), serializer);
        return 0;
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
