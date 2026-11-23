package com.sap.gateway.ip.core.customdev.util

/** Minimal stand-in for the Cloud Integration script API, for local unit tests only. */
class Message {
    Object body
    Map<String, Object> properties = [:]
    Map<String, Object> headers = [:]
    def getBody(Class type) {
        if (body == null) return null
        if (type == byte[].class) return body instanceof byte[] ? body : body.toString().getBytes('UTF-8')
        if (type == String.class) return body instanceof byte[] ? new String((byte[]) body, 'UTF-8') : body.toString()
        return body
    }
    def getBody() { body }
    void setBody(Object b) { body = b }
    Object getProperty(String name) { properties[name] }
    void setProperty(String name, Object value) { properties[name] = value }
    Map getProperties() { properties }
    Object getHeader(String name, Class type) { headers[name] }
    void setHeader(String name, Object value) { headers[name] = value }
    Map getHeaders() { headers }
}
