<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Declare expected context item type -->
    <xsl:template match="/" as="element()">
        <xsl:context-item as="document-node(element(config))" use="required"/>
        <settings>
            <xsl:for-each select="config/setting">
                <param name="{@name}" type="{@type}"><xsl:value-of select="."/></param>
            </xsl:for-each>
        </settings>
    </xsl:template>

</xsl:stylesheet>
