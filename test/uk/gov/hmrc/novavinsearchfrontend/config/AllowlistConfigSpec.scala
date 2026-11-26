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

package uk.gov.hmrc.novavinsearchfrontend.config

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class AllowlistConfigSpec extends AnyWordSpec with Matchers:

  private def resolved(environmentConfig: String) =
    ConfigFactory
      .parseString(environmentConfig)
      .withFallback(ConfigFactory.parseResources("application.conf"))
      .withFallback(ConfigFactory.defaultReference())
      .resolve()

  private def filterIps(environmentConfig: String) =
    resolved(environmentConfig)
      .getStringList("bootstrap.filters.allowlist.ips")
      .asScala
      .toList

  "the allowlist" should:
    "be off in the service's own config" in:
      resolved("").getBoolean("bootstrap.filters.allowlist.enabled") shouldBe false

  "the allowlist filter's IP list" should:
    "hold every organisation's IP addresses" in:
      filterIps("") shouldBe List("192.0.2.1", "192.0.2.2")

    "follow an environment's own organisation lists" in:
      val environmentConfig =
        """ogd-agents.allowed-ips {
          |  AF0851 = ["203.0.113.1", "203.0.113.2"]
          |  AE8653 = ["203.0.113.3"]
          |}""".stripMargin

      filterIps(environmentConfig) shouldBe List("203.0.113.1", "203.0.113.2", "203.0.113.3")
