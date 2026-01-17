<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:processing-instruction and xsl:comment -->
    <xsl:template match="/">
        <result>
            <!-- Static comment -->
            <xsl:comment> This is a static comment </xsl:comment>
            <!-- Dynamic comment -->
            <xsl:comment>Generated from: <xsl:value-of select="//source"/></xsl:comment>
            <!-- Static PI -->
            <xsl:processing-instruction name="xml-stylesheet">href="style.css" type="text/css"</xsl:processing-instruction>
            <!-- Dynamic PI -->
            <xsl:processing-instruction name="{//pi/@name}">
                <xsl:value-of select="//pi/@value"/>
            </xsl:processing-instruction>
            <content>done</content>
        </result>
    </xsl:template>
</xsl:stylesheet>
