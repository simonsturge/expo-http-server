import { EventEmitter } from "expo-modules-core";

import ExpoHttpServerModule from "./ExpoHttpServerModule";

const emitter = new EventEmitter(ExpoHttpServerModule);

export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE";

export interface RequestEvent {
  uuid: string;
  method: string;
  path: string;
  content: string;
  headersJson: string;
  paramsJson: string;
  cookiesJson: string;
}

export interface Callback {
  method: string;
  path: string;
  callback: (request: RequestEvent) => Promise<Response>;
}

const requestCallbacks: Callback[] = [];

export interface Response {
  status: number;
  body?: string;
  rawString?: string;
}

export const setup = (port: number) => {
  ExpoHttpServerModule.setup(port);
};

export const start = () => {
  ExpoHttpServerModule.start();
  emitter.addListener<RequestEvent>("onRequest", async (event) => {
    const callbacks = requestCallbacks.filter(
      (c) => event.path === c.path && event.method === c.method,
    );
    if (!callbacks.length) {
      ExpoHttpServerModule.respond(
        event.uuid,
        200,
        "No callback",
        "No callback",
      );
      return;
    }
    for (const c of callbacks) {
      const response = await c.callback(event);
      ExpoHttpServerModule.respond(
        event.uuid,
        response.status,
        response.body || "",
        response.rawString || "",
      );
    }
  });
};

export const route = (
  path: string,
  method: HttpMethod,
  callback: (request: RequestEvent) => Promise<Response>,
) => {
  requestCallbacks.push({
    method,
    path,
    callback,
  });
  ExpoHttpServerModule.route(path, method);
};

export const stop = () => ExpoHttpServerModule.stop();
