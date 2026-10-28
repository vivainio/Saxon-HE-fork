package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticContext;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-{@link Configuration} policy deciding whether a static error found while
 * type-checking an XSLT {@code select}/{@code test} attribute should be deferred
 * to run time instead of failing compilation.
 *
 * <p>A deferred error becomes an {@code ErrorExpression}: it is raised only if the
 * expression is actually evaluated, as legacy processors such as Xalan do. The
 * default is to defer nothing, so upstream behaviour is unchanged unless a policy
 * is installed with {@link #set}.</p>
 */
public final class StaticErrorPolicy {

    /** Decides whether a given static error should be deferred. */
    @FunctionalInterface
    public interface Policy {
        /**
         * @param error   the error raised during static type checking
         * @param context the static context of the failing expression, e.g. to test
         *                {@link StaticContext#isInBackwardsCompatibleMode()}
         * @return true to defer the error to run time
         */
        boolean shouldDefer(XPathException error, StaticContext context);
    }

    private static final Map<Configuration, Policy> POLICIES = new WeakHashMap<>();

    private StaticErrorPolicy() {}

    /** Install a policy for a configuration; {@code null} restores the default (defer nothing). */
    public static void set(Configuration config, Policy policy) {
        synchronized (POLICIES) {
            if (policy == null) {
                POLICIES.remove(config);
            } else {
                POLICIES.put(config, policy);
            }
        }
    }

    /** Called by the XSLT compiler when an expression fails static type checking. */
    public static boolean shouldDefer(Configuration config, XPathException error, StaticContext context) {
        Policy policy;
        synchronized (POLICIES) {
            policy = POLICIES.get(config);
        }
        return policy != null && policy.shouldDefer(error, context);
    }
}
