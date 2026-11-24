<?xml version="1.0" encoding="UTF-8"?>
<!-- Phase 5: XRechnung / ZUGFeRD (UN/CEFACT CII D16B) -> canonical Leinewerk invoice (ADR 0002).
     Pure syntax mapping, same canonical output as ubl-to-canonical.xsl. XSLT 2.0 (Cloud Integration). -->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:lw="urn:leinewerk:einvoice:fn"
    xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
    xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
    xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100"
    exclude-result-prefixes="xs lw rsm ram udt">
  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:function name="lw:amt" as="xs:string?">
    <xsl:param name="n" as="node()?"/>
    <xsl:sequence select="if (normalize-space($n) = '') then () else format-number(xs:decimal(normalize-space($n)), '0.00')"/>
  </xsl:function>
  <xsl:function name="lw:num" as="xs:string?">
    <xsl:param name="n" as="node()?"/>
    <xsl:sequence select="if (normalize-space($n) = '') then () else string(xs:decimal(normalize-space($n)))"/>
  </xsl:function>
  <!-- CII date format 102 (yyyyMMdd) -> xs:date text. Other formats are not valid in EN 16931 and pass through. -->
  <xsl:function name="lw:date" as="xs:string?">
    <xsl:param name="n" as="node()?"/>
    <xsl:variable name="v" select="normalize-space($n)"/>
    <xsl:sequence select="if ($v = '') then ()
                          else if (matches($v, '^[0-9]{8}$')) then concat(substring($v, 1, 4), '-', substring($v, 5, 2), '-', substring($v, 7, 2))
                          else $v"/>
  </xsl:function>

  <xsl:template match="/rsm:CrossIndustryInvoice">
    <xsl:variable name="t" select="rsm:SupplyChainTradeTransaction"/>
    <xsl:variable name="agr" select="$t/ram:ApplicableHeaderTradeAgreement"/>
    <xsl:variable name="set" select="$t/ram:ApplicableHeaderTradeSettlement"/>
    <xsl:variable name="due" select="lw:date(($set/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/udt:DateTimeString)[1])"/>
    <xsl:variable name="terms" select="($set/ram:SpecifiedTradePaymentTerms/ram:Description)[1]"/>
    <Invoice version="1" source="CII">
      <Header>
        <Number><xsl:value-of select="normalize-space(rsm:ExchangedDocument/ram:ID)"/></Number>
        <TypeCode><xsl:value-of select="normalize-space(rsm:ExchangedDocument/ram:TypeCode)"/></TypeCode>
        <IssueDate><xsl:value-of select="lw:date(rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString)"/></IssueDate>
        <xsl:if test="$due"><DueDate><xsl:value-of select="$due"/></DueDate></xsl:if>
        <Currency><xsl:value-of select="normalize-space($set/ram:InvoiceCurrencyCode)"/></Currency>
        <BuyerReference><xsl:value-of select="normalize-space($agr/ram:BuyerReference)"/></BuyerReference>
        <PurchaseOrder><xsl:value-of select="normalize-space($agr/ram:BuyerOrderReferencedDocument/ram:IssuerAssignedID)"/></PurchaseOrder>
        <SellerVatId><xsl:value-of select="normalize-space($agr/ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID = 'VA'])"/></SellerVatId>
        <GrossAmount><xsl:value-of select="lw:amt($set/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount)"/></GrossAmount>
        <xsl:if test="$terms"><PaymentTerms><xsl:value-of select="$terms"/></PaymentTerms></xsl:if>
      </Header>
      <Taxes>
        <xsl:for-each select="$set/ram:ApplicableTradeTax">
          <Tax>
            <Category><xsl:value-of select="normalize-space(ram:CategoryCode)"/></Category>
            <Rate><xsl:value-of select="lw:num(ram:RateApplicablePercent)"/></Rate>
            <BaseAmount><xsl:value-of select="lw:amt(ram:BasisAmount)"/></BaseAmount>
            <Amount><xsl:value-of select="lw:amt(ram:CalculatedAmount)"/></Amount>
          </Tax>
        </xsl:for-each>
      </Taxes>
      <Lines>
        <xsl:for-each select="$t/ram:IncludedSupplyChainTradeLineItem">
          <Line>
            <Id><xsl:value-of select="normalize-space(ram:AssociatedDocumentLineDocument/ram:LineID)"/></Id>
            <PurchaseOrderItem><xsl:value-of select="normalize-space(ram:SpecifiedLineTradeAgreement/ram:BuyerOrderReferencedDocument/ram:LineID)"/></PurchaseOrderItem>
            <Quantity unit="{normalize-space(ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode)}"><xsl:value-of select="lw:num(ram:SpecifiedLineTradeDelivery/ram:BilledQuantity)"/></Quantity>
            <NetAmount><xsl:value-of select="lw:amt(ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount)"/></NetAmount>
            <TaxCategory><xsl:value-of select="normalize-space(ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:CategoryCode)"/></TaxCategory>
            <TaxRate><xsl:value-of select="lw:num(ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:RateApplicablePercent)"/></TaxRate>
          </Line>
        </xsl:for-each>
      </Lines>
    </Invoice>
  </xsl:template>
</xsl:stylesheet>
