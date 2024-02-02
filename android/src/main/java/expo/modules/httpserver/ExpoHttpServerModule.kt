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
  class SimpleHttpResponse(val status: Int, val body: String?, val rawString: String?)

  private lateinit var server: Server
  private val responses = HashMap<String, SimpleHttpResponse>()

  private val requestHandler: RequestHandler = { request: Request, response: Response ->
    val uuid = UUID.randomUUID().toString()
    val headers: Map<String, String> = request.headers()
    val params: Map<String, String> = request.params()
    val cookies: Map<String, String> = request.cookies().associate { it.name() to it.value() }
    sendEvent("onRequest", bundleOf(
      "uuid" to uuid,
      "method" to request.method().name,
      "path" to request.url(),
      "content" to request.content(),
      "headersJson" to JSONObject(headers).toString(),
      "paramsJson" to JSONObject(params).toString(),
      "cookiesJson" to JSONObject(cookies).toString(),
    ))
    while (!responses.containsKey(uuid)) {
      Thread.sleep(10)
    }
    val res = responses[uuid]!!
    responses.remove(uuid);
    if (!res.rawString.isNullOrEmpty()) {
      response.setStatus(res.status).setBodyText(res.rawString)
    } else if (!res.body.isNullOrEmpty()) {
      response.setStatus(res.status).setBodyJson(res.body)
    } else {
      response.setStatus(200).setBodyText("Success")
    }
  }

  override fun definition() = ModuleDefinition {

    Name("ExpoHttpServer")

    Events("onRequest")

    Function("setup") { port: Int ->
      server = AndroidServer.Builder{
        port {
          port
        }
      }.build()
    }

    Function("route") { path: String, method: String ->
      server = server.request(HttpMethod.getMethod(method), path, requestHandler);
    }

    Function("start") {
      server.start()
    }

    Function("respond") { uuid: String, status: Int, body: String, rawString: String ->
      responses[uuid] = SimpleHttpResponse(status, body, rawString)
    }

    Function("stop") {
      server.close()
    }
  }
}