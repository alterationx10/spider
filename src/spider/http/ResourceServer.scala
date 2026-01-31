package spider.http

import scala.io.Source
import scala.util.{Try, Using}

/** Helper for serving static files from the classpath.
  *
  * This is useful for serving JavaScript, CSS, images, and other static assets
  * bundled in your resources directory.
  *
  * Example:
  * {{{
  * object MyApp extends cask.MainRoutes {
  *   // Serve files from resources/static/
  *   @cask.get("/static", subpath = true)
  *   def staticFiles(request: cask.Request) =
  *     ResourceServer.serve("static", request.remainingPathSegments.mkString("/"))
  *
  *   initialize()
  * }
  * }}}
  */
object ResourceServer {

  /** Serve a resource file as a Cask Response.
    *
    * @param resourceBasePath
    *   The base path in the resources directory (e.g., "static")
    * @param path
    *   The path within the base directory
    * @return
    *   A Cask Response with the file contents
    */
  def serve(resourceBasePath: String, path: String): cask.Response[Array[Byte]] = {
    // Build resource path (remove leading slash)
    val cleanPath    = path.stripPrefix("/")
    val resourcePath = s"$resourceBasePath/$cleanPath".stripPrefix("/")

    // Try to load the resource
    Try {
      val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
      if (stream == null) {
        throw new RuntimeException(s"Resource not found: $resourcePath")
      }
      stream
    }.flatMap { stream =>
      Try {
        val bytes = stream.readAllBytes()
        stream.close()
        bytes
      }
    } match {
      case scala.util.Success(content) =>
        // Determine content type from file extension
        val contentType = getContentType(path)
        cask.Response(
          content,
          headers = Seq("Content-Type" -> contentType)
        )

      case scala.util.Failure(_) =>
        cask.Response(
          s"Resource not found: $path".getBytes("UTF-8"),
          statusCode = 404,
          headers = Seq("Content-Type" -> "text/plain; charset=utf-8")
        )
    }
  }

  /** Serve a resource file as a string (for text files).
    *
    * @param resourceBasePath
    *   The base path in the resources directory
    * @param path
    *   The path within the base directory
    * @return
    *   A Cask Response with the file contents as a string
    */
  def serveText(
      resourceBasePath: String,
      path: String
  ): cask.Response[String] = {
    val cleanPath    = path.stripPrefix("/")
    val resourcePath = s"$resourceBasePath/$cleanPath".stripPrefix("/")

    Try {
      val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
      if (stream == null) {
        throw new RuntimeException(s"Resource not found: $resourcePath")
      }
      stream
    }.flatMap { stream =>
      Using(Source.fromInputStream(stream, "UTF-8")) { source =>
        source.mkString
      }
    } match {
      case scala.util.Success(content) =>
        val contentType = getContentType(path)
        cask.Response(
          content,
          headers = Seq("Content-Type" -> contentType)
        )

      case scala.util.Failure(_) =>
        cask.Response(
          s"Resource not found: $path",
          statusCode = 404,
          headers = Seq("Content-Type" -> "text/plain; charset=utf-8")
        )
    }
  }

  /** Get the content type based on file extension.
    *
    * @param path
    *   The file path
    * @return
    *   The content type
    */
  private def getContentType(path: String): String = {
    val extension = path.split("\\.").lastOption.map(_.toLowerCase)
    extension match {
      case Some("html")               => "text/html; charset=utf-8"
      case Some("css")                => "text/css; charset=utf-8"
      case Some("js")                 => "application/javascript; charset=utf-8"
      case Some("json")               => "application/json; charset=utf-8"
      case Some("png")                => "image/png"
      case Some("jpg") | Some("jpeg") => "image/jpeg"
      case Some("gif")                => "image/gif"
      case Some("svg")                => "image/svg+xml"
      case Some("ico")                => "image/x-icon"
      case Some("woff")               => "font/woff"
      case Some("woff2")              => "font/woff2"
      case Some("ttf")                => "font/ttf"
      case Some("eot")                => "application/vnd.ms-fontobject"
      case _                          => "application/octet-stream"
    }
  }
}
