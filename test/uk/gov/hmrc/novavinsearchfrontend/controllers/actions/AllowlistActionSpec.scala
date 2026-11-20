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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{AnyContentAsEmpty, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.novavinsearchfrontend.controllers.routes
import uk.gov.hmrc.novavinsearchfrontend.models.requests.IdentifierRequest

import scala.concurrent.Future

class AllowlistActionSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite:

  private val action       = app.injector.instanceOf[AllowlistAction]
  private val noPermission = routes.UnauthorisedController.onPageLoad.url

  private def run(request: FakeRequest[AnyContentAsEmpty.type], vatAgentRefNo: String): Future[Result] =
    action.invokeBlock(IdentifierRequest(request, vatAgentRefNo), _ => Future.successful(Results.Ok))

  private def runFrom(ip: String, vatAgentRefNo: String): Future[Result] =
    run(FakeRequest().withHeaders("True-Client-IP" -> ip), vatAgentRefNo)

  "AllowlistAction" should:
    "admit AF0851 from its own IP address" in:
      status(runFrom("192.0.2.1", "AF0851")) shouldBe OK

    "admit AE8653 from its own IP address" in:
      status(runFrom("192.0.2.2", "AE8653")) shouldBe OK

    "send AF0851 to the no permission page from another organisation's IP address" in:
      val result = runFrom("192.0.2.2", "AF0851")

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send AF0851 to the no permission page from an IP address on no list" in:
      val result = runFrom("198.51.100.1", "AF0851")

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send an agent reference with no IP address list to the no permission page" in:
      val result = runFrom("192.0.2.1", "ZZ9999")

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)

    "send AF0851 to the no permission page when the request has no True-Client-IP header" in:
      val result = run(FakeRequest(), "AF0851")

      status(result)           shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(noPermission)
