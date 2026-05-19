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
 *       implementation in {@link ExpathFileFunctions}.</li>
 *   <li>EXPath Binary module ({@code http://expath.org/ns/binary}) — native
 *       implementation in {@link ExpathBinaryFunctions}.</li>
 *   <li>EXPath Archive (ZIP) module ({@code http://expath.org/ns/archive}) —
 *       native implementation in {@link ExpathArchiveFunctions}.</li>
 *   <li>EXPath Crypto hash + HMAC subset ({@code http://expath.org/ns/crypto})
 *       — native implementation in {@link ExpathCryptoFunctions}.</li>
 * </ul>
 *
 * <p>All modules are pure Java, no third-party dependencies. The HTTP-client
 * module is intentionally <em>not</em> bundled — it has a larger surface
 * (multipart, auth, response dispatching) and is left for a future pass.</p>
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
        ExpathBinaryFunctions.register(config);
        ExpathArchiveFunctions.register(config);
        ExpathCryptoFunctions.register(config);
        return 4;
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
