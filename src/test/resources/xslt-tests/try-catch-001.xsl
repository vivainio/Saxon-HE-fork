<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:err="http://www.w3.org/2005/xqt-errors"
    exclude-result-prefixes="err">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <xsl:try>
                <xsl:value-of select="1 div 0"/>
                <xsl:catch>
                    <error>caught</error>
                </xsl:catch>
            </xsl:try>
        </result>
    </xsl:template>
</xsl:stylesheet>
