package spider

import mustachio.{Mustachio, Stache}
import mustachio.Stache.*

import scala.io.Source
import scala.util.*

/** Helper for generating WebView HTML pages.
  *
  * This generates an HTML page that:
  *   1. Loads the WebView client JavaScript 2. Provides a div for the WebView
  *      to render into 3. Connects to the WebSocket endpoint
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

  lazy val defaultTemplate = scala.util.Using(
    Source.fromResource("templates/site.html.mustache")
  )(_.mkString)

  val defaultStache = Stache.obj(
    "jsPath" -> Str("/public/js/webview.js"),
    "rootId" -> Str("root"),
    "debug"  -> Str("false")
  )

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
      siteTemplate: Try[String] = defaultTemplate,
      templateStache: Obj = defaultStache
  ): cask.Response[String] = {
    val renderedTemplate = siteTemplate
      .map { tmpl =>
        val stache = Stache.Obj(
          templateStache.value + ("wsUrl" -> Str(wsUrl))
        )
        Mustachio.render(tmpl, stache)
      }

    renderedTemplate match {
      case Success(html)      =>
        cask.Response(
          html,
          headers = Seq("Content-Type" -> "text/html; charset=utf-8")
        )
      case Failure(exception) =>
        cask.Response(
          s"There was an error...\n${exception.getMessage}",
          headers = Seq("Content-Type" -> "text/html; charset=utf-8"),
          statusCode = 500
        )
    }

  }

}
