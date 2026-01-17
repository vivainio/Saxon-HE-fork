<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:key name="emp-by-dept" match="employee" use="@dept"/>

    <xsl:template match="/">
        <result>
            <xsl:for-each select="key('emp-by-dept', 'sales')">
                <emp><xsl:value-of select="@name"/></emp>
            </xsl:for-each>
        </result>
    </xsl:template>
</xsl:stylesheet>
