package caliban

import caliban.interop.tapir.TestData.sampleCharacters
import caliban.interop.tapir.{ TapirAdapterSpec, TestApi, TestService }
import caliban.uploads.Uploads
import sttp.client3.UriContext
import zio._
import zio.http._
import zio.test.{ Live, ZIOSpecDefault }

import scala.language.postfixOps

object QuickAdapterSpec extends ZIOSpecDefault {
  import caliban.quick._

  private val envLayer = TestService.make(sampleCharacters) ++ Uploads.empty

  private val auth = Middleware.intercept { case (req, resp) =>
    if (req.headers.get("X-Invalid").nonEmpty)
      Response(Status.Unauthorized, body = Body.fromString("You are unauthorized!"))
    else resp
  }

  private val apiLayer = envLayer >>> ZLayer.fromZIO {
    for {
      _       <- TestApi.api
                   .routes("/api/graphql", uploadPath = Some("/upload/graphql"), webSocketPath = Some("/ws/graphql"))
                   .map(_ @@ auth)
                   .flatMap(_.serve[TestService & Uploads].forkScoped)
      _       <- Live.live(Clock.sleep(3 seconds))
      service <- ZIO.service[TestService]
    } yield service
  }

  override def spec = suite("ZIO Http Quick") {
    val suite = TapirAdapterSpec.makeSuite(
      "QuickAdapterSpec",
      uri"http://localhost:8090/api/graphql",
      wsUri = Some(uri"ws://localhost:8090/ws/graphql"),
      uploadUri = Some(uri"http://localhost:8090/upload/graphql")
    )
    suite.provideShared(
      apiLayer,
      Scope.default,
      Server.defaultWith(_.port(8090).enableRequestStreaming.responseCompression())
    )
  }
}
