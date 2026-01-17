<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <xsl:iterate select="//num">
                <xsl:param name="sum" select="0"/>
                <xsl:on-completion>
                    <total><xsl:value-of select="$sum"/></total>
                </xsl:on-completion>
                <xsl:next-iteration>
                    <xsl:with-param name="sum" select="$sum + ."/>
                </xsl:next-iteration>
            </xsl:iterate>
        </result>
    </xsl:template>
</xsl:stylesheet>
