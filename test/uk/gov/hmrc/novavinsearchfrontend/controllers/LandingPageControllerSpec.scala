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

package uk.gov.hmrc.novavinsearchfrontend.controllers

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.novavinsearchfrontend.config.AppConfig
import uk.gov.hmrc.novavinsearchfrontend.controllers.actions.{FakeFailingAuthConnector, FakeSuccessAuthConnector}

class LandingPageControllerSpec extends AnyWordSpec with Matchers with OptionValues:

  private val dvlaAgent =
    new ~(
      new ~(Some("internalId"), Some(AffinityGroup.Agent)),
      Enrolments(Set(Enrolment("HMRC-NOVRN-AGNT", List(EnrolmentIdentifier("VATAgentRefNo", "AF0851")), "Activated")))
    )

  private val individual = new ~(new ~(Some("internalId"), Some(AffinityGroup.Individual)), Enrolments(Set.empty))

  private def application(authConnector: AuthConnector) =
    GuiceApplicationBuilder().overrides(bind[AuthConnector].toInstance(authConnector)).build()

  private def requestFrom(ip: String) = FakeRequest().withHeaders("True-Client-IP" -> ip)

  "onPageLoad" should:
    "show the landing page to an approved agent on their own IP address" in:
      val app = application(FakeSuccessAuthConnector(dvlaAgent))
      running(app):
        val result = app.injector.instanceOf[LandingPageController].onPageLoad(requestFrom("192.0.2.1"))

        status(result)        shouldBe OK
        contentAsString(result) should include("Search for a vehicle with the vehicle identification number")

    "send an approved agent on another organisation's IP address to the no permission page" in:
      val app = application(FakeSuccessAuthConnector(dvlaAgent))
      running(app):
        val result = app.injector.instanceOf[LandingPageController].onPageLoad(requestFrom("192.0.2.2"))

        status(result)           shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UnauthorisedController.onPageLoad.url)

    "send an individual to the no permission page" in:
      val app = application(FakeSuccessAuthConnector(individual))
      running(app):
        val result = app.injector.instanceOf[LandingPageController].onPageLoad(requestFrom("192.0.2.1"))

        status(result)           shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UnauthorisedController.onPageLoad.url)

    "send a signed out user to sign in" in:
      val app = application(FakeFailingAuthConnector(new MissingBearerToken))
      running(app):
        val result = app.injector.instanceOf[LandingPageController].onPageLoad(requestFrom("192.0.2.1"))

        status(result)               shouldBe SEE_OTHER
        redirectLocation(result).value should startWith(app.injector.instanceOf[AppConfig].loginUrl)
