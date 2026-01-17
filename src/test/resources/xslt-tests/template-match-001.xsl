<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result><xsl:apply-templates/></result>
    </xsl:template>

    <xsl:template match="item[@type='a']">
        <a><xsl:value-of select="."/></a>
    </xsl:template>

    <xsl:template match="item[@type='b']">
        <b><xsl:value-of select="."/></b>
    </xsl:template>

    <xsl:template match="item">
        <other><xsl:value-of select="."/></other>
    </xsl:template>
</xsl:stylesheet>
