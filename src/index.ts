import { EventEmitter } from "expo-modules-core";

import ExpoHttpServerModule from "./ExpoHttpServerModule";

const emitter = new EventEmitter(ExpoHttpServerModule);
const requestCallbacks: Callback[] = [];

export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "OPTIONS";
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
  requestId: string;
  method: string;
  path: string;
  body: string;
  headersJson: string;
  paramsJson: string;
  cookiesJson: string;
}

export interface Response {
  statusCode?: number;
  statusDescription?: string;
  contentType?: string;
  headers?: Record<string, string>;
  body?: string;
}

export interface Callback {
  method: string;
  path: string;
  uuid: string;
  callback: (request: RequestEvent) => Promise<Response>;
}

export const start = () => {
  emitter.addListener<RequestEvent>("onRequest", async (event) => {
    const responseHandler = requestCallbacks.find((c) => c.uuid === event.uuid);
    if (!responseHandler) {
      ExpoHttpServerModule.respond(
        event.requestId,
        404,
        "Not Found",
        "application/json",
        {},
        JSON.stringify({ error: "Handler not found" }),
      );
      return;
    }
    const response = await responseHandler.callback(event);
    ExpoHttpServerModule.respond(
      event.requestId,
      response.statusCode || 200,
      response.statusDescription || "OK",
      response.contentType || "application/json",
      response.headers || {},
      response.body || "{}",
    );
  });
  ExpoHttpServerModule.start();
};

export const route = (
  path: string,
  method: HttpMethod,
  callback: (request: RequestEvent) => Promise<Response>,
) => {
  const uuid = Math.random().toString(16).slice(2);
  requestCallbacks.push({
    method,
    path,
    uuid,
    callback,
  });
  ExpoHttpServerModule.route(path, method, uuid);
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
