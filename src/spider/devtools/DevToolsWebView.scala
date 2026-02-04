package spider.devtools

import spider.{EventCodec, Session, WebView}
import spider.*
import scalatags.Text.all.*
import spider.html.WebViewAttributes.*

import java.time.format.DateTimeFormatter
import scala.annotation.nowarn

/** DevTools WebView - provides a UI for monitoring and debugging WebViews.
  *
  * Features:
  *   - State timeline viewer
  *   - Event log
  *   - Performance metrics
  *   - Connection status
  */

/** State for the DevTools UI. */
case class DevToolsUIState(
    devToolsStates: Map[String, DevToolsState] = Map.empty,
    selectedView: Option[String] = None,
    filter: TimelineFilter = TimelineFilter.All
)

/** Filter for timeline entries. */
enum TimelineFilter {
  case All
  case OnlyEvents
  case OnlyStateChanges
  case OnlyInfo
}

object TimelineFilter {

  given EventCodec[TimelineFilter] = EventCodec.from[TimelineFilter](
    encodeFunc = filter =>
      ujson.Str(filter match {
        case TimelineFilter.All              => "all"
        case TimelineFilter.OnlyEvents       => "events"
        case TimelineFilter.OnlyStateChanges => "state"
        case TimelineFilter.OnlyInfo         => "info"
      }),
    decodeFunc = json =>
      scala.util.Try {
        json match {
          case ujson.Str(s) =>
            s match {
              case "all"    => TimelineFilter.All
              case "events" => TimelineFilter.OnlyEvents
              case "state"  => TimelineFilter.OnlyStateChanges
              case "info"   => TimelineFilter.OnlyInfo
              case _        => throw new RuntimeException(s"Unknown filter: $s")
            }
          case _            => throw new RuntimeException(s"Expected string, got: $json")
        }
      },
    decodeClientFunc = (eventName, _) =>
      scala.util.Try {
        eventName match {
          case "all"    => TimelineFilter.All
          case "events" => TimelineFilter.OnlyEvents
          case "state"  => TimelineFilter.OnlyStateChanges
          case "info"   => TimelineFilter.OnlyInfo
          case _        => throw new RuntimeException(s"Unknown filter: $eventName")
        }
      }
  )
}

/** Events for the DevTools UI. */
sealed trait DevToolsEvent
case class SelectView(viewId: String)        extends DevToolsEvent
case class SetFilter(filter: TimelineFilter) extends DevToolsEvent
case object ClearTimeline                    extends DevToolsEvent
case object ClearDisconnected                extends DevToolsEvent
case object Refresh                          extends DevToolsEvent

object DevToolsEvent {
  given EventCodec[DevToolsEvent] = EventCodec.from[DevToolsEvent](
    encodeFunc = event => {
      event match {
        case SelectView(viewId) =>
          ujson.Obj(
            "type"   -> "SelectView",
            "viewId" -> viewId
          )
        case SetFilter(filter)  =>
          ujson.Obj(
            "type"   -> "SetFilter",
            "filter" -> filter.toString
          )
        case ClearTimeline      =>
          ujson.Obj("type" -> "ClearTimeline")
        case ClearDisconnected  =>
          ujson.Obj("type" -> "ClearDisconnected")
        case Refresh            => ujson.Obj("type" -> "Refresh")
      }
    },
    decodeFunc = _ =>
      scala.util.Try {
        throw new RuntimeException("Server-side decode not implemented")
      },
    decodeClientFunc = (eventName, _) =>
      scala.util.Try {
        // Handle event names like "select-view-abc123", "set-filter-all", "clear-timeline"
        if (eventName.startsWith("select-view-")) {
          val viewId = eventName.stripPrefix("select-view-")
          SelectView(viewId)
        } else if (eventName.startsWith("set-filter-")) {
          val filterType = eventName.stripPrefix("set-filter-")
          filterType match {
            case "all"    => SetFilter(TimelineFilter.All)
            case "events" => SetFilter(TimelineFilter.OnlyEvents)
            case "state"  => SetFilter(TimelineFilter.OnlyStateChanges)
            case "info"   => SetFilter(TimelineFilter.OnlyInfo)
            case _        =>
              throw new RuntimeException(s"Unknown filter type: $filterType")
          }
        } else if (eventName == "clear-timeline") {
          ClearTimeline
        } else if (eventName == "clear-disconnected") {
          ClearDisconnected
        } else if (eventName == "refresh") {
          Refresh
        } else {
          throw new RuntimeException(s"Unknown DevTools event: $eventName")
        }
      }
  )
}

/** DevTools WebView implementation. */
class DevToolsWebView extends WebView[DevToolsUIState, DevToolsEvent] {

  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

  override def mount(
      params: Map[String, String],
      session: Session
  ): DevToolsUIState = {
    DevToolsUIState()
  }

  override def handleEvent(
      event: DevToolsEvent,
      state: DevToolsUIState
  ): DevToolsUIState = {
    event match {
      case SelectView(viewId) =>
        state.copy(selectedView = Some(viewId))

      case SetFilter(filter) =>
        state.copy(filter = filter)

      case ClearTimeline =>
        state.selectedView match {
          case Some(viewId) =>
            state.copy(
              devToolsStates = state.devToolsStates.updatedWith(viewId) {
                case Some(devToolsState) =>
                  Some(devToolsState.copy(timeline = List.empty))
                case None                => None
              }
            )
          case None         => state
        }

      case ClearDisconnected =>
        // Remove all disconnected WebViews
        val connectedStates = state.devToolsStates.filter {
          case (_, devToolsState) =>
            devToolsState.connectionStatus == ConnectionStatus.Connected
        }
        // Update selected view if current selection was removed
        val newSelectedView =
          state.selectedView.filter(connectedStates.contains)
        state.copy(
          devToolsStates = connectedStates,
          selectedView = newSelectedView
        )

      case Refresh =>
        // In a real implementation, this would fetch latest state from actors
        state
    }
  }

  override def handleInfo(msg: Any, state: DevToolsUIState): DevToolsUIState = {
    msg match {
      case UpdateDevToolsState(viewId, devToolsState) =>
        state.copy(
          devToolsStates = state.devToolsStates + (viewId -> devToolsState),
          selectedView = state.selectedView.orElse(Some(viewId))
        )
      case _                                          => state
    }
  }

  override def render(state: DevToolsUIState): String = {
    div(cls := "devtools", style := devToolsStyle)(
      // Header
      div(cls := "devtools-header", style := headerStyle)(
        h1(style := "margin: 0; color: #667eea; font-size: 1.5rem;")(
          "Branch WebView DevTools"
        ),
        div(style := "font-size: 0.875rem; color: #718096;")(
          s"Monitoring ${state.devToolsStates.size} WebView(s)"
        )
      ),
      // Main content
      div(
        cls   := "devtools-content",
        style := "display: flex; height: calc(100vh - 80px);"
      )(
        // Sidebar: List of WebViews
        renderSidebar(state),
        // Main panel: Timeline and metrics
        state.selectedView match {
          case Some(viewId) =>
            state.devToolsStates.get(viewId) match {
              case Some(devToolsState) =>
                renderMainPanel(viewId, devToolsState, state.filter)
              case None                => renderEmptyState("WebView not found")
            }
          case None         =>
            renderEmptyState("Select a WebView from the sidebar")
        }
      )
    ).render
  }

  private def renderSidebar(state: DevToolsUIState): Frag = {
    val hasDisconnected = state.devToolsStates.exists {
      case (_, devToolsState) =>
        devToolsState.connectionStatus != ConnectionStatus.Connected
    }

    div(cls := "devtools-sidebar", style := sidebarStyle)(
      div(
        style := "display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;"
      )(
        h2(style := "margin: 0; font-size: 1rem; color: #4a5568;")(
          "Active WebViews"
        ),
        if (hasDisconnected) {
          button(
            wvClick := "clear-disconnected",
            style   := "padding: 6px 12px; background: #f56565; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 0.75rem;"
          )("Clear")
        } else {
          div()
        }
      ),
      if (state.devToolsStates.isEmpty) {
        div(style := "color: #a0aec0; font-size: 0.875rem;")(
          "No WebViews connected"
        )
      } else {
        div()(
          state.devToolsStates.toSeq.map { case (viewId, devToolsState) =>
            val isSelected = state.selectedView.contains(viewId)
            div(
              cls     := "webview-item",
              style   := s"padding: 12px; margin-bottom: 8px; border-radius: 8px; cursor: pointer; background: ${
                  if (isSelected) "#667eea" else "#f7fafc"
                }; color: ${if (isSelected) "white" else "#2d3748"};",
              wvClick := s"select-view-$viewId"
            )(
              div(style := "font-weight: bold; margin-bottom: 4px;")(
                devToolsState.componentType
              ),
              div(
                style := "font-size: 0.75rem; opacity: 0.8; margin-bottom: 2px;"
              )(
                viewId.take(12) + "..."
              ),
              div(style := "font-size: 0.75rem; opacity: 0.8;")(
                s"${devToolsState.timeline.size} events | ${devToolsState.connectionStatus}"
              )
            )
          }
        )
      }
    )
  }

  @nowarn
  private def renderMainPanel(
      viewId: String,
      devToolsState: DevToolsState,
      filter: TimelineFilter
  ): Frag = {
    div(cls := "devtools-main", style := mainPanelStyle)(
      // Metrics panel
      renderMetrics(devToolsState.metrics),
      // Connection status
      renderConnectionStatus(devToolsState.connectionStatus),
      // Filter controls
      div(style := "margin: 20px 0; display: flex; gap: 10px;")(
        button(
          wvClick := "set-filter-all",
          style   := filterButtonStyle(filter == TimelineFilter.All)
        )("All"),
        button(
          wvClick := "set-filter-events",
          style   := filterButtonStyle(filter == TimelineFilter.OnlyEvents)
        )("Events"),
        button(
          wvClick := "set-filter-state",
          style   := filterButtonStyle(filter == TimelineFilter.OnlyStateChanges)
        )("State"),
        button(
          wvClick := "clear-timeline",
          style   := "padding: 8px 16px; background: #f56565; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 0.875rem;"
        )("Clear")
      ),
      // Timeline
      h2(style := "margin: 20px 0 10px 0; font-size: 1.25rem; color: #2d3748;")(
        "Timeline"
      ),
      renderTimeline(devToolsState.recentTimeline(), filter)
    )
  }

  private def renderMetrics(metrics: PerformanceMetrics): Frag = {
    div(
      cls   := "metrics",
      style := "display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; margin-bottom: 20px;"
    )(
      renderMetricCard("Total Events", metrics.totalEvents.toString, "#667eea"),
      renderMetricCard(
        "Avg Render",
        f"${metrics.avgRenderTimeMs}%.2f ms",
        "#48bb78"
      ),
      renderMetricCard(
        "Min Render",
        s"${metrics.displayMinRenderTimeMs} ms",
        "#38b2ac"
      ),
      renderMetricCard(
        "Max Render",
        s"${metrics.maxRenderTimeMs} ms",
        "#ed8936"
      )
    )
  }

  private def renderMetricCard(
      labelText: String,
      valueText: String,
      color: String
  ): Frag = {
    div(
      style := s"background: white; padding: 15px; border-radius: 8px; border-left: 4px solid $color; box-shadow: 0 1px 3px rgba(0,0,0,0.1);"
    )(
      div(style := "font-size: 0.75rem; color: #718096; margin-bottom: 8px;")(
        labelText
      ),
      div(style := s"font-size: 1.5rem; font-weight: bold; color: $color;")(
        valueText
      )
    )
  }

  private def renderConnectionStatus(status: ConnectionStatus): Frag = {
    val (color, text) = status match {
      case ConnectionStatus.Connected    => ("#48bb78", "Connected")
      case ConnectionStatus.Disconnected => ("#a0aec0", "Disconnected")
      case ConnectionStatus.Error(msg)   => ("#f56565", s"Error: $msg")
    }

    div(
      style := s"padding: 12px; background: $color; color: white; border-radius: 8px; margin-bottom: 20px;"
    )(
      s"WebSocket Status: $text"
    )
  }

  private def renderTimeline(
      entries: List[TimelineEntry],
      filter: TimelineFilter
  ): Frag = {
    val filteredEntries = filter match {
      case TimelineFilter.All              => entries
      case TimelineFilter.OnlyEvents       => entries.filter(_.eventType == "Event")
      case TimelineFilter.OnlyStateChanges =>
        entries.filter(_.eventType == "Mount")
      case TimelineFilter.OnlyInfo         => entries.filter(_.eventType == "Info")
    }

    if (filteredEntries.isEmpty) {
      div(style := "padding: 40px; text-align: center; color: #a0aec0;")(
        "No timeline entries"
      )
    } else {
      div(cls := "timeline", style := "max-height: 500px; overflow-y: auto;")(
        filteredEntries.reverse.map(renderTimelineEntry)
      )
    }
  }

  private def renderTimelineEntry(entry: TimelineEntry): Frag = {
    val color = entry.eventType match {
      case "Mount"      => "#667eea"
      case "Event"      => "#48bb78"
      case "Info"       => "#38b2ac"
      case "Disconnect" => "#f56565"
      case _            => "#a0aec0"
    }

    div(
      style := s"background: white; padding: 15px; margin-bottom: 10px; border-radius: 8px; border-left: 4px solid $color; box-shadow: 0 1px 3px rgba(0,0,0,0.1);"
    )(
      div(
        style := "display: flex; justify-content: space-between; margin-bottom: 8px;"
      )(
        div(style := s"font-weight: bold; color: $color;")(
          entry.eventType
        ),
        div(style := "font-size: 0.75rem; color: #718096;")(
          entry.timestamp
            .atZone(java.time.ZoneId.systemDefault())
            .format(timeFormatter)
        )
      ),
      div(
        style := "font-size: 0.875rem; color: #4a5568; font-family: monospace; white-space: pre-wrap;"
      )(
        entry.data.map { case (k, v) => s"$k: $v" }.mkString("\n")
      )
    )
  }

  private def renderEmptyState(message: String): Frag = {
    div(
      style := "flex: 1; display: flex; align-items: center; justify-content: center; color: #a0aec0; font-size: 1.25rem;"
    )(
      message
    )
  }

  // Styles
  private val devToolsStyle  =
    "font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #edf2f7; height: 100vh; margin: 0; padding: 0;"
  private val headerStyle    =
    "background: white; padding: 20px; border-bottom: 2px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.1);"
  private val sidebarStyle   =
    "width: 300px; background: white; border-right: 2px solid #e2e8f0; padding: 20px; overflow-y: auto;"
  private val mainPanelStyle = "flex: 1; padding: 20px; overflow-y: auto;"

  private def filterButtonStyle(isActive: Boolean): String = {
    val bg    = if (isActive) "#667eea" else "#e2e8f0"
    val color = if (isActive) "white" else "#2d3748"
    s"padding: 8px 16px; background: $bg; color: $color; border: none; border-radius: 6px; cursor: pointer; font-size: 0.875rem;"
  }
}

/** Message to update DevTools state from WebViewActor. */
case class UpdateDevToolsState(viewId: String, devToolsState: DevToolsState)
