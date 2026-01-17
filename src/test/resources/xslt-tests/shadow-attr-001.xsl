<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test static shadow attributes using xsl:param with static="yes" -->
    <xsl:param name="element-name" select="'dynamic-element'" static="yes"/>

    <xsl:template match="/">
        <result>
            <!-- Shadow attribute uses static parameter -->
            <xsl:element _name="{$element-name}">
                <xsl:for-each select="//item">
                    <value><xsl:value-of select="."/></value>
                </xsl:for-each>
            </xsl:element>
        </result>
    </xsl:template>
</xsl:stylesheet>
