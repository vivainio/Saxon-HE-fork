<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <result>
            <toc><xsl:apply-templates mode="toc"/></toc>
            <full><xsl:apply-templates mode="full"/></full>
        </result>
    </xsl:template>

    <xsl:template match="section" mode="toc">
        <entry><xsl:value-of select="@title"/></entry>
    </xsl:template>

    <xsl:template match="section" mode="full">
        <section title="{@title}"><xsl:value-of select="."/></section>
    </xsl:template>
</xsl:stylesheet>
