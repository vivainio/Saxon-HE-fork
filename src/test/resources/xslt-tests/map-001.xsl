<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    exclude-result-prefixes="map">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <xsl:variable name="codes" select="map{'US': 'United States', 'UK': 'United Kingdom', 'DE': 'Germany'}"/>
        <result>
            <xsl:for-each select="//country">
                <name><xsl:value-of select="$codes(.)"/></name>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
