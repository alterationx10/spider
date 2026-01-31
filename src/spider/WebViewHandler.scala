package spider

import cask.util.Logger.Console.globalLogger
import castor.Context.Simple.global
import keanu.actors.ActorSystem

import java.util.UUID

/** Creates a Cask WebSocket handler for WebView connections.
  *
  * This handler creates a WebViewActor for each connected client and routes
  * messages between the WebSocket and the actor. It uses an EventCodec to
  * decode client events into strongly-typed Event instances.
  *
  * Example usage:
  * {{{
  * sealed trait MyEvent derives EventCodec
  * case object Click extends MyEvent
  *
  * val actorSystem = ActorSystem()
  * given EventCodec[MyEvent] = EventCodec.derived
  *
  * object MyApp extends cask.MainRoutes {
  *   @cask.websocket("/webview")
  *   def webViewWs(): cask.WebsocketResult = {
  *     WebViewHandler.createWsHandler(actorSystem, () => MyWebView())
  *   }
  *
  *   initialize()
  * }
  * }}}
  *
  * @param actorSystem
  *   The actor system to use for spawning actors
  * @param webViewFactory
  *   Factory function to create WebView instances
  * @param eventCodec
  *   Codec for encoding/decoding events
  * @param params
  *   URL query parameters to pass to mount
  * @param session
  *   Session data to pass to mount
  * @tparam State
  *   The state type of the WebView
  * @tparam Event
  *   The event type of the WebView
  */
object WebViewHandler {

  /** Create a Cask WebsocketResult for handling WebView connections.
    *
    * @param actorSystem
    *   The actor system to use
    * @param webViewFactory
    *   Factory function to create WebView instances
    * @param params
    *   URL query parameters
    * @param session
    *   Session data
    * @param devToolsActorName
    *   Optional DevTools actor name for integration
    * @param useExistingActor
    *   Optional existing actor name to reuse
    * @param eventCodec
    *   Codec for encoding/decoding events (provided implicitly)
    * @tparam State
    *   The WebView state type
    * @tparam Event
    *   The WebView event type
    * @return
    *   A Cask WebsocketResult
    */
  def createWsHandler[State, Event](
      actorSystem: ActorSystem,
      webViewFactory: () => WebView[State, Event],
      params: Map[String, String] = Map.empty,
      session: Session = Session(),
      devToolsActorName: Option[String] = None,
      useExistingActor: Option[String] = None
  )(using eventCodec: EventCodec[Event]): cask.WebsocketResult = {
    cask.WsHandler { channel =>
      // Generate unique actor name for this connection (or reuse existing)
      val actorName = useExistingActor match {
        case Some(existingActorName) =>
          // Check if actor already exists, create if not
          val webView = webViewFactory()

          try {
            // Try to create the actor - this will fail if it already exists
            actorSystem.registerProp(
              keanu.actors.ActorProps
                .props[WebViewActor[State, Event]](
                  Tuple1(webView)
                )
            )
            actorSystem.actorOf[WebViewActor[State, Event]](existingActorName)
            println(s"Created shared actor: $existingActorName")
          } catch {
            case _: Exception =>
              // Actor already exists, that's fine
              println(s"Reusing existing actor: $existingActorName")
          }
          existingActorName

        case None =>
          // Generate unique actor name for this connection
          val actorId = UUID.randomUUID().toString

          // Create the WebView instance
          val webView = webViewFactory()

          // Register actor props (for actor creation)
          actorSystem.registerProp(
            keanu.actors.ActorProps
              .props[WebViewActor[State, Event]](
                Tuple1(webView)
              )
          )

          // Spawn the actor
          val name = s"webview-$actorId"
          actorSystem.actorOf[WebViewActor[State, Event]](name)
          name
      }

      // Send mount message to initialize (includes channel and devTools actor)
      actorSystem.tell[WebViewActor[State, Event]](
        actorName,
        Mount(params, session, channel, devToolsActorName)
      )

      cask.WsActor {
        case cask.Ws.Text(message) =>
          handleMessage(
            actorSystem,
            actorName,
            channel,
            message,
            eventCodec
          )

        case cask.Ws.Close(_, _) =>
          actorSystem.tell[WebViewActor[State, Event]](
            actorName,
            ClientDisconnected
          )

        case cask.Ws.ChannelClosed() =>
          actorSystem.tell[WebViewActor[State, Event]](
            actorName,
            ClientDisconnected
          )

        case cask.Ws.Error(error) =>
          println(s"WebSocket error: ${error.getMessage}")
          error.printStackTrace()
          actorSystem.tell[WebViewActor[State, Event]](
            actorName,
            ClientDisconnected
          )
      }
    }
  }

  private def handleMessage[State, Event](
      actorSystem: ActorSystem,
      actorName: String,
      channel: cask.WsChannelActor,
      message: String,
      eventCodec: EventCodec[Event]
  ): Unit = {
    try {
      // Parse the message
      WebViewProtocol.parseClientMessage(message) match {
        case Some(WebViewProtocol.ClientReady) =>
          // Client is ready, mount already happened in onConnect
          ()

        case Some(WebViewProtocol.Event(eventName, target, value)) =>
          // EventCodec events are JSON. Try direct decode first.
          // For forms with dynamic data, fall back to decodeFromClient.
          val decodedEvent =
            eventCodec.decode(eventName).recoverWith { case _ =>
              val payload = Map(
                "target" -> target,
                "value"  -> value.orNull
              )
              eventCodec.decodeFromClient(eventName, payload)
            }

          decodedEvent match {
            case scala.util.Success(typedEvent) =>
              actorSystem.tell[WebViewActor[State, Event]](
                actorName,
                ClientEvent(typedEvent)
              )
            case scala.util.Failure(error)      =>
              println(
                s"[WebViewHandler] Failed to decode event '$eventName': ${error.getMessage}"
              )
              error.printStackTrace()
              // Send error to client
              val errorMsg = WebViewProtocol.Error(
                s"Failed to decode event '$eventName': ${error.getMessage}"
              )
              channel.send(cask.Ws.Text(ujson.write(errorMsg.toJson)))
          }

        case Some(WebViewProtocol.Heartbeat) =>
          // Send heartbeat response
          channel.send(
            cask.Ws.Text(ujson.write(WebViewProtocol.HeartbeatResponse.toJson))
          )

        case None =>
          println(s"Failed to parse client message: $message")
          val errorMsg = WebViewProtocol.Error("Failed to parse message")
          channel.send(cask.Ws.Text(ujson.write(errorMsg.toJson)))
      }
    } catch {
      case error: Throwable =>
        println(s"Error handling message: ${error.getMessage}")
        error.printStackTrace()
        // Try to send error to client before failing
        try {
          val errorMsg =
            WebViewProtocol.Error(s"Internal error: ${error.getMessage}")
          channel.send(cask.Ws.Text(ujson.write(errorMsg.toJson)))
        } catch {
          case _: Exception => // If we can't send, just log it
            println("Failed to send error message to client")
        }
    }
  }
}
