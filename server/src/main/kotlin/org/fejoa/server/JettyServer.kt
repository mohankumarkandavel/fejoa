/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.server

import org.apache.commons.cli.*
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.session.HashSessionIdManager
import org.eclipse.jetty.server.session.HashSessionManager
import org.eclipse.jetty.server.session.SessionHandler

import java.io.File
import java.net.InetSocketAddress


object DebugSingleton {
    var isNoAccessControl = false
}

internal class JettyServer(baseDir: String, host: String? = null, port: Int = DEFAULT_PORT) {
    private val server: Server

    constructor(baseDir: String, port: Int) : this(baseDir, null, port)

    init {
        println(File(baseDir).absolutePath)
        if (host == null)
            server = Server(port)
        else
            server = Server(InetSocketAddress(host, port))

        server.sessionIdManager = HashSessionIdManager()

        // Sessions are bound to a context.
        val context = ContextHandler("/")
        server.handler = context

        // Create the SessionHandler (wrapper) to handle the sessions
        val manager = HashSessionManager()
        val sessions = SessionHandler(manager)
        context.handler = sessions

        sessions.handler = Portal(baseDir)
    }

    @Throws(Exception::class)
    fun start() {
        server.start()
    }

    @Throws(Exception::class)
    fun stop() {
        server.stop()
        server.join()
    }

    fun setDebugNoAccessControl(noAccessControl: Boolean) {
        DebugSingleton.isNoAccessControl = noAccessControl
    }

    companion object {
        val DEFAULT_PORT = 8180

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val options = Options()

            val input = Option("h", "host", true, "Host ip address")
            input.isRequired = true
            options.addOption(input)

            val output = Option("p", "port", true, "Host port")
            output.isRequired = true
            options.addOption(output)

            val dirOption = Option("d", "directory", true, "Storage directory")
            dirOption.isRequired = false
            options.addOption(dirOption)

            val cmd: CommandLine
            try {
                cmd = DefaultParser().parse(options, args)
            } catch (e: ParseException) {
                println(e.message)
                HelpFormatter().printHelp("Fejoa Server", options)

                System.exit(1)
                return
            }

            val host = cmd.getOptionValue("host")
            val port: Int?
            try {
                port = Integer.parseInt(cmd.getOptionValue("port"))
            } catch (e: NumberFormatException) {
                println("Port must be a number")
                System.exit(1)
                return
            }

            var directory = "."
            if (cmd.hasOption("directory"))
                directory = cmd.getOptionValue("directory")

            val server = JettyServer(directory, host, port)
            server.start()
        }
    }
}

