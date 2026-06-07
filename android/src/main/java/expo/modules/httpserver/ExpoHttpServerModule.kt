package expo.modules.httpserver

import androidx.core.os.bundleOf
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import com.safframework.server.core.AndroidServer
import com.safframework.server.core.Server
import com.safframework.server.core.http.HttpMethod
import com.safframework.server.core.http.Request
import com.safframework.server.core.http.Response
import org.json.JSONObject
import java.net.BindException
import java.util.concurrent.ConcurrentHashMap

class ExpoHttpServerModule : Module() {
  class SimpleHttpResponse(val statusCode: Int,
                           val statusDescription: String,
                           val contentType: String,
                           val headers: HashMap<String, String>,
                           val body: String)

  data class RouteConfig(val path: String, val method: String, val uuid: String)

  companion object {
    // The underlying AndroidServer/Netty bind happens on Netty's own event loop
    // thread, asynchronously, so a BindException there can never be caught by a
    // try/catch around server.start() - it surfaces as an uncaught exception and
    // crashes the app. We install a single, narrowly-scoped default uncaught
    // exception handler (chained to whatever was already registered) that only
    // swallows BindExceptions thrown from within Netty's own call stack, so we
    // can report the failure via onStatusUpdate instead of crashing.
    private var bindExceptionGuardInstalled = false
    private val statusUpdateListeners = ConcurrentHashMap.newKeySet<(String) -> Unit>()

    @Synchronized
    private fun ensureBindExceptionGuardInstalled() {
      if (bindExceptionGuardInstalled) return
      bindExceptionGuardInstalled = true
      val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        if (isNettyBindException(throwable)) {
          val message = throwable.message ?: "Address already in use"
          statusUpdateListeners.forEach { it(message) }
        } else {
          previousHandler?.uncaughtException(thread, throwable)
        }
      }
    }

    private fun isNettyBindException(throwable: Throwable): Boolean =
      throwable is BindException &&
        throwable.stackTrace.any { it.className.startsWith("io.netty") }
  }

  private var server: Server? = null
  private var started = false
  // Tracks whether the *current* server instance has actually bound a socket.
  // A freshly built server (from setup()/buildServerWithRoutes()) holds no
  // socket, so there's nothing to release before starting it - closing and
  // rebuilding in that case only adds an unnecessary close/rebind race.
  private var hasBoundSocket = false
  private var configuredPort: Int = 0
  private val routeConfigs = mutableListOf<RouteConfig>()
  private val responses = ConcurrentHashMap<String, SimpleHttpResponse>()
  private val onBindFailure: (String) -> Unit = { message ->
    started = false
    hasBoundSocket = false
    sendEvent("onStatusUpdate", bundleOf(
      "status" to "ERROR",
      "message" to message
    ))
  }

  private fun handleRequest(uuid: String, request: Request, response: Response): Response {
    val headers: Map<String, String> = request.headers()
    val params: Map<String, String> = request.params()
    val cookies: Map<String, String> = request.cookies().associate { it.name() to it.value() }
    sendEvent("onRequest", bundleOf(
      "uuid" to uuid,
      "method" to request.method().name,
      "path" to request.url(),
      "body" to request.content(),
      "headersJson" to JSONObject(headers).toString(),
      "paramsJson" to JSONObject(params).toString(),
      "cookiesJson" to JSONObject(cookies).toString(),
    ))
    val deadline = System.currentTimeMillis() + 30_000
    while (!responses.containsKey(uuid) && System.currentTimeMillis() < deadline) {
      Thread.sleep(10)
    }
    val res = responses.remove(uuid) ?: return response
    response.setBodyText(res.body)
    response.setStatus(res.statusCode)
    response.addHeader("Content-Length", "" + res.body.length)
    response.addHeader("Content-Type", res.contentType)
    for ((key, value) in res.headers) {
      response.addHeader(key, value)
    }
    return response
  }

  private fun buildServerWithRoutes(): Server {
    var s: Server = AndroidServer.Builder {
      port { configuredPort }
    }.build()
    for (route in routeConfigs) {
      s = s.request(HttpMethod.getMethod(route.method), route.path) { request: Request, response: Response ->
        handleRequest(route.uuid, request, response)
      }
    }
    return s
  }

  override fun definition() = ModuleDefinition {

    Name("ExpoHttpServer")

    Events("onStatusUpdate", "onRequest")

    OnCreate {
      ensureBindExceptionGuardInstalled()
      statusUpdateListeners.add(onBindFailure)
    }

    OnDestroy {
      statusUpdateListeners.remove(onBindFailure)
    }

    Function("setup") { port: Int ->
      configuredPort = port
      server?.close()
      started = false
      hasBoundSocket = false
      routeConfigs.clear()
      server = AndroidServer.Builder {
        port { configuredPort }
      }.build()
    }

    Function("route") { path: String, method: String, uuid: String ->
      routeConfigs.add(RouteConfig(path, method, uuid))
      server = server?.request(HttpMethod.getMethod(method), path) { request: Request, response: Response ->
        handleRequest(uuid, request, response)
      }
    }

    Function("start") {
      if (configuredPort == 0) {
        sendEvent("onStatusUpdate", bundleOf(
          "status" to "ERROR",
          "message" to "Server not setup / port not configured"
        ))
      } else {
        if (!started) {
          try {
            started = true
            // Only close and rebuild when the current server instance has actually
            // bound a socket before - a freshly built instance (from setup(), or
            // because it was never started) holds nothing to release, so closing
            // and immediately rebinding it would just add an unnecessary race with
            // the OS releasing the port (BindException: Address already in use).
            if (hasBoundSocket) {
              server?.close()
              // Give the OS a moment to release the previous socket before rebinding
              // to the same port - reduces (but, due to the underlying library
              // configuring SO_REUSEADDR on the wrong socket scope, cannot fully
              // eliminate) the "Address already in use" race.
              Thread.sleep(300)
              server = buildServerWithRoutes()
            }
            server?.start()
            hasBoundSocket = true
            sendEvent("onStatusUpdate", bundleOf(
              "status" to "STARTED",
              "message" to "Server started"
            ))
          } catch (e: Exception) {
            started = false
            sendEvent("onStatusUpdate", bundleOf(
              "status" to "ERROR",
              "message" to (e.message ?: "Failed to start server")
            ))
          }
        }
      }
    }

    Function("respond") { uuid: String,
                          statusCode: Int,
                          statusDescription: String,
                          contentType: String,
                          headers: HashMap<String, String>,
                          body: String ->
      responses[uuid] = SimpleHttpResponse(statusCode, statusDescription, contentType, headers, body);
    }

    Function("stop") {
      started = false
      server?.close()
      sendEvent("onStatusUpdate", bundleOf(
        "status" to "STOPPED",
        "message" to "Server stopped"
      ))
    }
  }
}
