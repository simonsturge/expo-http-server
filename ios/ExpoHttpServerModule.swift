import ExpoModulesCore
import Criollo
import Foundation

public class ExpoHttpServerModule: Module {
    private let server = CRHTTPServer()
    private var port: Int?
    private var stopped = false
    private var responses = [String: CRResponse]()
    private var bgTaskIdentifier = UIBackgroundTaskIdentifier.invalid
    
    public func definition() -> ModuleDefinition {
        Name("ExpoHttpServer")

        Events("onStatusUpdate", "onRequest")
        
        Function("setup", setupHandler)
        Function("start", startHandler)
        Function("route", routeHandler)
        Function("respond", respondHandler)
        Function("stop", stopHandler)
    }
    
    private func setupHandler(port: Int) {
        self.port = port;
    }

    private func startHandler() {
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [unowned self] notification in
            if (!self.stopped) {
                self.startServer(status: "RESUMED", message: "Server resumed")
            }
        }
        stopped = false;
        startServer(status: "STARTED", message: "Server started")
    }
    
    private func routeHandler(path: String, method: String, uuid: String) {
        server.add(path, block: { (req, res, next) in
            DispatchQueue.main.async {
                var bodyString = "{}"
                if let body = req.body, let bodyData = try? JSONSerialization.data(withJSONObject: body) {
                    bodyString = String(data: bodyData, encoding: .utf8) ?? "{}"
                }
                let requestId = UUID().uuidString
                self.responses[requestId] = res
                self.sendEvent("onRequest", [
                    "uuid": uuid,
                    "requestId": requestId,
                    "method": req.method.toString(),
                    "path": path,
                    "body": bodyString,
                    "headersJson": req.allHTTPHeaderFields.jsonString,
                    "paramsJson": req.query.jsonString,
                    "cookiesJson": req.cookies?.jsonString ?? "{}"
                ])
            }
        }, recursive: false, method: CRHTTPMethod.fromString(method))
    }
    
    private func respondHandler(requestId: String,
                                statusCode: Int,
                                statusDescription: String,
                                contentType: String,
                                headers: [String: String],
                                body: String) {
        DispatchQueue.main.async {
            if let response = self.responses[requestId] {
                response.setStatusCode(UInt(statusCode), description: statusDescription)
                response.setValue(contentType, forHTTPHeaderField: "Content-type")
                response.setValue("\(body.count)", forHTTPHeaderField: "Content-Length")
                for (key, value) in headers {
                    response.setValue(value, forHTTPHeaderField: key)
                }
                response.send(body);
                self.responses[requestId] = nil;
            }
        }
       
    }
    
    private func stopHandler() {
        stopped = true;
        stopServer(status: "STOPPED", message: "Server stopped")
    }
        
    private func startServer(status: String, message: String) {
        stopServer()
        if let port = port {
            var error: NSError?
            server.startListening(&error, portNumber: UInt(port))
            if (error != nil) {
                sendEvent("onStatusUpdate", [
                    "status": "ERROR",
                    "message": error?.localizedDescription ?? "Unknown error starting server"
                ])
            } else {
                beginBackgroundTask()
                sendEvent("onStatusUpdate", [
                    "status": status,
                    "message": message
                ])
            }
        } else {
            sendEvent("onStatusUpdate", [
                "status": "ERROR",
                "message": "Can't start server with port configured"
            ])
        }
    }
    
    private func stopServer(status: String? = nil, message: String? = nil) {
        server.stopListening()
        endBackgroundTask()
        if let status = status, let message = message {
            sendEvent("onStatusUpdate", [
                "status": status,
                "message": message
            ])
        }
    }
    
    private func beginBackgroundTask() {
        if (bgTaskIdentifier == UIBackgroundTaskIdentifier.invalid) {
            self.bgTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: "BgTask", expirationHandler: {
                self.stopServer(status: "PAUSED", message: "Server paused")
            })
        }
    }

    private func endBackgroundTask() {
        if (bgTaskIdentifier != UIBackgroundTaskIdentifier.invalid) {
            UIApplication.shared.endBackgroundTask(bgTaskIdentifier)
            bgTaskIdentifier = UIBackgroundTaskIdentifier.invalid
        }
    }
}

extension Dictionary {
    var jsonString: String {
        guard let data = try? JSONSerialization.data(withJSONObject: self) else {
            return "{}";
        }
        return String(data: data, encoding: .utf8) ?? "{}"
    }
}

extension CRHTTPMethod {
    func toString() -> String {
        switch self {
        case .post:
            return "POST"
        case .put:
            return "PUT"
        case .delete:
            return "DELETE"
        default:
            return "GET"
        }
    }
    
    static func fromString(_ string: String) -> Self {
        var httpMethod: CRHTTPMethod
        switch (string) {
        case "POST":
            httpMethod = .post
        case "PUT":
            httpMethod = .put
        case "DELETE":
            httpMethod = .delete
        default:
            httpMethod = .get
        }
        return httpMethod
    }
}
