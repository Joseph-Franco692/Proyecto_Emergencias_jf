// Polyfills required by some third-party libraries in browser builds.
// `sockjs-client` expects a global object similar to Node's `global`.
// We set it here before the rest of the application loads.

(window as any).global = window;
export {};
