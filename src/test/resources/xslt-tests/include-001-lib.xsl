<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">

    <xsl:function name="local:format-name" xmlns:local="http://local">
        <xsl:param name="first"/>
        <xsl:param name="last"/>
        <xsl:value-of select="concat($last, ', ', $first)"/>
    </xsl:function>

    <xsl:template name="header">
        <header>Employee Directory</header>
    </xsl:template>

</xsl:stylesheet>
