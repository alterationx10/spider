package spider

import munit.FunSuite

/** Base trait for WebView testing with helper methods. */
trait WebViewSuite extends FunSuite {

  /** Mount a WebView with optional params and session. */
  def mount[State, Event](
      view: WebView[State, Event],
      params: Map[String, String] = Map.empty,
      session: Session = Session()
  ): State = {
    view.mount(params, session)
  }

  /** Send an event to a WebView and get the new state. */
  def sendEvent[State, Event](
      view: WebView[State, Event],
      event: Event,
      state: State
  ): State = {
    view.handleEvent(event, state)
  }

  /** Send multiple events in sequence. */
  def sendEvents[State, Event](
      view: WebView[State, Event],
      state: State,
      events: Seq[Event]
  ): State = {
    events.foldLeft(state)((s, e) => view.handleEvent(e, s))
  }

  /** Send an info message to a WebView. */
  def sendInfo[State, Event](
      view: WebView[State, Event],
      msg: Any,
      state: State
  ): State = {
    view.handleInfo(msg, state)
  }

  /** Render a WebView's state (applies beforeRender transform). */
  def render[State, Event](
      view: WebView[State, Event],
      state: State
  ): String = {
    val transformedState = view.beforeRender(state)
    view.render(transformedState)
  }

  /** Mount a WebView, send events, and render - returns final state and HTML. */
  def mountAndRender[State, Event](
      view: WebView[State, Event],
      events: Seq[Event] = Seq.empty,
      params: Map[String, String] = Map.empty,
      session: Session = Session()
  ): (State, String) = {
    val state       = mount(view, params, session)
    val finalState  = sendEvents(view, state, events)
    val html        = render(view, finalState)
    (finalState, html)
  }
}
