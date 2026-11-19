/*
 * PoToken bridge.
 *
 * The real BotGuard client (`potoken.js`, shipped with the ytmusic crate) is not
 * vendored here. Phase 4 copies it into android/platform-webview/src/main/assets
 * and renames it `potoken.js`; this loader then:
 *
 *   1. fetches /potoken.js from the embed origin,
 *   2. asks it for a token bound to the `po_binding` query parameter,
 *   3. writes the answer into the SONORA_POT cookie so the Kotlin side can read it
 *      through CookieManager without a JavaScript interface.
 *
 * Keeping the bridge cookie-based (instead of addJavascriptInterface) means the
 * auth/token pages have no Java bridge exposed to page scripts at all.
 */
(function () {
  var binding = new URLSearchParams(location.search).get("po_binding") || "";
  var write = function (token) {
    if (!token) return;
    document.cookie =
      "SONORA_POT=" + encodeURIComponent(token) + "; path=/; secure; samesite=none";
  };

  function requestToken() {
    if (window.SonoraPotoken && typeof window.SonoraPotoken.mint === "function") {
      Promise.resolve(window.SonoraPotoken.mint(binding)).then(write).catch(function () {});
      return;
    }
    write("UNCONFIGURED");
  }

  var script = document.createElement("script");
  script.src = "/potoken.js";
  script.onload = requestToken;
  script.onerror = function () { write("UNCONFIGURED"); };
  document.head.appendChild(script);
})();
