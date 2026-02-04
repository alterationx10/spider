package spider.html

import munit.FunSuite
import scalatags.Text.all._
import spider.html.WebViewAttributes._

/** Tests for WebView-specific HTML attributes.
  *
  * Tests the WebView event attribute builders and convenience helpers.
  */
class WebViewAttributesSuite extends FunSuite {

  test("wvClick creates click attribute") {
    val html = button(wvClick := "increment")("Test").render
    assert(html.contains("""wv-click="increment""""))
  }

  test("wvChange creates change attribute") {
    val html = input(wvChange := "update-name").render
    assert(html.contains("""wv-change="update-name""""))
  }

  test("wvSubmit creates submit attribute") {
    val html = form(wvSubmit := "save-form").render
    assert(html.contains("""wv-submit="save-form""""))
  }

  test("wvInput creates input attribute") {
    val html = input(wvInput := "search").render
    assert(html.contains("""wv-input="search""""))
  }

  test("wvFocus creates focus attribute") {
    val html = input(wvFocus := "field-focused").render
    assert(html.contains("""wv-focus="field-focused""""))
  }

  test("wvBlur creates blur attribute") {
    val html = input(wvBlur := "field-blurred").render
    assert(html.contains("""wv-blur="field-blurred""""))
  }

  test("wvKeydown creates keydown attribute") {
    val html = input(wvKeydown := "handle-key").render
    assert(html.contains("""wv-keydown="handle-key""""))
  }

  test("wvKeyup creates keyup attribute") {
    val html = input(wvKeyup := "key-released").render
    assert(html.contains("""wv-keyup="key-released""""))
  }

  test("wvMouseenter creates mouseenter attribute") {
    val html = div(wvMouseenter := "show-tooltip").render
    assert(html.contains("""wv-mouseenter="show-tooltip""""))
  }

  test("wvMouseleave creates mouseleave attribute") {
    val html = div(wvMouseleave := "hide-tooltip").render
    assert(html.contains("""wv-mouseleave="hide-tooltip""""))
  }

  test("wvDebounce creates debounce attribute") {
    val html = input(wvDebounce := "300").render
    assert(html.contains("""wv-debounce="300""""))
  }

  test("wvThrottle creates throttle attribute") {
    val html = button(wvThrottle := "1000").render
    assert(html.contains("""wv-throttle="1000""""))
  }

  test("wvValue creates value attribute") {
    val html = button(wvValue := "item-id-123").render
    assert(html.contains("""wv-value="item-id-123""""))
  }

  test("wvTarget creates target attribute") {
    val html = button(wvTarget := "element-id").render
    assert(html.contains("""wv-target="element-id""""))
  }

  test("wvIgnore creates ignore attribute") {
    val html = input(wvIgnore := "true").render
    assert(html.contains("""wv-ignore="true""""))
  }

  test("wvAttr creates custom WebView attribute") {
    val html = button(wvAttr("custom") := "event-name")("Test").render
    assert(html.contains("""wv-custom="event-name""""))
  }

  test("wvClickTarget helper creates click and target attributes") {
    val html =
      button(wvClickTarget("delete-item", "item-123")*)("Delete").render

    assert(html.contains("""wv-click="delete-item""""))
    assert(html.contains("""wv-target="item-123""""))
  }

  test("wvClickValue helper creates click and value attributes") {
    val html = button(wvClickValue("set-filter", "active")*)("Filter").render

    assert(html.contains("""wv-click="set-filter""""))
    assert(html.contains("""wv-value="active""""))
  }

  test("wvDebounceInput helper creates input and debounce attributes") {
    val html = input(wvDebounceInput("search", 300)*).render

    assert(html.contains("""wv-input="search""""))
    assert(html.contains("""wv-debounce="300""""))
  }

  test("wvThrottleClick helper creates click and throttle attributes") {
    val html = button(wvThrottleClick("track-hover", 1000)*)("Track").render

    assert(html.contains("""wv-click="track-hover""""))
    assert(html.contains("""wv-throttle="1000""""))
  }

  test("WebView attributes escape special characters") {
    val html = button(wvClick := """<script>alert("xss")</script>""").render

    assert(!html.contains("<script>"))
    // Scalatags automatically escapes HTML in attributes
    assert(html.contains("&lt;") || html.contains("&amp;"))
  }

  test("Multiple WebView attributes on element") {
    val html = button(
      wvClick  := "submit",
      wvTarget := "form-1",
      cls      := "btn"
    )("Submit").render

    assert(html.contains("""wv-click="submit""""))
    assert(html.contains("""wv-target="form-1""""))
    assert(html.contains("""class="btn""""))
  }

  test("Debounced search input pattern") {
    val html = input(
      `type`      := "text",
      placeholder := "Search...",
      wvInput     := "search",
      wvDebounce  := "500"
    ).render

    assert(html.contains("""wv-input="search""""))
    assert(html.contains("""wv-debounce="500""""))
  }

  test("Button with target value pattern") {
    val html = button(
      wvClick  := "delete-todo",
      wvTarget := "todo-42",
      cls      := "btn-danger"
    )("Delete").render

    assert(html.contains("""wv-click="delete-todo""""))
    assert(html.contains("""wv-target="todo-42""""))
  }
}
