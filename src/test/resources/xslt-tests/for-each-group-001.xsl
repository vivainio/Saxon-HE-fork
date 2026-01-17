<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <groups>
            <xsl:for-each-group select="//person" group-by="@city">
                <city name="{current-grouping-key()}">
                    <xsl:for-each select="current-group()">
                        <name><xsl:value-of select="@name"/></name>
                    </xsl:for-each>
                </city>
            </xsl:for-each-group>
        </groups>
    </xsl:template>
</xsl:stylesheet>
