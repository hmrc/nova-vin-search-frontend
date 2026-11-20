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

package uk.gov.hmrc.novavinsearchfrontend

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.novavinsearchfrontend.controllers.actions.FakeSuccessAuthConnector

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global

class AllowlistFilterIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite:

  private val dvlaAgent =
    new ~(
      new ~(Some("internalId"), Some(AffinityGroup.Agent)),
      Enrolments(Set(Enrolment("HMRC-NOVRN-AGNT", List(EnrolmentIdentifier("VATAgentRefNo", "AF0851")), "Activated")))
    )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure("ogd-agents.allowed-ips.AF0851" -> List("192.0.2.1", "198.51.100.1"))
      .overrides(bind[AuthConnector].toInstance(FakeSuccessAuthConnector(dvlaAgent)))
      .build()

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"

  private def getFrom(ip: String, url: URL): HttpResponse =
    httpClient
      .get(url)(HeaderCarrier())
      .setHeader("True-Client-IP" -> ip)
      .transform(_.withFollowRedirects(false))
      .execute()
      .futureValue

  "the allowlist filter" should:
    "send a request from an IP address it does not list to the no permission page, even when the agent's own list has it" in:
      val response = getFrom("198.51.100.1", url"$baseUrl/nova-vin-search/search-vehicle-by-vin")

      response.status             shouldBe 303
      response.header("Location") shouldBe Some("/nova-vin-search/no-permission")

    "let a refused IP address see the no permission page" in:
      getFrom("198.51.100.1", url"$baseUrl/nova-vin-search/no-permission").status shouldBe 200

    "let a refused IP address reach the assets" in:
      getFrom("198.51.100.1", url"$baseUrl/nova-vin-search/assets/not-a-real-file.css").status shouldBe 404

    "let a refused IP address reach the hmrc-frontend assets" in:
      getFrom("198.51.100.1", url"$baseUrl/nova-vin-search/hmrc-frontend/assets/not-a-real-file.css").status shouldBe 404

    "let an approved agent on their own IP address reach the landing page" in:
      getFrom("192.0.2.1", url"$baseUrl/nova-vin-search/search-vehicle-by-vin").status shouldBe 200

    "let an approved IP address through to the agent check, which refuses another organisation's IP address" in:
      val response = getFrom("192.0.2.2", url"$baseUrl/nova-vin-search/search-vehicle-by-vin")

      response.status             shouldBe 303
      response.header("Location") shouldBe Some("/nova-vin-search/no-permission")
