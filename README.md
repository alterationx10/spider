# Spider

A server-side reactive UI library for Scala 3, inspired by Phoenix LiveView. UI state lives on the server, and updates are pushed to clients over WebSocket.

**Status:** Early stage. I recently ported from some other hobby libraries to use some Li Haoyi's ecosystem (Cask, Scalatags, uPickle). 
Still getting things set up, s there may be some rough edges, but the example is working

## Dependencies

Note - not published yet while working through the port!
```yaml
mvnDeps:
  - dev.alteration::spider:0.0.15
```

Spider uses:
- [Cask](https://com-lihaoyi.github.io/cask/) for HTTP/WebSocket
- [Scalatags](https://com-lihaoyi.github.io/scalatags/) for HTML
- [uPickle](https://com-lihaoyi.github.io/upickle/) for JSON
- [Keanu](https://github.com/alterationx10/keanu) for actors

## Example

This example is also in the source code for now, until the library gets re-published, so there is an easy to run example.

```scala
import keanu.actors.ActorSystem
import spider.*
import spider.http.ResourceServer
import upickle.default.*

// State
case class CounterState(count: Int)

// Events
sealed trait CounterEvent derives ReadWriter
case object Increment extends CounterEvent
case object Decrement extends CounterEvent

object CounterEvent {
  given EventCodec[CounterEvent] = EventCodec.derived
}

// WebView
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
        <h1>Count: ${state.count}</h1>
        <div style="display: flex; gap: 10px; justify-content: center;">
          <button wv-click="Decrement">-</button>
          <button wv-click="Increment">+</button>
        </div>
      </div>
    """
}

// Server (using Cask)
object ExampleApp extends cask.MainRoutes {
  import CounterEvent.given

  val actorSystem: ActorSystem = ActorSystem()

  @cask.get("/counter")
  def counterPage(): cask.Response[String] = WebViewPageHandler.response(
    wsUrl = "ws://localhost:8080/counter",
    title = "Counter Demo"
  )

  @cask.websocket("/counter")
  def counterWs(): cask.WebsocketResult = WebViewHandler.createWsHandler(
    actorSystem,
    () => new CounterWebView()
  )

  @cask.get("/js", subpath = true)
  def js(request: cask.Request): cask.Response[String] = {
    val path = request.remainingPathSegments.mkString("/")
    ResourceServer.serveText("", path)
  }

  println("Starting Spider Counter Example on http://localhost:8080/counter")
  initialize()
}
```

## Core Concepts

### WebView Trait

```scala
trait WebView[State, Event] {
  def mount(params: Map[String, String], session: Session): State
  def handleEvent(event: Event, state: State): State
  def handleInfo(msg: Any, state: State): State = state
  def render(state: State): String
  def terminate(reason: Option[Throwable], state: State): Unit = {}
}
```

### Events

Events use a sealed trait with `derives ReadWriter` (from uPickle) and need an `EventCodec`:

```scala
sealed trait MyEvent derives ReadWriter, EventCodec
case class DoThing(value: String) extends MyEvent
case object Reset extends MyEvent
```

### HTML Attributes

Spider provides custom attributes for wiring up events:

```scala
import spider.html.WebViewAttributes.*

// In your render method (using Scalatags):
button(wvClick := "Increment")("+")
input(wvChange := "UpdateText", value := state.text)
```

Available attributes:
- `wvClick` - click events
- `wvChange` - change events
- `wvInput` - input events (fires on every keystroke)
- `wvSubmit` - form submit
- `wvFocus`, `wvBlur` - focus events
- `wvKeydown`, `wvKeyup` - keyboard events
- `wvTarget` - attach a target ID to events
- `wvValue` - attach a value to events
- `wvDebounce`, `wvThrottle` - rate limiting
- `wvIgnore` - prevent DOM updates for an element

### Lifecycle Hooks

```scala
class MyWebView extends WebView[MyState, MyEvent] {
  override def afterMount(state: MyState, context: WebViewContext): Unit = {
    // Called after mount, can access actor system
  }

  override def beforeUpdate(event: MyEvent, state: MyState, context: WebViewContext): Unit = {
    // Called before processing an event
  }

  override def afterUpdate(event: MyEvent, oldState: MyState, newState: MyState, context: WebViewContext): Unit = {
    // Called after processing an event
  }

  override def beforeRender(state: MyState): MyState = {
    // Transform state before rendering
    state
  }
}
```

### Actor Communication

WebViews run inside actors. You can send messages to other actors and receive them via `handleInfo`:

```scala
override def afterMount(state: State, context: WebViewContext): Unit = {
  // Send message to self (received via handleInfo)
  context.sendSelf(LoadData)

  // Send message to another actor
  context.tellPath("/user/some-actor", SomeMessage)
}

override def handleInfo(msg: Any, state: State): State = {
  msg match {
    case DataLoaded(data) => state.copy(data = data)
    case _ => state
  }
}
```

### Error Handling

```scala
override def onError(error: Throwable, state: State, phase: ErrorPhase): Option[State] = {
  // Return Some(state) to recover, None to show error UI
  Some(state.copy(error = Some(error.getMessage)))
}

override def renderError(error: Throwable, phase: ErrorPhase): String = {
  s"<div>Error: ${error.getMessage}</div>"
}
```

## DevTools

Spider includes a DevTools WebView for debugging. See `spider.devtools.DevToolsWebView`.

## License

Apache 2.0
