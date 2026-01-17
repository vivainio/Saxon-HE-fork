<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <upper><xsl:value-of select="upper-case(//text)"/></upper>
            <tokens><xsl:value-of select="count(tokenize(//text, ' '))"/></tokens>
            <sum><xsl:value-of select="sum(//num)"/></sum>
            <joined><xsl:value-of select="string-join(//item, '-')"/></joined>
        </result>
    </xsl:template>
</xsl:stylesheet>
