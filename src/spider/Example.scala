package spider

import keanu.actors.ActorSystem
import spider.http.ResourceServer
import upickle.default.*

// Define your WebView state and events
case class CounterState(count: Int)

sealed trait CounterEvent derives ReadWriter, EventCodec
case object Increment extends CounterEvent
case object Decrement extends CounterEvent

// Define the WebView
class CounterWebView extends WebView[CounterState, CounterEvent] {

  override def mount(
      params: Map[String, String],
      session: Session
  ): CounterState =
    CounterState(params.get("initial").flatMap(_.toIntOption).getOrElse(0))

  override def handleEvent(
      event: CounterEvent,
      state: CounterState
  ): CounterState =
    event match {
      case Increment => state.copy(count = state.count + 1)
      case Decrement => state.copy(count = state.count - 1)
    }

  override def render(state: CounterState): String =
    s"""
      <div style="font-family: sans-serif; max-width: 400px; margin: 50px auto; text-align: center;">
        <h1 style="color: #667eea;">Count: ${state.count}</h1>
        <div style="display: flex; gap: 10px; justify-content: center;">
          <button wv-click="Decrement" style="padding: 10px 20px; font-size: 1.5rem; cursor: pointer;">-</button>
          <button wv-click="Increment" style="padding: 10px 20px; font-size: 1.5rem; cursor: pointer;">+</button>
        </div>
      </div>
    """
}

// Define the server
object ExampleApp extends cask.MainRoutes {
  import CounterEvent.given

  val actorSystem: ActorSystem = ActorSystem()

  // Serve the counter page
  @cask.get("/counter")
  def counterPage(): cask.Response[String] = WebViewPageHandler.response(
    wsUrl = "ws://localhost:8080/counter",
    title = "Counter Demo"
  )

  // WebSocket endpoint for the counter
  @cask.websocket("/counter")
  def counterWs(): cask.WebsocketResult = WebViewHandler.createWsHandler(
    actorSystem,
    () => new CounterWebView()
  )

  // Serve static resources (webview.js from resources/)
  @cask.get("/js", subpath = true)
  def js(request: cask.Request): cask.Response[String] = {
    val path = request.remainingPathSegments.mkString("/")
    ResourceServer.serveText("", path)
  }

  println("Starting Spider Counter Example on http://localhost:8080/counter")
  initialize()
}
