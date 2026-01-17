<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:on-empty and xsl:on-non-empty -->
    <xsl:template match="/">
        <result>
            <!-- Container with items -->
            <list1>
                <xsl:on-non-empty><header>Items found:</header></xsl:on-non-empty>
                <xsl:for-each select="//category[@name='fruits']/item">
                    <item><xsl:value-of select="."/></item>
                </xsl:for-each>
                <xsl:on-empty><empty>No items</empty></xsl:on-empty>
            </list1>
            <!-- Container without items -->
            <list2>
                <xsl:on-non-empty><header>Items found:</header></xsl:on-non-empty>
                <xsl:for-each select="//category[@name='empty']/item">
                    <item><xsl:value-of select="."/></item>
                </xsl:for-each>
                <xsl:on-empty><empty>No items</empty></xsl:on-empty>
            </list2>
        </result>
    </xsl:template>
</xsl:stylesheet>
