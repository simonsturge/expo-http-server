import { EventEmitter } from "expo-modules-core";

import ExpoHttpServerModule from "./ExpoHttpServerModule";

const emitter = new EventEmitter(ExpoHttpServerModule);
const requestCallbacks: Callback[] = [];

export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE";
/**
 * PAUSED AND RESUMED are iOS only
 */
export type Status = "STARTED" | "PAUSED" | "RESUMED" | "STOPPED" | "ERROR";

export interface StatusEvent {
  status: Status;
  message: string;
}

export interface RequestEvent {
  uuid: string;
  method: string;
  path: string;
  body: string;
  headersJson: string;
  paramsJson: string;
  cookiesJson: string;
}

export interface Response {
  statusCode?: number;
  contentType?: string;
  body?: string;
}

export interface Callback {
  method: string;
  path: string;
  callback: (request: RequestEvent) => Promise<Response>;
}

export const start = () => {
  emitter.addListener<RequestEvent>("onRequest", async (event) => {
    const callbacks = requestCallbacks.filter(
      (c) => event.path.includes(c.path) && event.method === c.method,
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
        response.statusCode || 200,
        response.contentType || "application/json",
        response.body || "{}",
      );
    }
  });
  ExpoHttpServerModule.start();
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

export const setup = (
  port: number,
  onStatusUpdate?: (event: StatusEvent) => void,
) => {
  if (onStatusUpdate) {
    emitter.addListener<StatusEvent>("onStatusUpdate", async (event) => {
      onStatusUpdate(event);
    });
  }
  ExpoHttpServerModule.setup(port);
};

export const stop = () => ExpoHttpServerModule.stop();
