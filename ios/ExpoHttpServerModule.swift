import ExpoModulesCore
import Criollo

public class ExpoHttpServerModule: Module {
    private let server = CRHTTPServer()
    private var port: Int?
    private var responses = [String: CRResponse]()
    private var bgTaskIdentifier = UIBackgroundTaskIdentifier.invalid
    
    public func definition() -> ModuleDefinition {
        Name("ExpoHttpServer")

        Events("onRequest")
        
        Function("setup", setupHandler)
        Function("start", startHandler)
        Function("route", routeHandler)
        Function("respond", respondHandler)
        Function("stop", stopHandler)
    }
    
    private func setupHandler(port: Int) {
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [unowned self] notification in
            if (self.server.isListening) {
                self.endBackgroundTask()
                self.beginBackgroundTask()
            }
        }
        self.port = port;
    }

    private func startHandler() {
        if (server.isListening) { return }
        beginBackgroundTask()
        var error: NSError?
        if let port = port {
            server.startListening(&error, portNumber: UInt(port))
        } else {
            server.startListening(&error)
        }
    }
    
    private func routeHandler(path: String, method: String) {
        server.add(path, block: { [weak self] (req, res, next) in
            let uuid = UUID().uuidString
            self?.sendEvent("onRequest", [
                "uuid": uuid,
                "method": req.method.toString(),
                "path": path,
                "body": req.body,
                "headersJson": req.allHTTPHeaderFields.jsonString,
                "paramsJson": req.query.jsonString,
                "cookiesJson": req.cookies?.jsonString ?? "{}"
            ])
            self?.responses[uuid] = res
        }, recursive: false, method: CRHTTPMethod.fromString(method))
    }
    
    private func respondHandler(udid: String,
                                statusCode: Int,
                                contentType: String,
                                body: String) {
       if let response = responses[udid] {
           response.setStatusCode(UInt(statusCode), description: "Success")
           response.setValue(contentType, forHTTPHeaderField: "Content-type")
           response.setValue("\(body.count)", forHTTPHeaderField: "Content-Length")
           response.send(body);
           responses[udid] = nil;
       }
    }
    
    private func stopHandler() {
        if (server.isListening) {
            server.stopListening()
            endBackgroundTask()
        }
    }
    
    private func beginBackgroundTask() {
        if (bgTaskIdentifier == UIBackgroundTaskIdentifier.invalid) {
            self.bgTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: "BgTask", expirationHandler: {
                self.endBackgroundTask()
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