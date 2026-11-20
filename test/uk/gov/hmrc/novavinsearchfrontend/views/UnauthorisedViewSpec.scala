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

package uk.gov.hmrc.novavinsearchfrontend.views

import org.jsoup.Jsoup
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import uk.gov.hmrc.novavinsearchfrontend.views.html.UnauthorisedView

class UnauthorisedViewSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite:

  private val request = FakeRequest()
  private val view    = app.injector.instanceOf[UnauthorisedView]

  private val document =
    Jsoup.parse(view()(request, app.injector.instanceOf[MessagesApi].preferred(request)).body)

  private def mainParagraph(index: Int) = document.select("main p.govuk-body").eq(index)

  "UnauthorisedView" should:
    "have the page title" in:
      document.title shouldBe "You do not have permission to access this page - Notification of Vehicle Arrivals - GOV.UK"

    "have the heading" in:
      document.select("h1.govuk-heading-l").text shouldBe "You do not have permission to access this page"

    "explain that the user is not authorised" in:
      mainParagraph(0).text shouldBe "You tried to access a part of this service that you’re not authorised to use."

    "link to HMRC technical support in a new tab" in:
      val link = mainParagraph(1).select("a.govuk-link")

      mainParagraph(1).text shouldBe "If this is wrong, contact HMRC technical support (opens in new tab)."
      link.attr("href")     shouldBe "https://www.gov.uk/find-hmrc-contacts/technical-support-with-hmrc-online-services"
      link.attr("target")   shouldBe "_blank"

    "link back to the landing page" in:
      val link = mainParagraph(2).select("> a.govuk-link")

      link.text         shouldBe "Return to home"
      link.attr("href") shouldBe "/nova-vin-search/search-vehicle-by-vin"

    "have a back link" in:
      document.select("a.govuk-back-link").text shouldBe "Back"

    "link to the report a problem form for this service" in:
      document.select(".hmrc-report-technical-issue").attr("href") should include("service=nova-vin-search-frontend")
