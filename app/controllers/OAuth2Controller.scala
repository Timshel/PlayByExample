package controllers

import play.api.mvc._
import services.auth._

trait OAuth2GithubConfig extends OAuth2DefaultConfiguration {
  override val authenticateCall = routes.Application.authenticate
  override val authenticatedCall = routes.Application.testGithub
  override val configuration = "github"
}

trait GithubOAuthController extends Controller with OAuth2Authentication with OAuth2GithubConfig
