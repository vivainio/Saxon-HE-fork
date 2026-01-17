<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <!-- Test xsl:fallback with use-when for conditional behavior -->
    <xsl:template match="/">
        <result>
            <!-- Test use-when with fallback for version check -->
            <test1>
                <xsl:choose>
                    <xsl:when test="system-property('xsl:version') ge '3.0'">
                        <version>3.0+</version>
                    </xsl:when>
                    <xsl:otherwise>
                        <version>pre-3.0</version>
                    </xsl:otherwise>
                </xsl:choose>
            </test1>
            <!-- Test xsl:fallback inside xsl:evaluate (EE feature) -->
            <test2>
                <xsl:try>
                    <xsl:evaluate xpath="'test-value'"/>
                    <xsl:catch>
                        <fallback>evaluate-failed</fallback>
                    </xsl:catch>
                </xsl:try>
            </test2>
        </result>
    </xsl:template>
</xsl:stylesheet>
