package spider

import munit.FunSuite
import ujson._
import spider.WebViewProtocol._

/** Tests for WebViewProtocol.
  *
  * Tests client/server message parsing and serialization.
  */
class WebViewProtocolSuite extends FunSuite {

  // === Client Messages ===

  test("parse ClientReady message") {
    val json   = Obj("type" -> Str("ready"))
    val result = parseClientMessage(json)

    assertEquals(result, Some(ClientReady))
  }

  test("parse Event message with all fields") {
    val json   = Obj(
      "type"   -> Str("event"),
      "event"  -> Str("increment"),
      "target" -> Str("button-1"),
      "value"  -> Str("42")
    )
    val result = parseClientMessage(json)

    assertEquals(
      result,
      Some(Event("increment", "button-1", Some(Str("42"))))
    )
  }

  test("parse Event message without value") {
    val json   = Obj(
      "type"   -> Str("event"),
      "event"  -> Str("click"),
      "target" -> Str("btn")
    )
    val result = parseClientMessage(json)

    assert(result.isDefined)
    result match {
      case Some(Event(event, target, value)) =>
        assertEquals(event, "click")
        assertEquals(target, "btn")
      // value will be present but might be JsonNull or similar
      case _                                 => fail("Expected Event message")
    }
  }

  test("parse Event message returns None on missing event field") {
    val json   = Obj(
      "type"   -> Str("event"),
      "target" -> Str("btn")
    )
    val result = parseClientMessage(json)

    assertEquals(result, None)
  }

  test("parse Event message returns None on missing target field") {
    val json   = Obj(
      "type"  -> Str("event"),
      "event" -> Str("click")
    )
    val result = parseClientMessage(json)

    assertEquals(result, None)
  }

  test("parse Heartbeat message (ping)") {
    val json   = Obj("type" -> Str("ping"))
    val result = parseClientMessage(json)

    assertEquals(result, Some(Heartbeat))
  }

  test("parse Heartbeat message (heartbeat)") {
    val json   = Obj("type" -> Str("heartbeat"))
    val result = parseClientMessage(json)

    assertEquals(result, Some(Heartbeat))
  }

  test("parse unknown message type returns None") {
    val json   = Obj("type" -> Str("unknown"))
    val result = parseClientMessage(json)

    assertEquals(result, None)
  }

  test("parse message with missing type field returns None") {
    val json   = Obj("data" -> Str("value"))
    val result = parseClientMessage(json)

    assertEquals(result, None)
  }

  test("parseClientMessage from string") {
    val jsonStr = """{"type":"ready"}"""
    val result  = parseClientMessage(jsonStr)

    assertEquals(result, Some(ClientReady))
  }

  test("parseClientMessage from invalid JSON string returns None") {
    val jsonStr = "{invalid json"
    val result  = parseClientMessage(jsonStr)

    assertEquals(result, None)
  }

  // === Server Messages ===

  test("ReplaceHtml toJson with default target") {
    val msg  = ReplaceHtml("<div>Content</div>")
    val json = msg.toJson

    assertEquals(json.obj.get("type").flatMap(_.strOpt), Some("replace"))
    assertEquals(
      json.obj.get("html").flatMap(_.strOpt),
      Some("<div>Content</div>")
    )
    assertEquals(json.obj.get("target").flatMap(_.strOpt), Some("root"))
  }

  test("ReplaceHtml toJson with custom target") {
    val msg  = ReplaceHtml("<span>Text</span>", target = "sidebar")
    val json = msg.toJson

    assertEquals(json.obj.get("type").flatMap(_.strOpt), Some("replace"))
    assertEquals(
      json.obj.get("html").flatMap(_.strOpt),
      Some("<span>Text</span>")
    )
    assertEquals(json.obj.get("target").flatMap(_.strOpt), Some("sidebar"))
  }

  test("PatchHtml toJson") {
    val msg  = PatchHtml("<p>Updated</p>", "content")
    val json = msg.toJson

    assertEquals(json.obj.get("type").flatMap(_.strOpt), Some("patch"))
    assertEquals(json.obj.get("html").flatMap(_.strOpt), Some("<p>Updated</p>"))
    assertEquals(json.obj.get("target").flatMap(_.strOpt), Some("content"))
  }

  test("HeartbeatResponse toJson") {
    val json = HeartbeatResponse.toJson

    assertEquals(json.obj.get("type").flatMap(_.strOpt), Some("pong"))
  }

  test("Error toJson") {
    val msg  = Error("Something went wrong")
    val json = msg.toJson

    assertEquals(json.obj.get("type").flatMap(_.strOpt), Some("error"))
    assertEquals(
      json.obj.get("message").flatMap(_.strOpt),
      Some("Something went wrong")
    )
  }

  test("ReplaceHtml with HTML special characters") {
    val msg  = ReplaceHtml("""<div class="test">Content & "quotes"</div>""")
    val json = msg.toJson

    val htmlField = json.obj.get("html").flatMap(_.strOpt)
    assert(htmlField.isDefined)
    assert(htmlField.get.contains("&"))
    assert(htmlField.get.contains("\""))
  }

  test("Error message with special characters") {
    val msg  = Error("""Error: <script>alert("xss")</script>""")
    val json = msg.toJson

    val messageField = json.obj.get("message").flatMap(_.strOpt)
    assert(messageField.isDefined)
    // The message should be preserved as-is in JSON
    assert(messageField.get.contains("<script>"))
  }

  // === Round-trip Tests ===

  test("ClientReady round-trip") {
    val original = ClientReady
    val json     = Obj("type" -> Str("ready"))
    val parsed   = parseClientMessage(json)

    assertEquals(parsed, Some(original))
  }

  test("Event round-trip") {
    val original = Event("click", "button-1", Some(Str("data")))
    val json     = Obj(
      "type"   -> Str("event"),
      "event"  -> Str("click"),
      "target" -> Str("button-1"),
      "value"  -> Str("data")
    )
    val parsed   = parseClientMessage(json)

    assertEquals(parsed, Some(original))
  }

  test("Heartbeat round-trip") {
    val original = Heartbeat
    val json     = Obj("type" -> Str("ping"))
    val parsed   = parseClientMessage(json)

    assertEquals(parsed, Some(original))
  }

  // === Edge Cases ===

  test("Event with complex JSON value") {
    val json   = Obj(
      "type"   -> Str("event"),
      "event"  -> Str("submit"),
      "target" -> Str("form"),
      "value"  -> Obj(
        "username" -> Str("alice"),
        "password" -> Str("secret")
      )
    )
    val result = parseClientMessage(json)

    assert(result.isDefined)
    result match {
      case Some(Event(event, target, Some(value: ujson.Obj))) =>
        assertEquals(event, "submit")
        assertEquals(target, "form")
        assertEquals(value.obj.get("username").flatMap(_.strOpt), Some("alice"))
      case _                                                  =>
        fail("Expected Event with JsonObject value")
    }
  }

  test("ReplaceHtml with empty HTML") {
    val msg  = ReplaceHtml("")
    val json = msg.toJson

    assertEquals(json.obj.get("html").flatMap(_.strOpt), Some(""))
  }

  test("PatchHtml with empty target throws or handles gracefully") {
    val msg  = PatchHtml("<p>Content</p>", "")
    val json = msg.toJson

    assertEquals(json.obj.get("target").flatMap(_.strOpt), Some(""))
  }
}
