<?xml version="1.0" encoding="UTF-8"?>
<!-- Phase 5: enriched canonical invoice -> request XML of the OData V2 receiver adapter, operation Create (POST)
     on A_SupplierInvoice with deep insert of to_SuplrInvcItemPurOrdRef and to_SupplierInvoiceTax.
     The element names follow the adapter wizard's XSD generated from API_SUPPLIERINVOICE_PROCESS_SRV
     (docs/edmx in lw-s4-standin); property order follows the EDMX. Dates are xs:dateTime (the adapter turns
     them into OData V2 /Date(ms)/ values). Parameter postingDate (yyyy-MM-dd, today in CET) is set by Enrich.groovy.
     Needs the <S4> blocks that Enrich.groovy adds; without them it stops, because a posting without
     resolved keys must never be sent. XSLT 2.0 (Cloud Integration). -->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:lw="urn:leinewerk:einvoice:fn"
    exclude-result-prefixes="xs lw">
  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
  <xsl:param name="postingDate" as="xs:string" select="''"/>

  <xsl:template match="/Invoice">
    <xsl:variable name="h" select="Header"/>
    <xsl:if test="not($h/S4/CompanyCode != '' and $h/S4/InvoicingParty != '') or Lines/Line[not(S4/Unit != '' and S4/TaxCode != '')] or Taxes/Tax[not(S4/TaxCode != '')]">
      <xsl:sequence select="error(QName('urn:leinewerk:einvoice:fn', 'lw:not-enriched'), 'canonical-to-api: invoice is not enriched (run Enrich.groovy first)')"/>
    </xsl:if>
    <xsl:if test="not($postingDate castable as xs:date)">
      <xsl:sequence select="error(QName('urn:leinewerk:einvoice:fn', 'lw:bad-posting-date'), 'canonical-to-api: parameter postingDate must be yyyy-MM-dd')"/>
    </xsl:if>
    <xsl:variable name="issue" select="xs:date($h/IssueDate)"/>
    <!-- payment terms: XRechnung convention #SKONTO#TAGE=14#PROZENT=2.00# (one per line, at most two are posted) -->
    <xsl:variable name="skonto" as="element()*">
      <xsl:analyze-string select="string($h/PaymentTerms)" regex="#SKONTO#TAGE=([0-9]+)#PROZENT=([0-9]+(\.[0-9]+)?)#">
        <xsl:matching-substring>
          <s><d><xsl:value-of select="xs:integer(regex-group(1))"/></d><p><xsl:value-of select="format-number(xs:decimal(regex-group(2)), '0.00')"/></p></s>
        </xsl:matching-substring>
      </xsl:analyze-string>
    </xsl:variable>
    <A_SupplierInvoice>
      <CompanyCode><xsl:value-of select="$h/S4/CompanyCode"/></CompanyCode>
      <DocumentDate><xsl:value-of select="concat($issue, 'T00:00:00')"/></DocumentDate>
      <PostingDate><xsl:value-of select="concat($postingDate, 'T00:00:00')"/></PostingDate>
      <SupplierInvoiceIDByInvcgParty><xsl:value-of select="$h/Number"/></SupplierInvoiceIDByInvcgParty>
      <InvoicingParty><xsl:value-of select="$h/S4/InvoicingParty"/></InvoicingParty>
      <DocumentCurrency><xsl:value-of select="$h/Currency"/></DocumentCurrency>
      <InvoiceGrossAmount><xsl:value-of select="$h/GrossAmount"/></InvoiceGrossAmount>
      <DueCalculationBaseDate><xsl:value-of select="concat($issue, 'T00:00:00')"/></DueCalculationBaseDate>
      <xsl:if test="$skonto[1]">
        <CashDiscount1Percent><xsl:value-of select="$skonto[1]/p"/></CashDiscount1Percent>
        <CashDiscount1Days><xsl:value-of select="$skonto[1]/d"/></CashDiscount1Days>
      </xsl:if>
      <xsl:if test="$skonto[2]">
        <CashDiscount2Percent><xsl:value-of select="$skonto[2]/p"/></CashDiscount2Percent>
        <CashDiscount2Days><xsl:value-of select="$skonto[2]/d"/></CashDiscount2Days>
      </xsl:if>
      <xsl:if test="$h/DueDate != ''">
        <NetPaymentDays><xsl:value-of select="max((0, days-from-duration(xs:date($h/DueDate) - $issue)))"/></NetPaymentDays>
      </xsl:if>
      <!-- the supplier's VAT amounts are posted as sent; the stand-in checks the balance (R6) -->
      <TaxIsCalculatedAutomatically>false</TaxIsCalculatedAutomatically>
      <to_SuplrInvcItemPurOrdRef>
        <xsl:for-each select="Lines/Line">
          <A_SuplrInvcItemPurOrdRefType>
            <SupplierInvoiceItem><xsl:value-of select="Id"/></SupplierInvoiceItem>
            <PurchaseOrder><xsl:value-of select="$h/PurchaseOrder"/></PurchaseOrder>
            <PurchaseOrderItem><xsl:value-of select="PurchaseOrderItem"/></PurchaseOrderItem>
            <TaxCode><xsl:value-of select="S4/TaxCode"/></TaxCode>
            <DocumentCurrency><xsl:value-of select="$h/Currency"/></DocumentCurrency>
            <SupplierInvoiceItemAmount><xsl:value-of select="NetAmount"/></SupplierInvoiceItemAmount>
            <PurchaseOrderQuantityUnit><xsl:value-of select="S4/Unit"/></PurchaseOrderQuantityUnit>
            <QuantityInPurchaseOrderUnit><xsl:value-of select="Quantity"/></QuantityInPurchaseOrderUnit>
          </A_SuplrInvcItemPurOrdRefType>
        </xsl:for-each>
      </to_SuplrInvcItemPurOrdRef>
      <to_SupplierInvoiceTax>
        <xsl:for-each select="Taxes/Tax">
          <A_SupplierInvoiceTaxType>
            <TaxCode><xsl:value-of select="S4/TaxCode"/></TaxCode>
            <DocumentCurrency><xsl:value-of select="$h/Currency"/></DocumentCurrency>
            <TaxAmount><xsl:value-of select="Amount"/></TaxAmount>
          </A_SupplierInvoiceTaxType>
        </xsl:for-each>
      </to_SupplierInvoiceTax>
    </A_SupplierInvoice>
  </xsl:template>
</xsl:stylesheet>
