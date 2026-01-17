<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes" use-character-maps="special-chars"/>

    <!-- Test xsl:character-map for character substitution -->
    <xsl:character-map name="special-chars">
        <xsl:output-character character="©" string="(c)"/>
        <xsl:output-character character="®" string="(R)"/>
        <xsl:output-character character="™" string="(TM)"/>
    </xsl:character-map>

    <xsl:template match="/">
        <result>
            <xsl:for-each select="//text">
                <line><xsl:value-of select="."/></line>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
