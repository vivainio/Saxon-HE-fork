package saxx;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.expr.Expression;

import java.io.PrintStream;
import java.util.*;

/**
 * Compact YAML-like trace output with all instruction info.
 */
public class CompactTraceListener implements TraceListener {
    private final PrintStream out;
    private int depth = 0;
    private String currentNode = null;

    public CompactTraceListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void setOutputDestination(net.sf.saxon.lib.Logger stream) {}

    @Override
    public void open(net.sf.saxon.Controller controller) {}

    @Override
    public void close() {}

    @Override
    public void enter(Traceable traceable, Map<String, Object> properties, XPathContext context) {
        try {
            String node = null;
            Item contextItem = context.getContextItem();
            if (contextItem instanceof NodeInfo) {
                node = Navigator.getPath((NodeInfo) contextItem);
            }

            // Get location info
            Location loc = traceable.getLocation();
            String module = getShortModule(loc.getSystemId());
            int line = loc.getLineNumber();

            // Get instruction type and details
            String type = getInstructionType(traceable);
            String detail = getInstructionDetail(traceable);

            // Print node change
            if (node != null && !node.equals(currentNode)) {
                indent();
                out.printf("%s:%n", node);
                currentNode = node;
            }

            // Print instruction
            indent();
            if (detail != null && !detail.isEmpty()) {
                out.printf("  %s %s  # %s:%d%n", type, detail, module, line);
            } else {
                out.printf("  %s  # %s:%d%n", type, module, line);
            }

            depth++;
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void leave(Traceable traceable) {
        if (depth > 0) depth--;
    }

    @Override
    public void startCurrentItem(Item item) {
        if (item instanceof NodeInfo) {
            String node = Navigator.getPath((NodeInfo) item);
            if (!node.equals(currentNode)) {
                indent();
                out.printf("%s:%n", node);
                currentNode = node;
            }
        }
    }

    @Override
    public void endCurrentItem(Item item) {}

    @Override
    public void startRuleSearch() {}

    @Override
    public void endRuleSearch(Object rule, Mode mode, Item item) {}

    private void indent() {
        int spaces = Math.min(depth, 20);  // cap indentation
        for (int i = 0; i < spaces; i++) {
            out.print("  ");
        }
    }

    private String getInstructionType(Traceable t) {
        if (t instanceof TemplateRule) return "template";
        if (t instanceof ApplyTemplates) return "apply-templates";
        if (t instanceof CallTemplate) return "call-template";
        if (t instanceof ForEach) return "for-each";
        if (t instanceof Choose) return "choose";
        if (t instanceof ValueOf) return "value-of";
        if (t instanceof Copy) return "copy";
        if (t instanceof CopyOf) return "copy-of";
        if (t instanceof FixedElement) return "element";
        if (t instanceof FixedAttribute) return "attribute";
        if (t instanceof Comment) return "comment";
        if (t instanceof ProcessingInstruction) return "pi";
        // Message class may not exist in all Saxon versions
        if (t instanceof ResultDocument) return "result-document";
        if (t instanceof IterateInstr) return "iterate";
        if (t instanceof Instruction) return t.getClass().getSimpleName().toLowerCase();
        return t.getClass().getSimpleName();
    }

    private String getInstructionDetail(Traceable t) {
        try {
            if (t instanceof TemplateRule) {
                return "match=\"" + ((TemplateRule) t).getMatchPattern().toShortString() + "\"";
            }
            if (t instanceof ApplyTemplates) {
                Expression select = ((ApplyTemplates) t).getSelectExpression();
                if (select != null) {
                    return "select=\"" + select.toShortString() + "\"";
                }
            }
            if (t instanceof ForEach) {
                Expression select = ((ForEach) t).getSelectExpression();
                if (select != null) {
                    return "select=\"" + select.toShortString() + "\"";
                }
            }
            if (t instanceof ValueOf) {
                Expression select = ((ValueOf) t).getSelect();
                if (select != null) {
                    String s = select.toShortString();
                    if (s.length() > 40) s = s.substring(0, 37) + "...";
                    return "select=\"" + s + "\"";
                }
            }
            if (t instanceof FixedElement) {
                // Element name access varies by Saxon version
                return null;
            }
            if (t instanceof FixedAttribute) {
                return null;
            }
            if (t instanceof CallTemplate) {
                // Template name access varies by Saxon version
                return null;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String getShortModule(String systemId) {
        if (systemId == null) return "?";
        int lastSlash = systemId.lastIndexOf('/');
        return lastSlash >= 0 ? systemId.substring(lastSlash + 1) : systemId;
    }
}
