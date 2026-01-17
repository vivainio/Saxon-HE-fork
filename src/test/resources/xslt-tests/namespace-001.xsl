<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:namespace for dynamic namespace creation -->
    <xsl:template match="/">
        <result>
            <xsl:for-each select="//ns">
                <xsl:element name="{@prefix}:element" namespace="{@uri}">
                    <xsl:namespace name="{@prefix}" select="@uri"/>
                    <xsl:value-of select="@value"/>
                </xsl:element>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
