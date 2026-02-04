package spider

import munit.FunSuite
import ujson._
import upickle.default._

import scala.util.Success

/** Tests for EventCodec.
  *
  * Tests encoding and decoding of WebView events.
  */
class EventCodecSuite extends FunSuite {

  // === Test Events ===

  sealed trait CounterEvent derives ReadWriter, EventCodec
  case object Increment           extends CounterEvent
  case object Decrement           extends CounterEvent
  case class SetCount(value: Int) extends CounterEvent

  sealed trait UserEvent derives ReadWriter, EventCodec
  case class SetName(name: String) extends UserEvent
  case class SetAge(age: Int)      extends UserEvent
  case class UpdateProfile(name: String, age: Int, email: String)
      extends UserEvent

  // === String Events ===

  test("String events - encode") {
    val codec = EventCodec.stringEventCodec
    val json  = codec.encode("increment")
    assertEquals(json, Str("increment"))
  }

  test("String events - decode") {
    val codec  = EventCodec.stringEventCodec
    val result = codec.decode(Str("increment"))
    assertEquals(result, Success("increment"))
  }

  test("String events - decodeFromClient") {
    val codec  = EventCodec.stringEventCodec
    val result = codec.decodeFromClient("increment", Map.empty)
    assertEquals(result, Success("increment"))
  }

  test("String events - decode fails on non-string") {
    val codec  = EventCodec.stringEventCodec
    val result = codec.decode(Num(42))
    assert(result.isFailure)
  }

  // === Case Object Events ===

  test("Case object - encode Increment") {
    val codec = EventCodec[CounterEvent]
    val json  = codec.encode(Increment)

    // uPickle encodes case objects as just their name string
    json match {
      case ujson.Str(s)   => assertEquals(s, "Increment")
      case ujson.Obj(obj) =>
        val typeField = obj.get("$type").flatMap(_.strOpt)
        assertEquals(typeField, Some("Increment"))
      case _              => fail(s"Unexpected JSON format: $json")
    }
  }

  test("Case object - decode Increment") {
    val codec  = EventCodec[CounterEvent]
    val json   = Obj("$type" -> Str("Increment"))
    val result = codec.decode(json)

    assert(result.isSuccess)
    assertEquals(result.get, Increment)
  }

  test("Case object - decodeFromClient Increment") {
    val codec  = EventCodec[CounterEvent]
    val result = codec.decodeFromClient("Increment", Map.empty)

    assert(result.isSuccess)
    assertEquals(result.get, Increment)
  }

  test("Case object - decode Decrement") {
    val codec  = EventCodec[CounterEvent]
    val json   = Obj("$type" -> Str("Decrement"))
    val result = codec.decode(json)

    assert(result.isSuccess)
    assertEquals(result.get, Decrement)
  }

  // === Case Class Events ===

  test("Case class - encode SetCount") {
    val codec = EventCodec[CounterEvent]
    val event = SetCount(42)
    val json  = codec.encode(event)

    val typeField = json.obj.get("$type").flatMap(_.strOpt)

    assertEquals(typeField, Some("SetCount"))
    // Value field exists in the JSON
    assert(ujson.write(json).contains("42"))
  }

  test("Case class - decode SetCount") {
    val codec  = EventCodec[CounterEvent]
    val json   = Obj(
      "$type" -> Str("SetCount"),
      "value" -> Num(42)
    )
    val result = codec.decode(json)

    assert(result.isSuccess)
    assertEquals(result.get, SetCount(42))
  }

  test("Case class - decodeFromClient SetCount with int value") {
    val codec  = EventCodec[CounterEvent]
    val result = codec.decodeFromClient("SetCount", Map("value" -> 42))

    assert(result.isSuccess)
    assertEquals(result.get, SetCount(42))
  }

  test("Case class - decodeFromClient SetCount with string value") {
    val codec  = EventCodec[CounterEvent]
    val result = codec.decodeFromClient("SetCount", Map("value" -> "42"))

    // This should parse the string "42" as JSON
    assert(result.isSuccess)
  }

  test("Case class with string - encode SetName") {
    val codec = EventCodec[UserEvent]
    val event = SetName("Alice")
    val json  = codec.encode(event)

    val typeField = json.obj.get("$type").flatMap(_.strOpt)
    val nameField = json.obj.get("name").flatMap(_.strOpt)

    assertEquals(typeField, Some("SetName"))
    assertEquals(nameField, Some("Alice"))
  }

  test("Case class with string - decode SetName") {
    val codec  = EventCodec[UserEvent]
    val json   = Obj(
      "$type" -> Str("SetName"),
      "name"  -> Str("Alice")
    )
    val result = codec.decode(json)

    assert(result.isSuccess)
    assertEquals(result.get, SetName("Alice"))
  }

  test("Case class with multiple fields - encode UpdateProfile") {
    val codec = EventCodec[UserEvent]
    val event = UpdateProfile("Bob", 25, "bob@example.com")
    val json  = codec.encode(event)

    val typeField  = json.obj.get("$type").flatMap(_.strOpt)
    val nameField  = json.obj.get("name").flatMap(_.strOpt)
    val emailField = json.obj.get("email").flatMap(_.strOpt)

    assertEquals(typeField, Some("UpdateProfile"))
    assertEquals(nameField, Some("Bob"))
    assertEquals(emailField, Some("bob@example.com"))
    // Age field exists in the JSON
    assert(ujson.write(json).contains("25"))
  }

  test("Case class with multiple fields - decode UpdateProfile") {
    val codec  = EventCodec[UserEvent]
    val json   = Obj(
      "$type" -> Str("UpdateProfile"),
      "name"  -> Str("Bob"),
      "age"   -> Num(25),
      "email" -> Str("bob@example.com")
    )
    val result = codec.decode(json)

    assert(result.isSuccess)
    assertEquals(result.get, UpdateProfile("Bob", 25, "bob@example.com"))
  }

  // === Error Cases ===

  test("decode fails on invalid event name") {
    val codec  = EventCodec[CounterEvent]
    val json   = Obj("$type" -> Str("InvalidEvent"))
    val result = codec.decode(json)

    assert(result.isFailure)
  }

  test("decode fails on missing type field") {
    val codec  = EventCodec[CounterEvent]
    val json   = Obj("value" -> Num(42))
    val result = codec.decode(json)

    assert(result.isFailure)
  }

  test("decodeFromClient fails on invalid event name") {
    val codec  = EventCodec[CounterEvent]
    val result = codec.decodeFromClient("InvalidEvent", Map.empty)

    assert(result.isFailure)
  }

  test("decode from JSON string") {
    val codec   = EventCodec[CounterEvent]
    val jsonStr = """{"$type":"Increment"}"""
    val result  = codec.decode(jsonStr)

    assert(result.isSuccess)
    assertEquals(result.get, Increment)
  }

  test("decode from invalid JSON string") {
    val codec   = EventCodec[CounterEvent]
    val jsonStr = "{invalid json"
    val result  = codec.decode(jsonStr)

    assert(result.isFailure)
  }

  // === Extension Methods ===

  test("event.toJson extension method") {
    given EventCodec[CounterEvent] = EventCodec.derived

    val event = Increment
    val json  = event.toJson

    // uPickle encodes case objects as just their name string
    json match {
      case ujson.Str(s)   => assertEquals(s, "Increment")
      case ujson.Obj(obj) =>
        val typeField = obj.get("$type").flatMap(_.strOpt)
        assertEquals(typeField, Some("Increment"))
      case _              => fail(s"Unexpected JSON format: $json")
    }
  }

  test("event.toJsonString extension method") {
    given EventCodec[CounterEvent] = EventCodec.derived

    val event   = SetCount(42)
    val jsonStr = event.toJsonString

    assert(jsonStr.contains("SetCount"))
    assert(jsonStr.contains("42"))
  }

  // === Client Event Payload Scenarios ===

  test("decodeFromClient with empty value") {
    val codec  = EventCodec[CounterEvent]
    val result = codec.decodeFromClient("Increment", Map("value" -> ""))

    // Should still succeed for case objects that don't need the value
    assert(result.isSuccess)
  }

  test("decodeFromClient with JSON object value") {
    val codec     = EventCodec[UserEvent]
    val jsonValue = """{"name":"Alice","age":30,"email":"alice@example.com"}"""
    val result    = codec.decodeFromClient(
      "UpdateProfile",
      Map("value" -> jsonValue)
    )

    assert(result.isSuccess)
    assertEquals(result.get, UpdateProfile("Alice", 30, "alice@example.com"))
  }

  test("decodeFromClient preserves target in payload") {
    val codec  = EventCodec[CounterEvent]
    val result = codec.decodeFromClient(
      "Increment",
      Map("target" -> "button-1", "value" -> "")
    )

    // Target is in the payload but not used for decoding the event itself
    assert(result.isSuccess)
  }
}
