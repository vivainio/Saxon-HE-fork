<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:local="http://local"
                exclude-result-prefixes="local">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:sequence for returning sequences from functions -->
    <xsl:function name="local:get-range">
        <xsl:param name="start"/>
        <xsl:param name="end"/>
        <xsl:sequence select="$start to $end"/>
    </xsl:function>

    <xsl:function name="local:wrap-items">
        <xsl:param name="items"/>
        <xsl:for-each select="$items">
            <xsl:sequence>
                <wrapped><xsl:value-of select="."/></wrapped>
            </xsl:sequence>
        </xsl:for-each>
    </xsl:function>

    <xsl:template match="/">
        <result>
            <!-- Use sequence from function -->
            <numbers>
                <xsl:for-each select="local:get-range(1, 5)">
                    <n><xsl:value-of select="."/></n>
                </xsl:for-each>
            </numbers>
            <!-- Return node sequence -->
            <items>
                <xsl:copy-of select="local:wrap-items(//item)"/>
            </items>
        </result>
    </xsl:template>
</xsl:stylesheet>
