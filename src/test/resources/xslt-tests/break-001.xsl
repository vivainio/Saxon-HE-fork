<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:break to exit iteration early -->
    <xsl:template match="/">
        <result>
            <xsl:iterate select="//num">
                <xsl:param name="sum" select="0"/>
                <!-- Break when sum exceeds 10 -->
                <xsl:choose>
                    <xsl:when test="$sum + . > 10">
                        <xsl:break>
                            <stopped-at sum="{$sum}"/>
                        </xsl:break>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:next-iteration>
                            <xsl:with-param name="sum" select="$sum + ."/>
                        </xsl:next-iteration>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:iterate>
        </result>
    </xsl:template>
</xsl:stylesheet>
