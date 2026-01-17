<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <xsl:for-each select="//item">
                <xsl:choose>
                    <xsl:when test=". &lt; 0">negative</xsl:when>
                    <xsl:when test=". = 0">zero</xsl:when>
                    <xsl:otherwise>positive</xsl:otherwise>
                </xsl:choose>
                <xsl:if test="position() != last()">,</xsl:if>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
