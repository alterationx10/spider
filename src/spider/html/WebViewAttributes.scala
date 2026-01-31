package spider.html

import scalatags.Text.all._
import scalatags.generic.AttrValue

/** WebView-specific HTML attributes for Scalatags.
  *
  * These attributes enable reactive, server-side event handling in Spider
  * WebView. When a user interacts with an element that has a WebView attribute,
  * an event is sent to the server over WebSocket.
  *
  * Example with string events:
  * {{{
  * import spider.html.WebViewAttributes._
  * import scalatags.Text.all._
  *
  * button(wvClick := "increment")("Click me")
  * input(wvChange := "update-name", value := name)
  * form(wvSubmit := "save")(...)
  * }}}
  *
  * The client-side JavaScript listens for these attributes and automatically
  * wires up event handlers that send events to the server.
  */
object WebViewAttributes {

  // === Primary WebView Attributes ===

  /** Handle click events.
    *
    * Fires when the user clicks the element.
    *
    * {{{
    * button(wvClick := "increment")("+")
    * // When clicked, sends: { event: "increment", payload: { target: elementId } }
    * }}}
    */
  val wvClick: Attr = attr("wv-click")

  /** Handle change events (input, select, textarea).
    *
    * Fires when the value of a form field changes.
    *
    * {{{
    * input(wvChange := "update-name")
    * // On change, sends: { event: "update-name", payload: { value: inputValue } }
    * }}}
    */
  val wvChange: Attr = attr("wv-change")

  /** Handle form submit events.
    *
    * Fires when a form is submitted. Automatically prevents default form
    * submission.
    *
    * {{{
    * form(wvSubmit := "save")(...)
    * // On submit, sends: { event: "save", payload: { formData: {...} } }
    * }}}
    */
  val wvSubmit: Attr = attr("wv-submit")

  /** Handle input events (fires on every keystroke).
    *
    * Similar to wvChange but fires immediately on each input.
    *
    * {{{
    * input(wvInput := "search")
    * // On each keystroke, sends: { event: "search", payload: { value: inputValue } }
    * }}}
    */
  val wvInput: Attr = attr("wv-input")

  /** Handle focus events.
    *
    * Fires when an element receives focus.
    *
    * {{{
    * input(wvFocus := "field-focused")
    * }}}
    */
  val wvFocus: Attr = attr("wv-focus")

  /** Handle blur events.
    *
    * Fires when an element loses focus.
    *
    * {{{
    * input(wvBlur := "field-blurred")
    * }}}
    */
  val wvBlur: Attr = attr("wv-blur")

  /** Handle keydown events.
    *
    * Fires when a key is pressed.
    *
    * {{{
    * input(wvKeydown := "handle-keypress")
    * // Sends: { event: "handle-keypress", payload: { key: "Enter", ... } }
    * }}}
    */
  val wvKeydown: Attr = attr("wv-keydown")

  /** Handle keyup events.
    *
    * Fires when a key is released.
    *
    * {{{
    * input(wvKeyup := "key-released")
    * }}}
    */
  val wvKeyup: Attr = attr("wv-keyup")

  /** Handle mouseenter events.
    *
    * Fires when the mouse enters an element.
    *
    * {{{
    * div(wvMouseenter := "show-tooltip")
    * }}}
    */
  val wvMouseenter: Attr = attr("wv-mouseenter")

  /** Handle mouseleave events.
    *
    * Fires when the mouse leaves an element.
    *
    * {{{
    * div(wvMouseleave := "hide-tooltip")
    * }}}
    */
  val wvMouseleave: Attr = attr("wv-mouseleave")

  // === Advanced WebView Attributes ===

  /** Debounce events (useful for search inputs).
    *
    * Delays sending events until the user stops typing.
    *
    * {{{
    * input(
    *   wvInput := "search",
    *   wvDebounce := "300"  // Wait 300ms after last keystroke
    * )
    * }}}
    */
  val wvDebounce: Attr = attr("wv-debounce")

  /** Throttle events (rate-limit event sending).
    *
    * Limits how often events can be sent.
    *
    * {{{
    * div(
    *   wvMouseenter := "track-hover",
    *   wvThrottle := "1000"  // Max once per second
    * )
    * }}}
    */
  val wvThrottle: Attr = attr("wv-throttle")

  /** Attach a value to the event payload.
    *
    * Useful for passing additional context with the event.
    *
    * {{{
    * button(
    *   wvClick := "select-item",
    *   wvValue := todo.id
    * )("Select")
    * // Sends: { event: "select-item", payload: { value: todo.id } }
    * }}}
    */
  val wvValue: Attr = attr("wv-value")

  /** Target element for event context.
    *
    * Specifies which element's data should be sent with the event.
    *
    * {{{
    * button(
    *   wvClick := "delete-item",
    *   wvTarget := item.id
    * )("Delete")
    * // Sends: { event: "delete-item", payload: { target: item.id } }
    * }}}
    */
  val wvTarget: Attr = attr("wv-target")

  /** Disable updates for this element.
    *
    * Prevents the WebView from updating this element's content on re-render.
    * Useful for preserving input focus or scroll position.
    *
    * {{{
    * input(
    *   wvChange := "update",
    *   wvIgnore := "true"
    * )
    * }}}
    */
  val wvIgnore: Attr = attr("wv-ignore")

  // === Helper Functions ===

  /** Create a custom WebView attribute.
    *
    * {{{
    * button(wvAttr("custom-event") := "handler")
    * }}}
    */
  def wvAttr(name: String): Attr = attr(s"wv-$name")

  /** Helper to create click event with target.
    *
    * {{{
    * button(wvClickTarget("delete", itemId)*)("Delete")
    * }}}
    */
  def wvClickTarget(event: String, target: String): Seq[AttrPair] = Seq(
    wvClick := event,
    wvTarget := target
  )

  /** Helper to create click event with value.
    *
    * {{{
    * button(wvClickValue("select", "option1")*)("Select")
    * }}}
    */
  def wvClickValue(event: String, value: String): Seq[AttrPair] = Seq(
    wvClick := event,
    wvValue := value
  )

  /** Helper to create debounced input.
    *
    * {{{
    * input(wvDebounceInput("search", 300)*)
    * }}}
    */
  def wvDebounceInput(event: String, delay: Int): Seq[AttrPair] = Seq(
    wvInput := event,
    wvDebounce := delay.toString
  )

  /** Helper to create throttled click.
    *
    * {{{
    * button(wvThrottleClick("track", 1000)*)("Track")
    * }}}
    */
  def wvThrottleClick(event: String, throttle: Int): Seq[AttrPair] = Seq(
    wvClick := event,
    wvThrottle := throttle.toString
  )

  // === Implicit Conversions ===

  /** Allow any Product (case class/object) to be used as an event attribute value.
    * Extracts the type name to use as the event name.
    */
  given productAttrValue[T <: Product]: AttrValue[scalatags.text.Builder, T] = {
    (t: scalatags.text.Builder, attr: Attr, value: T) => {
      val eventName = value.productPrefix
      t.setAttr(attr.name, scalatags.text.Builder.GenericAttrValueSource(eventName))
    }
  }
}
