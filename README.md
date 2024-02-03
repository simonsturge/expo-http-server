
# expo-http-server

[![npm](https://img.shields.io/npm/v/expo-http-server?style=for-the-badge)](https://www.npmjs.com/package/expo-http-server)
[![npm](https://img.shields.io/npm/dt/expo-http-server?style=for-the-badge)](https://www.npmjs.com/package/expo-http-server)
[![GitHub contributors](https://img.shields.io/github/contributors/simonsturge/expo-http-server?style=for-the-badge)](https://github.com/simonsturge/expo-http-server)
[![GitHub Repo stars](https://img.shields.io/github/stars/simonsturge/expo-http-server?style=for-the-badge)](https://github.com/simonsturge/expo-http-server)

A simple HTTP Server [Expo module](https://docs.expo.dev/modules/) .

Current implementation is for iOS / Android only ([React Native](https://github.com/facebook/react-native)).

iOS: [Criollo](https://github.com/thecatalinstan/Criollo)
Android: [AndroidServer](https://github.com/fengzhizi715/AndroidServer)
Web: **Not implemented**

This is a work in progress and not yet used in a production app (there are surely things I've overlooked), but please do make a PR for any improvements / problems you can see!

## Install

```shell
npx expo install expo-http-server
```

## Example
```tsx
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
      console.log("Request", "/", "GET", JSON.stringify(request));
      setLastCalled(Date.now());
      return {
        statusCode: 200,
        contentType: "application/json",
        body: JSON.stringify(obj),
      };
    });
    server.route("/html", "GET", async (request) => {
      console.log("Request", "/html", "GET", JSON.stringify(request));
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

```

Not that on iOS when the app is backgrounded the server will inevitably get paused. A background task will be started automatically, however you will likely only get 30 seconds of listening time in the background.

Send a request to the server in a browser `browser` or `curl`:

```shell
curl http://IP_OF_DEVICE:MY_PORT
```
For example:
```shell
curl http://192.168.1.109:3000
```
