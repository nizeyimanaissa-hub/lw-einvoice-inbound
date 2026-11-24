package com.sap.it.api.mapping

/** Same signature as the Cloud Integration API; returns null when the value mapping has no entry. */
interface ValueMappingApi {
    String getMappedValue(String sourceAgency, String sourceIdentifier, String sourceValue, String targetAgency, String targetIdentifier)
}
