<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <xsl:analyze-string select="//text" regex="\d+">
                <xsl:matching-substring>
                    <num><xsl:value-of select="."/></num>
                </xsl:matching-substring>
                <xsl:non-matching-substring>
                    <str><xsl:value-of select="."/></str>
                </xsl:non-matching-substring>
            </xsl:analyze-string>
        </result>
    </xsl:template>
</xsl:stylesheet>
