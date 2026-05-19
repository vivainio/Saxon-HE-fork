package net.sf.saxon.fork.expath;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Initializer;

import javax.xml.transform.TransformerException;

/**
 * Optional, best-effort wiring for EXPath function modules on Saxon HE.
 *
 * <p>Copyright © Ville Vainio. Licensed under the Mozilla Public License
 * 2.0, same as the rest of this fork.</p>
 *
 * This is a fork add-on. Everything in the {@code net.sf.saxon.fork.expath}
 * package can be deleted without affecting any other Saxon functionality —
 * no other class in the codebase references it.
 *
 * Currently wires up:
 * <ul>
 *   <li>EXPath File module ({@code http://expath.org/ns/file}) — native
 *       implementation in {@link ExpathFileFunctions}. Pure Java, no
 *       third-party dependencies.</li>
 * </ul>
 *
 * The HTTP-client, binary, and archive modules are intentionally <em>not</em>
 * bundled here: they require external jars (or have no maintained Saxon-12
 * OSS binding) and would bloat the fat-jar. Callers who want those modules
 * can wire them in separately against this Saxon HE fork.
 */
public final class ExpathExtensions {

    private ExpathExtensions() {}

    /**
     * Register all bundled EXPath extension functions on the given
     * Configuration.
     *
     * @return the number of function modules successfully registered
     */
    public static int registerAll(Configuration config) {
        ExpathFileFunctions.register(config);
        return 1;
    }

    /**
     * Saxon {@link Initializer} that calls {@link #registerAll(Configuration)}.
     * Enable by setting the Saxon config property
     * {@code http://saxon.sf.net/feature/initializer} to
     * {@code net.sf.saxon.fork.expath.ExpathExtensions$AutoInit}.
     */
    public static final class AutoInit implements Initializer {
        @Override
        public void initialize(Configuration config) throws TransformerException {
            registerAll(config);
        }
    }
}
