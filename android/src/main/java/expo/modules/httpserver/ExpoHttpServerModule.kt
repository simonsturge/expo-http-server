package expo.modules.httpserver

import androidx.core.os.bundleOf
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import com.safframework.server.core.AndroidServer
import com.safframework.server.core.RequestHandler
import com.safframework.server.core.Server
import com.safframework.server.core.http.HttpMethod
import com.safframework.server.core.http.Request
import com.safframework.server.core.http.Response
import org.json.JSONObject
import java.util.UUID

class ExpoHttpServerModule : Module() {
  class SimpleHttpResponse(val statusCode: Int,
                           val contentType: String,
                           val body: String)

  private var server: Server? = null;
  private var started = false;
  private val responses = HashMap<String, SimpleHttpResponse>()

  override fun definition() = ModuleDefinition {

    Name("ExpoHttpServer")

    Events("onStatusUpdate", "onRequest")

    Function("setup") { port: Int ->
      server = AndroidServer.Builder{
        port {
          port
        }
      }.build()
    }

    Function("route") { path: String, method: String, uuid: String ->
      server = server?.request(HttpMethod.getMethod(method), path) { request: Request, response: Response ->
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
        while (!responses.containsKey(uuid)) {
          Thread.sleep(10)
        }
        val res = responses[uuid]!!
        responses.remove(uuid);
        response.setBodyText(res.body)
        response.setStatus(res.statusCode)
        response.addHeader("Content-Length", "" + res.body.length)
        response.addHeader("Content-Type", res.contentType)
      };
    }

    Function("start") {
      if (server == null) {
        sendEvent("onStatusUpdate", bundleOf(
          "status" to "ERROR",
          "message" to "Server not setup / port not configured"
        ))
      } else {
        if (!started) {
          started = true
          server?.start()
          sendEvent("onStatusUpdate", bundleOf(
            "status" to "STARTED",
            "message" to "Server started"
          ))
        }
      }
    }

    Function("respond") { uuid: String,
                          statusCode: Int,
                          contentType: String,
                          body: String ->
      responses[uuid] = SimpleHttpResponse(statusCode, contentType, body);
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