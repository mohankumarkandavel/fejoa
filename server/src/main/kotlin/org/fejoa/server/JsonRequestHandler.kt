package org.fejoa.server

import java.io.InputStream


abstract class JsonRequestHandler(val method: String) {
    abstract fun handle(responseHandler: Portal.ResponseHandler, jsonRPCHandler: JsonRPCHandler,
                        data: InputStream?, session: Session)
}
