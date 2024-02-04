import * as server from "expo-http-server";
import { useEffect, useState } from "react";
import { Text, View } from "react-native";

export default function App() {
  const [lastCalled, setLastCalled] = useState<number | undefined>();

  const html = `
	<!DOCTYPE html>
	<html>
		<body style="background-color:powderblue;">
			<h1>expo-http-server</h1>
			<p>You can load HTML!</p>
		</body>
	</html>`;

  const obj = { app: "expo-http-server", desc: "You can load JSON!" };

  useEffect(() => {
    server.setup(9666);
    server.route("/", "GET", async (request) => {
      console.log("Request", "/", "GET", request);
      setLastCalled(Date.now());
      return {
        statusCode: 200,
        contentType: "application/json",
        body: JSON.stringify(obj),
      };
    });
    server.route("/html", "GET", async (request) => {
      console.log("Request", "/html", "GET", request);
      setLastCalled(Date.now());
      return {
        statusCode: 200,
        contentType: "text/html",
        body: html,
      };
    });
    server.start();
    return () => {
      server.stop();
    };
  }, []);

  return (
    <View
      style={{
        flex: 1,
        backgroundColor: "#fff",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Text>
        {lastCalled === undefined
          ? "Request webserver to change text"
          : "Called at " + new Date(lastCalled).toLocaleString()}
      </Text>
    </View>
  );
}
