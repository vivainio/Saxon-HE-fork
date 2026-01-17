<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:next-match for template chaining -->

    <!-- More specific template - handles special items -->
    <xsl:template match="item[@special='true']" priority="2">
        <special>
            <xsl:next-match/>
        </special>
    </xsl:template>

    <!-- General template for all items -->
    <xsl:template match="item" priority="1">
        <item name="{@name}"/>
    </xsl:template>

    <xsl:template match="/">
        <result>
            <xsl:apply-templates select="//item"/>
        </result>
    </xsl:template>
</xsl:stylesheet>
