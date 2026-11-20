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

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{BodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.novavinsearchfrontend.config.AppConfig
import uk.gov.hmrc.novavinsearchfrontend.controllers.routes

import java.net.URLEncoder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OgdIdentifierActionSpec extends AnyWordSpec with Matchers with OptionValues with GuiceOneAppPerSuite:

  private val appConfig    = app.injector.instanceOf[AppConfig]
  private val bodyParsers  = app.injector.instanceOf[BodyParsers.Default]
  private val noPermission = routes.UnauthorisedController.onPageLoad.url

  private def ogdEnrolment(refNo: String, state: String = "Activated") =
    Enrolment("HMRC-NOVRN-AGNT", List(EnrolmentIdentifier("VATAgentRefNo", refNo)), state)

  private def retrievals(internalId: Option[String], affinityGroup: AffinityGroup, enrolments: Set[Enrolment]) =
    new ~(new ~(internalId, Some(affinityGroup)), Enrolments(enrolments))

  private def run(authConnector: AuthConnector): Future[Result] =
    new OgdIdentifierAction(authConnector, appConfig, bodyParsers)
      .apply(request => Results.Ok(request.vatAgentRefNo))(FakeRequest())

  private def runAs(internalId: Option[String], affinityGroup: AffinityGroup, enrolments: Enrolment*): Future[Result] =
    run(FakeSuccessAuthConnector(retrievals(internalId, affinityGroup, enrolments.toSet)))

  "OgdIdentifierAction" should:
    "admit an agent with an activated HMRC-NOVRN-AGNT enrolment for AF0851" in:
      val result = runAs(Some("internalId"), AffinityGroup.Agent, ogdEnrolment("AF0851"))

      status(result)          shouldBe OK
      contentAsString(result) shouldBe "AF0851"

    "admit an agent with an activated HMRC-NOVRN-AGNT enrolment for AE8653" in:
      val result = runAs(Some("internalId"), AffinityGroup.Agent, ogdEnrolment("AE8653"))

      status(result)          shouldBe OK
      contentAsString(result) shouldBe "AE8653"

    "send an agent with an unapproved agent reference to the no permission page" in:
      val result = runAs(Some("internalId"), AffinityGroup.Agent, ogdEnrolment("ZZ9999"))

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an agent whose HMRC-NOVRN-AGNT enrolment is not activated to the no permission page" in:
      val result = runAs(Some("internalId"), AffinityGroup.Agent, ogdEnrolment("AF0851", state = "NotYetActivated"))

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an agent whose HMRC-NOVRN-AGNT enrolment has no VATAgentRefNo to the no permission page" in:
      val result = runAs(Some("internalId"), AffinityGroup.Agent, Enrolment("HMRC-NOVRN-AGNT", Nil, "Activated"))

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an agent with only an HMCE-VAT-AGNT enrolment to the no permission page" in:
      val vatAgent = Enrolment("HMCE-VAT-AGNT", List(EnrolmentIdentifier("AgentRefNo", "AF0851")), "Activated")
      val result   = runAs(Some("internalId"), AffinityGroup.Agent, vatAgent)

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an agent with no enrolments to the no permission page" in:
      val result = runAs(Some("internalId"), AffinityGroup.Agent)

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an organisation with an activated HMRC-NOVRN-AGNT enrolment to the no permission page" in:
      val result = runAs(Some("internalId"), AffinityGroup.Organisation, ogdEnrolment("AF0851"))

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an individual to the no permission page" in:
      val result = runAs(Some("internalId"), AffinityGroup.Individual)

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an agent with no internal id to the no permission page" in:
      val result = runAs(None, AffinityGroup.Agent, ogdEnrolment("AF0851"))

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send a user with no bearer token to sign in and back to the landing page" in:
      val result = run(FakeFailingAuthConnector(new MissingBearerToken))

      status(result)               shouldBe SEE_OTHER
      redirectLocation(result).value should startWith(appConfig.loginUrl)
      redirectLocation(result).value should include(URLEncoder.encode(appConfig.loginContinueUrl, "UTF-8"))

    "send a user with an expired bearer token to sign in" in:
      val result = run(FakeFailingAuthConnector(new BearerTokenExpired))

      status(result)               shouldBe SEE_OTHER
      redirectLocation(result).value should startWith(appConfig.loginUrl)

    "send a user with insufficient enrolments to the no permission page" in:
      val result = run(FakeFailingAuthConnector(new InsufficientEnrolments))

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)
