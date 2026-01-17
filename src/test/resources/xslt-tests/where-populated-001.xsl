<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="products">
        <catalog>
            <xsl:for-each select="product">
                <item>
                    <name><xsl:value-of select="name"/></name>
                    <xsl:where-populated>
                        <description><xsl:value-of select="description"/></description>
                    </xsl:where-populated>
                </item>
            </xsl:for-each>
        </catalog>
    </xsl:template>

</xsl:stylesheet>
