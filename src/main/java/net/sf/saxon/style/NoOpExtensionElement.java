////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Saxon-HE Fork Enhancement: No-op Extension Element
// See ENHANCEMENTS.md for documentation
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trans.XPathException;

/**
 * A no-op extension element that compiles to nothing.
 * Use this when you want extension elements to be silently ignored.
 *
 * Example usage:
 * <pre>
 * config.registerExtensionElementFactory("http://example.com/ext",
 *     localName -> new NoOpExtensionElement());
 * </pre>
 */
public class NoOpExtensionElement extends ExtensionInstruction {

    @Override
    protected void prepareAttributes() {
        // Accept any attributes
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        // No validation needed
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        // Return null = no-op
        return null;
    }

    @Override
    protected boolean mayContainSequenceConstructor() {
        // Allow any content (which will be ignored)
        return true;
    }
}
