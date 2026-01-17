<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <xsl:variable name="json" select="parse-json(//json)"/>
        <result>
            <name><xsl:value-of select="$json?name"/></name>
            <age><xsl:value-of select="$json?age"/></age>
        </result>
    </xsl:template>
</xsl:stylesheet>
