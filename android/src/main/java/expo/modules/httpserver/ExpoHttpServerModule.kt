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
import java.io.IOException
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import java.util.UUID

class ExpoHttpServerModule : Module() {
  class SimpleHttpResponse(val statusCode: Int,
                           val statusDescription: String,
                           val contentType: String,
                           val headers: HashMap<String, String>,
                           val body: String)

  private var server: Server? = null;
  private var started = false;
  private val responses = HashMap<String, SimpleHttpResponse>()

  override fun definition() = ModuleDefinition {

    Name("ExpoHttpServer")

    Events("onStatusUpdate", "onRequest")

    AsyncFunction("setup") { port: Int, promise: Promise ->
      try {
        server = AndroidServer.Builder{
          port {
            port
          }
        }.build()
        promise.resolve("OK");
      } catch(e : IOException) {
        promise.reject(CodedException(e))
      }
    }

    AsyncFunction("start") { promise: Promise ->
      if(server == null) {
        promise.reject(CodedException("Server not setup / port not configured"))
        sendEvent("onStatusUpdate", bundleOf(
          "status" to "ERROR",
          "message" to "Server not setup / port not configured"
        ))
      } else {
        try{
          if(!started) {
            started = true
            server?.start()
          }
          sendEvent("onStatusUpdate", bundleOf(
            "status" to "STARTED",
            "message" to "Server started"
          ))
          promise.resolve("OK")
        } catch(e: IOException) {
          promise.reject(CodedException(e))
          sendEvent("onStatusUpdate", bundleOf(
            "status" to "ERROR",
            "message" to e.message
          ))
        }
      }
    }

    AsyncFunction("stop") { promise: Promise ->
      try{
        started = false
        server?.close()
        promise.resolve("OK");
        sendEvent("onStatusUpdate", bundleOf(
          "status" to "STOPPED",
          "message" to "Server stopped"
        ))
      } catch(e: IOException) {
        promise.reject(CodedException(e))
        sendEvent("onStatusUpdate", bundleOf(
          "status" to "ERROR",
          "message" to e.message
        ))
      }
    }

    Function("route") { path: String, method: String, uuid: String ->
      server = server?.request(HttpMethod.getMethod(method), path) { request: Request, response: Response ->
        val headers: Map<String, String> = request.headers()
        val params: Map<String, String> = request.params()
        val cookies: Map<String, String> = request.cookies().associate { it.name() to it.value() }
        val requestId = UUID.randomUUID().toString()
        sendEvent("onRequest", bundleOf(
          "uuid" to uuid,
          "requestId" to requestId,
          "method" to request.method().name,
          "path" to request.url(),
          "body" to request.content(),
          "headersJson" to JSONObject(headers).toString(),
          "paramsJson" to JSONObject(params).toString(),
          "cookiesJson" to JSONObject(cookies).toString(),
        ))
        while (!responses.containsKey(requestId)) {
          Thread.sleep(10)
        }
        val res = responses[requestId]!!
        response.setBodyText(res.body)
        response.setStatus(res.statusCode)
        response.addHeader("Content-Length", "" + res.body.length)
        response.addHeader("Content-Type", res.contentType)
        for ((key, value) in res.headers) {
          response.addHeader(key, value)
        }
        responses.remove(requestId);
        return@request response
      };
    }

    Function("respond") {
                          requestId: String,
                          statusCode: Int,
                          statusDescription: String,
                          contentType: String,
                          headers: HashMap<String, String>,
                          body: String ->
      responses[requestId] = SimpleHttpResponse(statusCode, statusDescription, contentType, headers, body);
    }
  }
}