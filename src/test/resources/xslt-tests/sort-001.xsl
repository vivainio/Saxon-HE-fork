<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <xsl:for-each select="//person">
                <xsl:sort select="@age" data-type="number"/>
                <name><xsl:value-of select="@name"/></name>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
