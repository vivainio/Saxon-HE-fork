package net.sf.saxon.fork.expath;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test crossing all four EXPath modules in one XQuery:
 * read text → hash → bundle into ZIP → write archive → read back →
 * verify entries + payload + checksum.
 *
 * Hermetic — runs against a JUnit {@code @TempDir}, no external services.
 */
class ExpathRoundTripIT {

    @TempDir
    Path tmp;

    @Test
    void readHashZipWriteReadVerify() throws Exception {
        Path payload = tmp.resolve("payload.txt");
        Files.writeString(payload, "Saxon EXPath round-trip 2026");

        Processor processor = new Processor(false);
        ExpathExtensions.registerAll(processor.getUnderlyingConfiguration());
        XPathCompiler xp = processor.newXPathCompiler();
        xp.declareNamespace("file", "http://expath.org/ns/file");
        xp.declareNamespace("bin", "http://expath.org/ns/binary");
        xp.declareNamespace("arch", "http://expath.org/ns/archive");
        xp.declareNamespace("crypto", "http://expath.org/ns/crypto");

        String src = payload.toString().replace('\\', '/');
        String zip = tmp.resolve("bundle.zip").toString().replace('\\', '/');

        // (1) read text, (2) hash with SHA-256 hex, (3) bundle text+hash into a
        // ZIP, (4) write archive to disk, (5) read it back, (6) verify entries
        // and contents.
        String xq =
                "let $text := file:read-text('" + src + "')," +
                "    $sha  := crypto:hash($text, 'SHA-256', 'hex')," +
                "    $zip  := arch:create(('payload.txt','SHA256SUMS'), ($text, $sha || '  payload.txt'))," +
                "    $_    := file:write-binary('" + zip + "', $zip)," +
                "    $back := file:read-binary('" + zip + "')," +
                "    $ents := string-join(arch:entries($back), ',')," +
                "    $body := arch:extract-text($back, 'payload.txt')," +
                "    $sum  := arch:extract-text($back, 'SHA256SUMS')," +
                "    $sha2 := crypto:hash($body, 'SHA-256', 'hex') " +
                "return string-join(($ents, $body, $sum, $sha2), '|')";

        XPathSelector sel = xp.compile(xq).load();
        XdmValue result = sel.evaluate();
        String[] parts = result.itemAt(0).getStringValue().split("\\|");

        assertEquals("payload.txt,SHA256SUMS", parts[0], "entry list");
        assertEquals("Saxon EXPath round-trip 2026", parts[1], "payload round-trip");
        assertTrue(parts[2].endsWith("  payload.txt"), "checksum manifest line tail");
        // Re-hash inside the round-trip equals the manifest digest
        String digestFromManifest = parts[2].substring(0, parts[2].indexOf("  "));
        assertEquals(digestFromManifest, parts[3], "manifest hash matches re-hash of extracted body");
        // SHA-256 of a non-empty input is never all zeros
        assertNotEquals("0".repeat(64), parts[3]);

        // And the archive on disk is a real ZIP
        byte[] head = Files.readAllBytes(tmp.resolve("bundle.zip"));
        assertEquals('P', (char) head[0]);
        assertEquals('K', (char) head[1]);
    }
}
