package spider

import ujson.Value

/** Messages that a WebView actor can receive. */
sealed trait WebViewMessage

/** Lifecycle Messages */

/** Initialize the WebView with connection parameters.
  *
  * @param params
  *   URL query parameters
  * @param session
  *   Session data
  * @param channel
  *   The Cask WebSocket channel for sending messages
  * @param devToolsActorName
  *   Optional actor name for DevTools integration
  */
case class Mount(
    params: Map[String, String],
    session: Session,
    channel: cask.WsChannelActor,
    devToolsActorName: Option[String] = None
) extends WebViewMessage

/** Event from the client.
  *
  * This message contains a strongly-typed event that has been decoded from the
  * client's JSON payload using an EventCodec.
  *
  * @param event
  *   The typed event (e.g., Increment, SetName("Alice"))
  * @tparam Event
  *   The event type
  */
case class ClientEvent[Event](event: Event) extends WebViewMessage

/** Message from the actor system (pub/sub, timers, etc.).
  *
  * @param msg
  *   The message payload
  */
case class InfoMessage(msg: Any) extends WebViewMessage

/** Client disconnected. */
case object ClientDisconnected extends WebViewMessage

/** Protocol messages exchanged between client and server over WebSocket. */
object WebViewProtocol {

  /** Messages sent from client to server */
  sealed trait ClientMessage

  /** Client is ready and requesting initial render */
  case object ClientReady extends ClientMessage

  /** Client triggered an event */
  case class Event(event: String, target: String, value: Option[Any])
      extends ClientMessage

  /** Client sent a heartbeat/ping */
  case object Heartbeat extends ClientMessage

  /** Messages sent from server to client */
  sealed trait ServerMessage {
    def toJson: Value
  }

  /** Replace entire HTML content */
  case class ReplaceHtml(html: String, target: String = "root")
      extends ServerMessage {
    def toJson: Value = ujson.Obj(
      "type"   -> "replace",
      "html"   -> html,
      "target" -> target
    )
  }

  /** Patch specific element */
  case class PatchHtml(html: String, target: String) extends ServerMessage {
    def toJson: Value = ujson.Obj(
      "type"   -> "patch",
      "html"   -> html,
      "target" -> target
    )
  }

  /** Send heartbeat response */
  case object HeartbeatResponse extends ServerMessage {
    def toJson: Value = ujson.Obj(
      "type" -> "pong"
    )
  }

  /** Error message */
  case class Error(message: String) extends ServerMessage {
    def toJson: Value = ujson.Obj(
      "type"    -> "error",
      "message" -> message
    )
  }

  /** Parse a client message from JSON */
  def parseClientMessage(json: Value): Option[ClientMessage] =
    json.obj.get("type").flatMap(_.strOpt) match {
      case Some("ready")     => Some(ClientReady)
      case Some("event")     =>
        for {
          eventJson <- json.obj.get("event")
          target    <- json.obj.get("target").flatMap(_.strOpt)
        } yield {
          val value = json.obj.get("value")
          // Handle both string and JSON object events
          val event = eventJson match {
            case ujson.Str(str)  => str
            case jobj: ujson.Obj => ujson.write(jobj) // Pre-encoded event as object
            case j: Value        => ujson.write(j)    // Any other JSON type
          }
          Event(event, target, value)
        }
      case Some("ping")      => Some(Heartbeat)
      case Some("heartbeat") => Some(Heartbeat)
      case _                 => None
    }

  /** Parse a client message from JSON string */
  def parseClientMessage(jsonStr: String): Option[ClientMessage] =
    scala.util.Try(ujson.read(jsonStr)).toOption.flatMap(parseClientMessage)
}
