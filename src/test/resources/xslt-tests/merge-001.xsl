<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="data">
        <merged>
            <xsl:merge>
                <xsl:merge-source select="list1/item">
                    <xsl:merge-key select="xs:integer(@id)"/>
                </xsl:merge-source>
                <xsl:merge-source select="list2/item">
                    <xsl:merge-key select="xs:integer(@id)"/>
                </xsl:merge-source>
                <xsl:merge-action>
                    <entry id="{current-merge-key()}">
                        <xsl:for-each select="current-merge-group()">
                            <value><xsl:value-of select="."/></value>
                        </xsl:for-each>
                    </entry>
                </xsl:merge-action>
            </xsl:merge>
        </merged>
    </xsl:template>

</xsl:stylesheet>
