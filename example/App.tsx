import * as server from "expo-http-server";
import { useEffect, useState } from "react";
import { Text, View } from "react-native";

export default function App() {
  const [lastCalled, setLastCalled] = useState<number | undefined>();

  useEffect(() => {
    server.setup(9666);
    server.route("/", "GET", async (request) => {
      console.log("Request", "/", "GET", JSON.stringify(request));
      setLastCalled(Date.now());
      return {
        status: 200,
        rawString: "Hello World",
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
