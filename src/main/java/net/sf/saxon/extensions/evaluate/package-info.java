/**
 * Fork-additions: dynamic XPath-evaluation extension functions for Saxon-HE.
 *
 * <p>Provides {@code dynamic:evaluate()} (EXSLT) and {@code saxon:evaluate()}
 * (Saxon vendor namespace) on Saxon-HE, both backed by a single
 * implementation that delegates to the existing XSLT 3.0
 * {@code xsl:evaluate} compile-and-evaluate machinery.</p>
 *
 * <p>See {@link net.sf.saxon.extensions.evaluate.EvaluateExtensions} for the
 * one-call registration API.</p>
 *
 * <p>This package is a fork-only addition; no upstream Saxonica source files
 * are modified.</p>
 */
package net.sf.saxon.extensions.evaluate;
