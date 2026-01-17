<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <!-- Test various xsl:output options -->
    <xsl:output method="xml"
                indent="no"
                omit-xml-declaration="yes"
                cdata-section-elements="code"
                suppress-indentation="inline"/>

    <xsl:template match="/">
        <result>
            <code><xsl:value-of select="//script"/></code>
            <text><xsl:value-of select="//message"/></text>
            <inline>Some <b>inline</b> content</inline>
        </result>
    </xsl:template>
</xsl:stylesheet>
