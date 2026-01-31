package spider

/** Helper for generating WebView HTML pages.
  *
  * This generates an HTML page that:
  *   1. Loads the WebView client JavaScript
  *   2. Provides a div for the WebView to render into
  *   3. Connects to the WebSocket endpoint
  *
  * Example:
  * {{{
  * object MyApp extends cask.MainRoutes {
  *   @cask.get("/counter")
  *   def counterPage() = WebViewPageHandler.response(
  *     wsUrl = "ws://localhost:8080/counter",
  *     title = "Counter Demo"
  *   )
  *
  *   initialize()
  * }
  * }}}
  */
object WebViewPageHandler {

  /** Create a Cask Response containing the WebView page HTML.
    *
    * @param wsUrl
    *   The WebSocket URL to connect to (e.g., "ws://localhost:8080/counter")
    * @param title
    *   The page title
    * @param jsPath
    *   The path to the webview.js file (relative to server root)
    * @param rootId
    *   The ID of the root div for rendering (default: "root")
    * @param debug
    *   Enable debug mode in the client (default: false)
    * @return
    *   A Cask Response with the HTML page
    */
  def response(
      wsUrl: String,
      title: String = "Spider WebView",
      jsPath: String = "/js/webview.js",
      rootId: String = "root",
      debug: Boolean = false
  ): cask.Response[String] = {
    val html = generateHtml(wsUrl, title, jsPath, rootId, debug)
    cask.Response(html, headers = Seq("Content-Type" -> "text/html; charset=utf-8"))
  }

  /** Generate the HTML page content.
    *
    * @param wsUrl
    *   The WebSocket URL to connect to
    * @param title
    *   The page title
    * @param jsPath
    *   The path to the webview.js file
    * @param rootId
    *   The ID of the root div for rendering
    * @param debug
    *   Enable debug mode in the client
    * @return
    *   The HTML string
    */
  def generateHtml(
      wsUrl: String,
      title: String = "Spider WebView",
      jsPath: String = "/js/webview.js",
      rootId: String = "root",
      debug: Boolean = false
  ): String = {
    s"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>$title</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      background: #edf2f7;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    #connection-status {
      position: fixed;
      top: 10px;
      right: 10px;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 0.875rem;
      font-weight: 500;
      z-index: 1000;
      transition: all 0.3s ease;
    }
    #connection-status.connected {
      background: #48bb78;
      color: white;
    }
    #connection-status.disconnected {
      background: #f56565;
      color: white;
    }
    #connection-status.connecting {
      background: #ed8936;
      color: white;
    }
    #loading {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100vh;
      font-size: 1.5rem;
      color: #718096;
    }
    #$rootId {
      min-height: 100vh;
    }
  </style>
</head>
<body>
  <div id="connection-status" class="connecting">Connecting...</div>
  <div id="loading">Loading WebView...</div>
  <div id="$rootId"></div>

  <script src="$jsPath"></script>
  <script>
    // Create the WebView connection
    const webview = new BranchWebView('$wsUrl', {
      rootSelector: '#$rootId',
      debug: $debug
    });

    // Update connection status indicator
    const statusEl = document.getElementById('connection-status');
    const loadingEl = document.getElementById('loading');

    document.addEventListener('webview:connected', () => {
      statusEl.textContent = 'Connected';
      statusEl.className = 'connected';
      loadingEl.style.display = 'none';
    });

    document.addEventListener('webview:disconnected', () => {
      statusEl.textContent = 'Disconnected';
      statusEl.className = 'disconnected';
      loadingEl.style.display = 'flex';
    });

    document.addEventListener('webview:error', (e) => {
      console.error('WebView error:', e.detail);
    });

    document.addEventListener('webview:updated', (e) => {
      console.log('WebView updated:', e.detail);
    });
  </script>
</body>
</html>
"""
  }
}
