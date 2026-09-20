package net.sf.saxon.extensions.xquery;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.PrincipalStylesheetModule;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.VisibilityProvenance;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compile-time representation of {@code <saxon:import-query>} in a
 * stylesheet. Imports the public functions and variables of an XQuery
 * library module into the stylesheet's static function scope, so that
 * the stylesheet can call them as ordinary XPath function calls.
 *
 * <p>This mirrors the Saxon-PE/EE extension element of the same name.
 * Attributes:</p>
 * <ul>
 *   <li>{@code namespace} (required): the module namespace of the
 *       XQuery library module to import.</li>
 *   <li>{@code href} (optional): location of the library module
 *       source. In Saxon-HE this is required, because pre-compiled
 *       XQuery libraries via {@code XsltCompiler.importXQueryEnvironment}
 *       depend on {@code StaticQueryContext.compileLibrary}, which is
 *       only implemented in Saxon-EE.</li>
 * </ul>
 *
 * <p>Implementation: at {@code index} time we compile a synthetic main
 * module {@code "import module namespace x = '<ns>' at '<href>'; ()"}
 * via the existing XQuery machinery already shipped in Saxon-HE. That
 * populates the executable's query-library module list as a side
 * effect. We then walk each imported module and copy its non-private
 * {@link XQueryFunction} declarations into the stylesheet package's
 * {@link XQueryFunctionLibrary} (the same slot already installed by
 * {@code StylesheetPackage.createFunctionLibrary} at chain position
 * for XQuery-imported functions). After that, XPath function calls in
 * the stylesheet bind to those declarations through the normal
 * function-library lookup path — no per-call overhead.</p>
 *
 * <p><strong>Library cache.</strong> Library compiles are cached
 * per-Configuration keyed by {@code (namespace, absolute-href, mtime)}.
 * Across N stylesheets that import the same library in the same JVM,
 * the XQuery parser runs only once for an unchanged source file. The
 * cache is keyed by {@link Configuration} via a {@link WeakHashMap} so
 * shutting down a Configuration releases its libraries. For
 * non-{@code file:} URIs we use mtime {@code -1} and assume the
 * resource is immutable for the JVM lifetime; volatile remote
 * libraries should not use this mechanism.</p>
 *
 * <p>Cached {@link UserFunction}s are shared across stylesheet
 * packages, but each importing package gets its own {@link Component}
 * wrapper so the per-package component index, visibility, and
 * binding-slot machinery stays clean. Sharing the function body is
 * safe because {@code allocateAllBindingSlots} only recurses into
 * actors whose {@code packageData.isXSLT()} is true — these come from
 * XQuery, so XSLT's binding-slot allocator skips them entirely.</p>
 */
public final class SaxonImportQuery extends StyleElement {

    /**
     * Per-{@link Configuration} cache of compiled library modules. The
     * outer map is weak-keyed so dropping a Configuration releases its
     * cache. The inner map is concurrent because Configurations may be
     * driving compiles from multiple threads.
     */
    private static final Map<Configuration, Map<CacheKey, List<QueryModule>>> CACHE =
            new WeakHashMap<>();

    private String namespaceAttr;
    private String hrefAttr;

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    protected void prepareAttributes() {
        readImportAttributes();
        if (namespaceAttr == null || namespaceAttr.isEmpty()) {
            compileError("saxon:import-query requires a non-empty 'namespace' attribute");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
        checkEmpty();
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        // index() runs before processAllAttributes(), so we read attributes here.
        readImportAttributes();
        if (namespaceAttr == null || namespaceAttr.isEmpty()) {
            compileError("saxon:import-query requires a non-empty 'namespace' attribute");
            return;
        }
        if (hrefAttr == null || hrefAttr.isEmpty()) {
            compileError(
                    "saxon:import-query in Saxon-HE requires an 'href' attribute. " +
                            "Pre-compiled XQuery libraries (via " +
                            "XsltCompiler.importXQueryEnvironment) are not supported " +
                            "in this build.");
            return;
        }

        NamespaceUri moduleUri = NamespaceUri.of(namespaceAttr);
        StylesheetPackage pack = getContainingPackage();
        XQueryFunctionLibrary target = pack.getXQueryFunctionLibrary();

        // Saxon-EE rule: if the same module namespace is imported more
        // than once, only the first declaration takes effect.
        for (XQueryFunction existing : target.getFunctionDefinitions()) {
            if (existing.getFunctionName().getNamespaceUri().equals(moduleUri)) {
                return;
            }
        }

        List<QueryModule> modules = obtainLibrary(moduleUri);
        if (modules == null) {
            return; // error already reported
        }

        for (QueryModule module : modules) {
            // Imported (non-main) library modules expose their own
            // declarations via the local function library; the global
            // library is null on these because it only exists on the
            // top-level main module.
            XQueryFunctionLibrary moduleFns = module.getLocalFunctionLibrary();
            if (moduleFns == null) {
                continue;
            }
            for (XQueryFunction fn : moduleFns.getFunctionDefinitions()) {
                if (fn.isPrivate()) {
                    continue;
                }
                if (!fn.getFunctionName().getNamespaceUri().equals(moduleUri)) {
                    continue;
                }
                target.declareFunction(fn);

                // XSLT's link phase resolves every UserFunctionCall via the
                // package's component index. Register each compiled
                // UserFunction as a component in this package.
                //
                // We construct the Component directly (rather than calling
                // uf.makeDeclaringComponent, which caches a single component
                // on the Actor) so a cached library shared across packages
                // can give each importing package its own Component wrapper.
                UserFunction uf = fn.getUserFunction();
                if (uf != null) {
                    // Per-package Component wrapper: each importing
                    // package needs its own entry in its componentIndex,
                    // since the index is per-package and addComponent
                    // does not look at the actor's cached declaringComponent.
                    Component perPkg = Component.makeComponent(
                            uf, Visibility.PRIVATE, VisibilityProvenance.DEFAULTED,
                            pack, pack);
                    pack.addComponent(perPkg);
                    // The Actor.declaringComponent field is single-slot.
                    // The XSLT link phase reads it to gate recursion in
                    // allocateAllBindingSlots, which is itself gated on
                    // packageData.isXSLT() — false for XQuery functions —
                    // so the value only needs to be non-null. We populate
                    // it on first sight from this package; later imports
                    // (e.g. from cache, into a different package) leave
                    // the cached value alone.
                    if (uf.getDeclaringComponent() == null) {
                        uf.setDeclaringComponent(perPkg);
                    }
                }
            }
        }
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) {
        // No-op: all effect is at index() time.
    }

    private void readImportAttributes() {
        if (namespaceAttr != null || hrefAttr != null) {
            return;
        }
        for (AttributeInfo att : attributes()) {
            String name = att.getNodeName().getDisplayName();
            String value = att.getValue();
            if ("namespace".equals(name)) {
                namespaceAttr = Whitespace.trim(value);
            } else if ("href".equals(name)) {
                hrefAttr = Whitespace.trim(value);
            }
        }
    }

    /**
     * Return the imported library's {@link QueryModule} list, hitting
     * the per-Configuration cache when the source is unchanged. On
     * cache miss, compiles via a synthetic main module and stores the
     * result. Reports compile errors via {@link #compileError} and
     * returns {@code null}.
     */
    private List<QueryModule> obtainLibrary(NamespaceUri moduleUri) {
        Configuration config = getConfiguration();
        URI absoluteHref = resolveHref();
        long mtime = mtimeOrMinusOne(absoluteHref);
        CacheKey key = new CacheKey(moduleUri,
                absoluteHref == null ? hrefAttr : absoluteHref.toString(),
                mtime);

        Map<CacheKey, List<QueryModule>> perConfig;
        synchronized (CACHE) {
            perConfig = CACHE.computeIfAbsent(config, c -> new ConcurrentHashMap<>());
        }
        List<QueryModule> cached = perConfig.get(key);
        if (cached != null) {
            return cached;
        }

        List<QueryModule> compiled = compileLibrary(moduleUri);
        if (compiled == null) {
            return null;
        }
        // Store an immutable copy. ConcurrentHashMap.put — last writer
        // wins; benign if two threads race on the same key.
        List<QueryModule> snapshot = new ArrayList<>(compiled);
        perConfig.put(key, snapshot);
        return snapshot;
    }

    /**
     * Compile the synthetic main module {@code "import module namespace
     * m = '<ns>' at '<href>'; ()"} and return the imported library's
     * {@link QueryModule}s for {@code moduleUri}.
     */
    private List<QueryModule> compileLibrary(NamespaceUri moduleUri) {
        XQueryExpression compiled;
        try {
            StaticQueryContext sqc = getConfiguration().newStaticQueryContext();
            String stylesheetBase = getBaseURI();
            if (stylesheetBase != null && !stylesheetBase.isEmpty()) {
                sqc.setBaseURI(stylesheetBase);
            }
            String synthetic =
                    "import module namespace m = \"" + escapeXQ(namespaceAttr) + "\"" +
                            " at \"" + escapeXQ(hrefAttr) + "\";\n" +
                            "()";
            compiled = sqc.compileQuery(synthetic);
        } catch (XPathException e) {
            compileError(
                    "Failed to import XQuery module {" + namespaceAttr + "}" +
                            " at " + hrefAttr + ": " + e.getMessage(),
                    "XTSE0165");
            return null;
        }

        Executable xqExec = compiled.getExecutable();
        List<QueryModule> modules = xqExec.getQueryLibraryModules(moduleUri);
        if (modules == null || modules.isEmpty()) {
            compileError(
                    "XQuery module at " + hrefAttr +
                            " did not declare the expected module namespace " +
                            namespaceAttr,
                    "XTSE0165");
            return null;
        }
        return modules;
    }

    /** Resolve {@link #hrefAttr} against the stylesheet's base URI. */
    private URI resolveHref() {
        try {
            URI rel = new URI(hrefAttr);
            String base = getBaseURI();
            if (base == null || base.isEmpty() || rel.isAbsolute()) {
                return rel.isAbsolute() ? rel : null;
            }
            return new URI(base).resolve(rel);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * mtime of the resource at {@code u} in milliseconds, or {@code -1}
     * if {@code u} is null, not a {@code file:} URI, or unreadable.
     * Non-file URIs are treated as immutable for the JVM lifetime —
     * volatile remote libraries should not use this caching path.
     */
    private static long mtimeOrMinusOne(URI u) {
        if (u == null || !"file".equalsIgnoreCase(u.getScheme())) {
            return -1L;
        }
        try {
            Path p = Paths.get(u);
            if (Files.isReadable(p)) {
                return Files.getLastModifiedTime(p).toMillis();
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through
        }
        return -1L;
    }

    /** Escape a string for safe inclusion inside an XQuery double-quoted literal. */
    private static String escapeXQ(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append("\"\"");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class CacheKey {
        final NamespaceUri namespace;
        final String href;
        final long mtime;

        CacheKey(NamespaceUri namespace, String href, long mtime) {
            this.namespace = namespace;
            this.href = href;
            this.mtime = mtime;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) o;
            return mtime == other.mtime
                    && namespace.equals(other.namespace)
                    && Objects.equals(href, other.href);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, href, mtime);
        }
    }
}
