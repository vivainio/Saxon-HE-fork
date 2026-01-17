<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:accumulator name="running-total" as="xs:integer" initial-value="0">
        <xsl:accumulator-rule match="item" select="$value + xs:integer(@price)"/>
    </xsl:accumulator>

    <xsl:mode use-accumulators="running-total"/>

    <xsl:template match="order">
        <totals>
            <xsl:apply-templates select="item"/>
        </totals>
    </xsl:template>

    <xsl:template match="item">
        <running-total after="{@name}"><xsl:value-of select="accumulator-after('running-total')"/></running-total>
    </xsl:template>

</xsl:stylesheet>
