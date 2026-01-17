<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:local="http://local"
                exclude-result-prefixes="local"
                version="3.0">

    <xsl:include href="include-001-lib.xsl"/>

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="employees">
        <directory>
            <xsl:call-template name="header"/>
            <xsl:for-each select="employee">
                <person><xsl:value-of select="local:format-name(first, last)"/></person>
            </xsl:for-each>
        </directory>
    </xsl:template>

</xsl:stylesheet>
