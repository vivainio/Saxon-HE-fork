<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="data">
        <xsl:assert test="count(value) gt 0" error-code="NO_VALUES">Data must have values</xsl:assert>
        <xsl:assert test="every $v in value satisfies $v castable as xs:integer" error-code="NOT_INT">All values must be integers</xsl:assert>
        <result>
            <count><xsl:value-of select="count(value)"/></count>
            <sum><xsl:value-of select="sum(value)"/></sum>
        </result>
    </xsl:template>

</xsl:stylesheet>
