package com.sap.it.api

/** Minimal stand-in for the Cloud Integration script API, for local unit tests only: tests set `stub`. */
class ITApiFactory {
    static Object stub
    static <T> T getApi(Class<T> type, Object context) { (T) stub }
}
