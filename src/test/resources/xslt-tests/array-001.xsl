<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:array="http://www.w3.org/2005/xpath-functions/array"
    exclude-result-prefixes="array">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <xsl:variable name="arr" select="['first', 'second', 'third']"/>
        <result>
            <size><xsl:value-of select="array:size($arr)"/></size>
            <item><xsl:value-of select="$arr(2)"/></item>
        </result>
    </xsl:template>
</xsl:stylesheet>
