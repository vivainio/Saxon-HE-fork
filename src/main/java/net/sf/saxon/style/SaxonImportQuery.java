package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.util.List;

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
 */
public final class SaxonImportQuery extends StyleElement {

    private String namespaceAttr;
    private String hrefAttr;

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    protected void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String name = attName.getDisplayName();
            String value = att.getValue();
            if ("namespace".equals(name)) {
                namespaceAttr = Whitespace.trim(value);
            } else if ("href".equals(name)) {
                hrefAttr = Whitespace.trim(value);
            } else {
                checkUnknownAttribute(attName);
            }
        }
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
        if (namespaceAttr == null) {
            // prepareAttributes will populate fields; defer if not yet run.
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
        if (namespaceAttr == null || namespaceAttr.isEmpty()) {
            compileError("saxon:import-query requires a non-empty 'namespace' attribute");
            return;
        }
        if (hrefAttr == null || hrefAttr.isEmpty()) {
            // Saxon-EE supports pre-compiled libraries via importXQueryEnvironment.
            // That path requires StaticQueryContext.compileLibrary, which is
            // EE-only. In Saxon-HE href is therefore required.
            compileError(
                    "saxon:import-query in Saxon-HE requires an 'href' attribute. " +
                            "Pre-compiled XQuery libraries (via " +
                            "XsltCompiler.importXQueryEnvironment) are not supported " +
                            "in this build.");
            return;
        }

        NamespaceUri moduleUri = NamespaceUri.of(namespaceAttr);

        // De-dup: if the stylesheet's XQueryFunctionLibrary already has a
        // function in this namespace, treat this declaration as a no-op
        // (matches the Saxon-EE rule: first import wins).
        XQueryFunctionLibrary target = getContainingPackage().getXQueryFunctionLibrary();
        for (XQueryFunction existing : target.getFunctionDefinitions()) {
            if (existing.getFunctionName().getNamespaceUri().equals(moduleUri)) {
                return;
            }
        }

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
            return;
        }

        Executable xqExec = compiled.getExecutable();
        List<QueryModule> modules = xqExec.getQueryLibraryModules(moduleUri);
        if (modules == null || modules.isEmpty()) {
            compileError(
                    "XQuery module at " + hrefAttr +
                            " did not declare the expected module namespace " +
                            namespaceAttr,
                    "XTSE0165");
            return;
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
                // Skip %private (not visible across module boundaries) and
                // anything not in the imported namespace.
                if (fn.isPrivate()) {
                    continue;
                }
                if (!fn.getFunctionName().getNamespaceUri().equals(moduleUri)) {
                    continue;
                }
                target.declareFunction(fn);

                // XSLT's link phase resolves every UserFunctionCall via the
                // package's component index. The XQuery compile produced a
                // UserFunction for each library declaration; register it
                // as a component in the stylesheet package so that calls
                // from the stylesheet bind at link time.
                UserFunction uf = fn.getUserFunction();
                if (uf != null) {
                    Component comp = uf.makeDeclaringComponent(Visibility.PRIVATE, getContainingPackage());
                    getContainingPackage().addComponent(comp);
                }
            }
        }
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) {
        // No-op: all effect is at index() time.
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
}
