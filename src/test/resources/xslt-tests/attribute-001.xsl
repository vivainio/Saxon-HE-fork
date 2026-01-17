<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <xsl:element name="{//tag}">
            <xsl:attribute name="id" select="//id"/>
            <xsl:attribute name="class">
                <xsl:value-of select="//class"/>
            </xsl:attribute>
            <xsl:value-of select="//content"/>
        </xsl:element>
    </xsl:template>
</xsl:stylesheet>
