<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result><xsl:apply-templates select="//item"/></result>
    </xsl:template>

    <xsl:template match="item">
        <v><xsl:value-of select="."/></v>
    </xsl:template>
</xsl:stylesheet>
