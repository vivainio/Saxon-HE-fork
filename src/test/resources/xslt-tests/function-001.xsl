<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:local="http://local"
    exclude-result-prefixes="local">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:function name="local:factorial" as="xs:integer" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xsl:param name="n" as="xs:integer"/>
        <xsl:sequence select="if ($n le 1) then 1 else $n * local:factorial($n - 1)"/>
    </xsl:function>

    <xsl:template match="/">
        <result><xsl:value-of select="local:factorial(//n)"/></result>
    </xsl:template>
</xsl:stylesheet>
