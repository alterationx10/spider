package spider

import keanu.actors.ActorSystem

/** Helper utilities for setting up Cask-based Spider WebView servers.
  *
  * Spider now uses Cask-idiomatic patterns for defining servers. Instead of a
  * builder pattern, you define your server as a Cask `MainRoutes` class.
  *
  * ==Quick Start==
  *
  * {{{
  * import spider._
  * import spider.http.ResourceServer
  * import keanu.actors.ActorSystem
  *
  * // Define your WebView
  * case class CounterState(count: Int)
  *
  * sealed trait CounterEvent derives EventCodec
  * case object Increment extends CounterEvent
  * case object Decrement extends CounterEvent
  *
  * class CounterWebView extends WebView[CounterState, CounterEvent] {
  *   override def mount(params: Map[String, String], session: Session) =
  *     CounterState(0)
  *
  *   override def handleEvent(event: CounterEvent, state: CounterState) =
  *     event match {
  *       case Increment => state.copy(count = state.count + 1)
  *       case Decrement => state.copy(count = state.count - 1)
  *     }
  *
  *   override def render(state: CounterState) =
  *     s\"\"\"
  *       <div>
  *         <h1>Count: ${state.count}</h1>
  *         <button wv-click="Increment">+</button>
  *         <button wv-click="Decrement">-</button>
  *       </div>
  *     \"\"\"
  * }
  *
  * // Define your server
  * object MyApp extends cask.MainRoutes {
  *   val actorSystem = ActorSystem()
  *
  *   // Serve the counter page
  *   @cask.get("/counter")
  *   def counterPage() = WebViewPageHandler.response(
  *     wsUrl = "ws://localhost:8080/counter",
  *     title = "Counter Demo"
  *   )
  *
  *   // WebSocket endpoint for the counter
  *   @cask.websocket("/counter")
  *   def counterWs() = WebViewHandler.createWsHandler(
  *     actorSystem,
  *     () => new CounterWebView()
  *   )
  *
  *   // Serve static resources (webview.js)
  *   @cask.get("/js", subpath = true)
  *   def js(request: cask.Request) = {
  *     val path = request.remainingPathSegments.mkString("/")
  *     ResourceServer.serveText("spider", path)
  *   }
  *
  *   initialize()
  * }
  *
  * @main def run() = MyApp.main(Array.empty)
  * }}}
  *
  * ==With DevTools==
  *
  * {{{
  * object MyApp extends cask.MainRoutes {
  *   val actorSystem = ActorSystem()
  *   val devMode = true
  *   val devToolsActorName = if (devMode) Some("__devtools-actor") else None
  *
  *   // Your WebView with DevTools integration
  *   @cask.websocket("/counter")
  *   def counterWs() = WebViewHandler.createWsHandler(
  *     actorSystem,
  *     () => new CounterWebView(),
  *     devToolsActorName = devToolsActorName
  *   )
  *
  *   // DevTools page and WebSocket (if devMode enabled)
  *   @cask.get("/__devtools")
  *   def devToolsPage() = WebViewPageHandler.response(
  *     wsUrl = "ws://localhost:8080/__devtools",
  *     title = "Spider DevTools"
  *   )
  *
  *   @cask.websocket("/__devtools")
  *   def devToolsWs() = {
  *     import spider.devtools._
  *     WebViewHandler.createWsHandler(
  *       actorSystem,
  *       () => new DevToolsWebView(),
  *       useExistingActor = devToolsActorName
  *     )
  *   }
  *
  *   initialize()
  * }
  * }}}
  */
object WebViewServer {

  /** Create an ActorSystem for WebView applications.
    *
    * This is a convenience method that creates a default ActorSystem.
    *
    * @return
    *   A new ActorSystem
    */
  def createActorSystem(): ActorSystem = ActorSystem()

  /** Configuration for a single WebView route.
    *
    * This is used internally for type-safe route configuration.
    *
    * @param factory
    *   Factory function to create WebView instances
    * @param params
    *   URL query parameters to pass to mount
    * @param session
    *   Session data to pass to mount
    * @param eventCodec
    *   Codec for encoding/decoding events
    * @tparam State
    *   The state type of the WebView
    * @tparam Event
    *   The event type of the WebView
    */
  case class WebViewRoute[State, Event](
      factory: () => WebView[State, Event],
      params: Map[String, String] = Map.empty,
      session: Session = Session(),
      eventCodec: EventCodec[Event]
  )
}
