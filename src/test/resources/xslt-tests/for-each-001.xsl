<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <numbers>
            <xsl:for-each select="//num">
                <doubled><xsl:value-of select=". * 2"/></doubled>
            </xsl:for-each>
        </numbers>
    </xsl:template>
</xsl:stylesheet>
