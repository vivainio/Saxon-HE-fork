<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="calculations">
        <results>
            <xsl:for-each select="calc">
                <result expr="{@expr}">
                    <xsl:evaluate xpath="@expr"/>
                </result>
            </xsl:for-each>
        </results>
    </xsl:template>

</xsl:stylesheet>
