package org.fejoa.server

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.fejoa.network.Errors
import org.fejoa.network.HTMLRequestMultipartKeys
import org.fejoa.support.toUTFString

import javax.servlet.MultipartConfigElement
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.io.*
import java.util.ArrayList


class Portal(private val baseDir: String) : AbstractHandler() {
    private val jsonHandlers = ArrayList<JsonRequestHandler>()

    init {
        addJsonHandler(JsonPingHandler())
        /*
        addJsonHandler(new JsonPingHandler());
        addJsonHandler(new WatchHandler());
        addJsonHandler(new ChunkStoreRequestHandler());
        addJsonHandler(new CreateAccountHandler());
        addJsonHandler(new LoginRequestHandler());
        addJsonHandler(new CommandHandler());
        addJsonHandler(new AccessRequestHandler());
        addJsonHandler(new StartMigrationHandler());
        addJsonHandler(new RemotePullHandler());
        addJsonHandler(new RemoteIndexRequestHandler());
        */
    }

    inner class ResponseHandler(private val response: HttpServletResponse) {
        var isHandled = false
            private set
        private val builder = MultipartEntityBuilder.create()
        // TODO don't buffer the output data but send it directly!
        private var outputStream: ByteArrayOutputStream? = null

        init {
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        }

        fun setResponseHeader(header: String) {
            isHandled = true
            builder.addTextBody(HTMLRequestMultipartKeys.MESSAGE_KEY, header, ContentType.DEFAULT_TEXT)
        }

        fun addData(): OutputStream? {
            if (!isHandled)
                return null
            if (outputStream == null)
                outputStream = ByteArrayOutputStream()
            return outputStream
        }

        @Throws(IOException::class)
        fun finish() {
            if (outputStream != null) {
                builder.addBinaryBody(HTMLRequestMultipartKeys.DATA_KEY, ByteArrayInputStream(outputStream!!.toByteArray()),
                        ContentType.DEFAULT_BINARY, HTMLRequestMultipartKeys.DATA_FILE)
            }
            val entity = builder.build()
            response.outputStream.write(entity.contentType.toString().toByteArray())
            response.outputStream.write('\n'.toInt())
            entity.writeTo(response.outputStream)
        }
    }

    private fun addJsonHandler(handler: JsonRequestHandler) {
        jsonHandlers.add(handler)
    }

    @Throws(IOException::class, ServletException::class)
    override fun handle(s: String, request: Request, httpServletRequest: HttpServletRequest,
                        response: HttpServletResponse) {
        response.contentType = "text/plain;charset=utf-8"
        response.status = HttpServletResponse.SC_OK
        request.isHandled = true

        val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"))
        if (request.contentType != null && request.contentType.startsWith("multipart/form-data")) {
            request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
        }

        val session = Session(baseDir, httpServletRequest.session)
        val responseHandler = ResponseHandler(response)

        val messagePart = request.getPart(HTMLRequestMultipartKeys.MESSAGE_KEY)
        val data = request.getPart(HTMLRequestMultipartKeys.DATA_KEY)

        if (messagePart == null) {
            responseHandler.setResponseHeader("empty request!")
            responseHandler.finish()
            return
        }

        val stringWriter = ByteArrayOutputStream()
        messagePart.inputStream.copyTo(stringWriter)

        val error = handleJson(responseHandler, stringWriter.toByteArray().toUTFString(),
                data?.inputStream, session)

        if (!responseHandler.isHandled || error != null)
            responseHandler.setResponseHeader(error!!)

        responseHandler.finish()
    }

    private fun handleJson(responseHandler: ResponseHandler, message: String, data: InputStream?, session: Session): String? {
        val jsonRPCHandler: JsonRPCHandler
        try {
            jsonRPCHandler = JsonRPCHandler(message)
        } catch (e: Exception) {
            e.printStackTrace()
            return JsonRPCHandler.makeResult(-1, Errors.INVALID_JSON_REQUEST, "can't parse json")
        }

        val method = jsonRPCHandler.method
        for (handler in jsonHandlers) {
            if (!handler.method.equals(method))
                continue

            try {
                handler.handle(responseHandler, jsonRPCHandler, data, session)
            } catch (e: Exception) {
                e.printStackTrace()
                return jsonRPCHandler.makeResult(Errors.EXCEPTION, e.message ?: "")
            }

            if (responseHandler.isHandled)
                return null
        }

        return jsonRPCHandler.makeResult(Errors.NO_HANDLER_FOR_REQUEST, "can't handle request")
    }
}