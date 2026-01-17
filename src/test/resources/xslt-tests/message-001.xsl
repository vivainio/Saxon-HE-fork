<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:message without terminate (message goes to stderr, output continues) -->
    <xsl:template match="/">
        <result>
            <xsl:for-each select="//item">
                <xsl:message>Processing item: <xsl:value-of select="@id"/></xsl:message>
                <processed id="{@id}"/>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
