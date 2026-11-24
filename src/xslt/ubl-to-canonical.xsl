<?xml version="1.0" encoding="UTF-8"?>
<!-- Phase 5: XRechnung / EN 16931 UBL 2.1 Invoice -> canonical Leinewerk invoice (ADR 0002).
     Pure syntax mapping: no S/4HANA knowledge, no value mapping, no defaults. XSLT 2.0 (Cloud Integration). -->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:lw="urn:leinewerk:einvoice:fn"
    xmlns:ubl="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
    exclude-result-prefixes="xs lw ubl cac cbc">
  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:function name="lw:amt" as="xs:string?">
    <xsl:param name="n" as="node()?"/>
    <xsl:sequence select="if (normalize-space($n) = '') then () else format-number(xs:decimal(normalize-space($n)), '0.00')"/>
  </xsl:function>
  <xsl:function name="lw:num" as="xs:string?">
    <xsl:param name="n" as="node()?"/>
    <xsl:sequence select="if (normalize-space($n) = '') then () else string(xs:decimal(normalize-space($n)))"/>
  </xsl:function>

  <xsl:template match="/ubl:Invoice">
    <Invoice version="1" source="UBL">
      <Header>
        <Number><xsl:value-of select="normalize-space(cbc:ID)"/></Number>
        <TypeCode><xsl:value-of select="normalize-space(cbc:InvoiceTypeCode)"/></TypeCode>
        <IssueDate><xsl:value-of select="normalize-space(cbc:IssueDate)"/></IssueDate>
        <xsl:if test="normalize-space(cbc:DueDate) != ''"><DueDate><xsl:value-of select="normalize-space(cbc:DueDate)"/></DueDate></xsl:if>
        <Currency><xsl:value-of select="normalize-space(cbc:DocumentCurrencyCode)"/></Currency>
        <BuyerReference><xsl:value-of select="normalize-space(cbc:BuyerReference)"/></BuyerReference>
        <PurchaseOrder><xsl:value-of select="normalize-space(cac:OrderReference/cbc:ID)"/></PurchaseOrder>
        <SellerVatId><xsl:value-of select="normalize-space(cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme[cac:TaxScheme/cbc:ID = 'VAT']/cbc:CompanyID)"/></SellerVatId>
        <GrossAmount><xsl:value-of select="lw:amt(cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount)"/></GrossAmount>
        <xsl:if test="cac:PaymentTerms[1]/cbc:Note"><PaymentTerms><xsl:value-of select="cac:PaymentTerms[1]/cbc:Note[1]"/></PaymentTerms></xsl:if>
      </Header>
      <Taxes>
        <xsl:for-each select="cac:TaxTotal/cac:TaxSubtotal">
          <Tax>
            <Category><xsl:value-of select="normalize-space(cac:TaxCategory/cbc:ID)"/></Category>
            <Rate><xsl:value-of select="lw:num(cac:TaxCategory/cbc:Percent)"/></Rate>
            <BaseAmount><xsl:value-of select="lw:amt(cbc:TaxableAmount)"/></BaseAmount>
            <Amount><xsl:value-of select="lw:amt(cbc:TaxAmount)"/></Amount>
          </Tax>
        </xsl:for-each>
      </Taxes>
      <Lines>
        <xsl:for-each select="cac:InvoiceLine">
          <Line>
            <Id><xsl:value-of select="normalize-space(cbc:ID)"/></Id>
            <PurchaseOrderItem><xsl:value-of select="normalize-space(cac:OrderLineReference/cbc:LineID)"/></PurchaseOrderItem>
            <Quantity unit="{normalize-space(cbc:InvoicedQuantity/@unitCode)}"><xsl:value-of select="lw:num(cbc:InvoicedQuantity)"/></Quantity>
            <NetAmount><xsl:value-of select="lw:amt(cbc:LineExtensionAmount)"/></NetAmount>
            <TaxCategory><xsl:value-of select="normalize-space(cac:Item/cac:ClassifiedTaxCategory/cbc:ID)"/></TaxCategory>
            <TaxRate><xsl:value-of select="lw:num(cac:Item/cac:ClassifiedTaxCategory/cbc:Percent)"/></TaxRate>
          </Line>
        </xsl:for-each>
      </Lines>
    </Invoice>
  </xsl:template>
</xsl:stylesheet>
