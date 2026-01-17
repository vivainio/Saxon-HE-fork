////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Saxon-HE Fork Enhancement: Extension Element Factory Support
// See ENHANCEMENTS.md for documentation
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.NodeName;

/**
 * Factory interface for creating custom extension element handlers.
 * Register implementations with Configuration.registerExtensionElementFactory().
 */
public interface ExtensionElementFactory {

    /**
     * Create a StyleElement to handle an extension element.
     *
     * @param localName the local name of the element (without namespace prefix)
     * @return a StyleElement subclass to handle this element, or null to use default (AbsentExtensionElement)
     */
    StyleElement makeExtensionElement(String localName);
}
