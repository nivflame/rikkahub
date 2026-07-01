(function() {
  var port = browser.runtime.connectNative("browser");
  var ready = true;

  port.onMessage.addListener(function(message) {
    if (message && message.type === "eval") {
      try {
        var result = eval(message.code);
        port.postMessage({ type: "result", result: result == null ? "null" : (typeof result === "string" ? result : JSON.stringify(result)) });
      } catch (e) {
        port.postMessage({ type: "error", error: e.message });
      }
    }
  });

  port.onDisconnect.addListener(function() {
    ready = false;
  });

  port.postMessage({ type: "ready" });
})();
