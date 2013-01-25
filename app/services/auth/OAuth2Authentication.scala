package services.auth

import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

sealed case class AuthenticatedRequest(token: OAuth2Token, private val request: Request[AnyContent])
  extends WrappedRequest(request)

trait OAuth2Authentication {
  self: Controller with OAuth2Configuration =>

  object Ws {
    def url(url: String)(implicit request: AuthenticatedRequest) = {
      WS.url(url).withQueryString("access_token" -> request.token.accessToken).withHeaders("Accept" -> "application/json")
    }
  }

  private lazy val logger = Logger("services.auth.OAuth2Authentication")
  private val sessionKeyToken = "oauth2token"
  private val sessionKeyCsrf = "oauth2csrf"

  implicit def token(implicit request: AuthenticatedRequest) = request.token

  def Authenticated(action: AuthenticatedRequest => Result) = Action { implicit request =>
    parseToken match {
      case Some(token) => action(AuthenticatedRequest(token, request))
      case _ => Redirect(authenticateCall)
    }
  }

  def authenticate = Action { implicit request =>
    request.getQueryString("error") match {
      case Some(error) =>
        displayError(error, request.getQueryString("error_description"))
      case _ =>
        (request.getQueryString("code"), request.getQueryString("state"), session.get(sessionKeyCsrf)) match {
          // Step 1: request Access code
          case (None, _, _) =>
            redirectToLoginPage
          // Step 2: request Access token
          case (Some(code), Some(csrf), Some(csrfSess)) if csrf == csrfSess =>
            checkAccessToken(code)
          // Error: CSRF verification failed
          case (Some(code), csrf, csrfSess) =>
            displayError("Error during authentication",
              Some("CSRF field doesn't match: %s != %s (%s)".format(csrf, csrfSess, request.remoteAddress)))
        }
    }
  }

  private def checkAccessToken(code: String)(implicit request: RequestHeader) = {
    Async {
      logger.trace(s"AccessCode received: $code")
      requestAccessToken(code).map(result =>
        result.status match {
          case OK => redirectSuccessful(result)
          case _ => displayError("Unexpected error after requesting token", Some("(%d) %s".format(result.status, result.body)))
        })
    }
  }

  private def requestAccessToken(code: String)(implicit request: RequestHeader) = {
    val params = Map(
      "grant_type" -> "authorization_code",
      "code" -> code,
      "redirect_uri" -> authenticateCall.absoluteURL(false),
      "client_id" -> oauth2info.clientId,
      "client_secret" -> oauth2info.clientSecret)
    logger.trace(s"Request access token : $params")

    WS.url(oauth2info.urlAccessToken)
      .withHeaders("Accept" -> "application/json")
      .post(params.mapValues(Seq(_)))
  }

  private def redirectSuccessful(response: play.api.libs.ws.Response)(implicit request: RequestHeader) = {
    response.status match {
      case 200 => {
        logger.trace(s"AccessToken received: ${response.json}")
        val token = response.json.as[OAuth2Token]
        val tokenStr = Json.stringify(Json.toJson(token))
        Redirect(authenticatedCall)
          .withSession(request.session - sessionKeyCsrf + (sessionKeyToken -> tokenStr))
      }
      case _ => displayError("Invalid authentication (%s:%s)".format(response.status, response.body))
    }
  }

  private def redirectToLoginPage(implicit request: RequestHeader) = {
    val csrf = java.util.UUID.randomUUID().toString
    val redirectQueryString = Map(
      "client_id"       -> oauth2info.clientId,
      "redirect_uri"    -> authenticateCall.absoluteURL(false),
      "state"           -> csrf,
      "scope"           -> "",
      "response_type"   -> "code")
    val url = redirectQueryString.foldLeft(oauth2info.urlAuthorize+"?")((url, qs) => url + qs._1+"="+qs._2+"&")
    logger.trace(s"Redirect to login page: $url")

    Redirect(url).withSession(request.session + (sessionKeyCsrf -> csrf))
  }

  private def parseToken(implicit request: RequestHeader): Option[OAuth2Token] = {
    session.get(sessionKeyToken).flatMap(Json.parse(_).asOpt[OAuth2Token])
  }

  private def displayError(techError: String, userError: Option[String] = None) = {
    logger.error(userError.map("\n" + _).getOrElse("") + techError)
    BadRequest(userError.getOrElse(techError))
  }
}
