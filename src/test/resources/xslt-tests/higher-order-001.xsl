<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

    <xsl:template match="numbers">
        <results>
            <!-- fn:for-each: double each number -->
            <doubled><xsl:value-of select="for-each(num, function($n) { $n * 2 })" separator=","/></doubled>
            <!-- fn:filter: keep only even numbers -->
            <evens><xsl:value-of select="filter(num, function($n) { $n mod 2 = 0 })" separator=","/></evens>
            <!-- fn:fold-left: sum all numbers -->
            <sum><xsl:value-of select="fold-left(num, 0, function($acc, $n) { $acc + $n })"/></sum>
        </results>
    </xsl:template>

</xsl:stylesheet>
