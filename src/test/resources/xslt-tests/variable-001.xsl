<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:variable name="multiplier" select="10"/>

    <xsl:template match="/">
        <xsl:variable name="value" select="//value"/>
        <result><xsl:value-of select="$value * $multiplier"/></result>
    </xsl:template>
</xsl:stylesheet>
