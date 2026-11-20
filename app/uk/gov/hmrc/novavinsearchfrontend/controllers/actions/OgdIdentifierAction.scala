/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.novavinsearchfrontend.controllers.actions

import play.api.Logging
import play.api.mvc.*
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.novavinsearchfrontend.config.AppConfig
import uk.gov.hmrc.novavinsearchfrontend.controllers.routes
import uk.gov.hmrc.novavinsearchfrontend.models.requests.IdentifierRequest
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class OgdIdentifierAction @Inject() (override val authConnector: AuthConnector, appConfig: AppConfig, val parser: BodyParsers.Default)(using
  val executionContext: ExecutionContext
) extends ActionBuilder[IdentifierRequest, AnyContent]
    with AuthorisedFunctions
    with Logging:

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] =
    given HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised()
      .retrieve(Retrievals.internalId and Retrievals.affinityGroup and Retrievals.allEnrolments):
        case Some(_) ~ Some(AffinityGroup.Agent) ~ ApprovedAgentRefNo(vatAgentRefNo) =>
          block(IdentifierRequest(request, vatAgentRefNo))
        case _ =>
          logger.warn("OGD route accessed without an approved HMRC-NOVRN-AGNT enrolment")
          Future.successful(Redirect(routes.UnauthorisedController.onPageLoad))
      .recover:
        case _: NoActiveSession        => Redirect(appConfig.loginUrl, Map("continue" -> Seq(appConfig.loginContinueUrl)))
        case _: AuthorisationException => Redirect(routes.UnauthorisedController.onPageLoad)

  private object ApprovedAgentRefNo:
    def unapply(enrolments: Enrolments): Option[String] =
      enrolments
        .getEnrolment("HMRC-NOVRN-AGNT")
        .filter(_.isActivated)
        .flatMap(_.getIdentifier("VATAgentRefNo"))
        .map(_.value)
        .filter(appConfig.ogdAgentAllowedIps.contains)
