/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffrulingfrontend.controllers.action

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{Request, Result, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffrulingfrontend.config.AppConfig
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class WhitelistedActionTest extends UnitSpec with MockitoSugar {

  private val block = mock[Request[_] => Future[Result]]
  private val config = mock[AppConfig]
  private val action = new WhitelistedAction(config)

  "Whitelisted Action" should {
    "Filter unauthenticated" in {
      given(block.apply(any[Request[_]])) willReturn Future.successful(Results.Ok)
      given(config.whitelist) willReturn Some(Set[String]())

      await(action.invokeBlock(FakeRequest(), block)) shouldBe Results.Forbidden
    }

    "Filter authenticated" in {
      given(block.apply(any[Request[_]])) willReturn Future.successful(Results.Ok)
      given(config.whitelist) willReturn Some(Set("ip"))

      await(action.invokeBlock(FakeRequest().withHeaders("True-Client-IP" -> "ip"), block)) shouldBe Results.Ok
    }

    "Not Filter when disabled" in {
      given(block.apply(any[Request[_]])) willReturn Future.successful(Results.Ok)
      given(config.whitelist) willReturn None

      await(action.invokeBlock(FakeRequest(), block)) shouldBe Results.Ok
    }
  }

}
