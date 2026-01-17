package net.sf.saxon;

import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized XSLT test suite that runs tests from the test catalog.
 */
class XsltTest {

    private static Processor processor;
    private static Path testResourcesPath;

    @BeforeAll
    static void setup() {
        processor = new Processor(false);
        testResourcesPath = Paths.get("src/test/resources/xslt-tests");
    }

    @TestFactory
    Stream<DynamicTest> xsltTests() throws Exception {
        Path catalogPath = testResourcesPath.resolve("test-catalog.xml");
        List<TestCase> testCases = parseCatalog(catalogPath);

        return testCases.stream().map(tc ->
            DynamicTest.dynamicTest(tc.name + ": " + tc.description, () -> runTest(tc))
        );
    }

    private void runTest(TestCase testCase) throws Exception {
        Path stylesheetPath = testResourcesPath.resolve(testCase.stylesheet);
        Path sourcePath = testResourcesPath.resolve(testCase.source);
        Path expectedPath = testResourcesPath.resolve(testCase.expected);

        // Compile stylesheet
        XsltCompiler compiler = processor.newXsltCompiler();
        XsltExecutable executable = compiler.compile(new StreamSource(stylesheetPath.toFile()));
        Xslt30Transformer transformer = executable.load30();

        // Transform
        StringWriter output = new StringWriter();
        Serializer serializer = processor.newSerializer(output);
        transformer.transform(new StreamSource(sourcePath.toFile()), serializer);

        // Compare results
        String actual = normalizeXml(output.toString());
        String expected = normalizeXml(Files.readString(expectedPath));

        assertEquals(expected, actual, "Output mismatch for test: " + testCase.name);
    }

    private String normalizeXml(String xml) {
        // Basic normalization: trim whitespace
        return xml.trim().replaceAll(">\\s+<", "><");
    }

    private List<TestCase> parseCatalog(Path catalogPath) throws Exception {
        List<TestCase> tests = new ArrayList<>();

        DocumentBuilder builder = processor.newDocumentBuilder();
        XdmNode doc = builder.build(catalogPath.toFile());

        XPathCompiler xpath = processor.newXPathCompiler();
        XdmValue testNodes = xpath.evaluate("//test", doc);

        for (XdmItem item : testNodes) {
            XdmNode testNode = (XdmNode) item;
            TestCase tc = new TestCase();
            tc.name = xpath.evaluateSingle("@name", testNode).getStringValue();
            tc.description = xpath.evaluateSingle("@description", testNode).getStringValue();
            tc.stylesheet = xpath.evaluateSingle("stylesheet", testNode).getStringValue();
            tc.source = xpath.evaluateSingle("source", testNode).getStringValue();
            tc.expected = xpath.evaluateSingle("expected", testNode).getStringValue();
            tests.add(tc);
        }

        return tests;
    }

    private static class TestCase {
        String name;
        String description;
        String stylesheet;
        String source;
        String expected;
    }
}
