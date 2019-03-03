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

package uk.gov.hmrc.bindingtariffrulingfrontend.service

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffrulingfrontend.connector.BindingTariffClassificationConnector
import uk.gov.hmrc.bindingtariffrulingfrontend.connector.model.{Attachment, Case, CaseStatus, Decision}
import uk.gov.hmrc.bindingtariffrulingfrontend.controllers.forms.SimpleSearch
import uk.gov.hmrc.bindingtariffrulingfrontend.model.{Paged, Ruling}
import uk.gov.hmrc.bindingtariffrulingfrontend.repository.RulingRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class RulingServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val connector = mock[BindingTariffClassificationConnector]
  private val repository = mock[RulingRepository]

  private val service = new RulingService(repository, connector)


  "Service GET by reference" should {

    "delegate to repository" in {
      given(repository.get("id")) willReturn Future.successful(None)
      await(service.get("id")) shouldBe None
    }
  }

  "Service GET by search" should {
    val search = SimpleSearch("query", 1, 1)

    "delegate to repository" in {
      given(repository.get(search)) willReturn Future.successful(Paged.empty[Ruling])
      await(service.get(search)) shouldBe Paged.empty[Ruling]
    }
  }

  "Service Refresh" should {
    val startDate = Instant.now().plus(10, ChronoUnit.SECONDS)
    val endDate = Instant.now()
    val validDecision = Decision("code", Some(startDate), Some(endDate), "justification", "description")
    val publicAttachment = Attachment("file-id", public = true)
    val privateAttachment = Attachment("file-id", public = false)
    val validCase: Case = Case(
      reference = "ref",
      status = CaseStatus.COMPLETED,
      decision = Some(validDecision),
      attachments = Seq(publicAttachment, privateAttachment),
      keywords = Set("keyword")
    )

    "do nothing when case doesnt exist in repository or connector" in {
      given(repository.get("ref")) willReturn Future.successful(None)
      given(connector.get("ref")) willReturn Future.successful(None)

      await(service.refresh("ref"))

      verify(repository, never()).update(any[Ruling], anyBoolean())
    }

    "create new ruling" in {
      given(repository.get("ref")) willReturn Future.successful(None)
      given(connector.get("ref")) willReturn Future.successful(Some(validCase))
      given(repository.update(any[Ruling], any[Boolean])) will returnTheRuling

      await(service.refresh("ref"))

      verify(repository).update(any[Ruling], refEq(true))
      theRulingUpdated shouldBe Ruling(
        "ref",
        "code",
        startDate,
        endDate,
        "justification",
        "description",
        Set("keyword"),
        Seq("file-id")
      )
    }

    "update existing ruling" in {
      val existing = Ruling("ref", "old", Instant.now, Instant.now, "old", "old", Set("old"), Seq("old"))
      given(repository.get("ref")) willReturn Future.successful(Some(existing))
      given(connector.get("ref")) willReturn Future.successful(Some(validCase))
      given(repository.update(any[Ruling], any[Boolean])) will returnTheRuling

      await(service.refresh("ref"))

      verify(repository).update(any[Ruling], refEq(false))
      theRulingUpdated shouldBe Ruling(
        "ref",
        "code",
        startDate,
        endDate,
        "justification",
        "description",
        Set("keyword"),
        Seq("file-id")
      )
    }

    "delete existing ruling" in {
      val existing = Ruling("ref", "old", Instant.now, Instant.now, "old", "old", Set("old"), Seq("old"))
      given(repository.get("ref")) willReturn Future.successful(Some(existing))
      given(connector.get("ref")) willReturn Future.successful(None)
      given(repository.delete("ref")) willReturn Future.successful(())

      await(service.refresh("ref"))

      verify(repository).delete("ref")
    }

    "filter cases not COMPLETED" in {
      given(repository.get("ref")) willReturn Future.successful(None)
      given(connector.get("ref")) willReturn Future.successful(Some(validCase.copy(status = CaseStatus.OPEN)))

      await(service.refresh("ref"))

      verify(repository, never()).update(any[Ruling], anyBoolean())
    }

    "filter cases without Decision" in {
      given(repository.get("ref")) willReturn Future.successful(None)
      given(connector.get("ref")) willReturn Future.successful(Some(validCase.copy(decision = None)))

      await(service.refresh("ref"))

      verify(repository, never()).update(any[Ruling], anyBoolean())
    }

    "filter cases without Decision Start Date" in {
      given(repository.get("ref")) willReturn Future.successful(None)
      given(connector.get("ref")) willReturn Future.successful(Some(validCase.copy(decision = Some(validDecision.copy(effectiveStartDate = None)))))

      await(service.refresh("ref"))

      verify(repository, never()).update(any[Ruling], anyBoolean())
    }

    "filter cases without Decision End Date" in {
      given(repository.get("ref")) willReturn Future.successful(None)
      given(connector.get("ref")) willReturn Future.successful(Some(validCase.copy(decision = Some(validDecision.copy(effectiveEndDate = None)))))

      await(service.refresh("ref"))

      verify(repository, never()).update(any[Ruling], anyBoolean())
    }

    def theRulingUpdated: Ruling = {
      val captor = ArgumentCaptor.forClass(classOf[Ruling])
      verify(repository).update(captor.capture(), anyBoolean())
      captor.getValue
    }

    def returnTheRuling: Answer[Future[Ruling]] = new Answer[Future[Ruling]] {
      override def answer(invocation: InvocationOnMock): Future[Ruling] = Future.successful(invocation.getArgument(0))
    }
  }


  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(repository, connector)
  }
}
