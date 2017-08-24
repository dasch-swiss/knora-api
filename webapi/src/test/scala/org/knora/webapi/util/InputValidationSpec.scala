/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import org.knora.webapi.BadRequestException
import org.scalatest.{Matchers, WordSpec}

/**
  * Created by sepidehalassi on 7/3/17.
  *
  * Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
  */
class InputValidationSpec extends WordSpec with Matchers {

    "The InputValidation class" should {

        "not accept 2017-05-10" in {
            val dateString = "2017-05-10"
            assertThrows[IllegalArgumentException] {
                InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
            }
        }

        "accept GREGORIAN:2017" in {
            val dateString = "GREGORIAN:2017"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05" in {
            val dateString = "GREGORIAN:2017-05"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05-10" in {
            val dateString = "GREGORIAN:2017-05-10"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:2017-05-10:2017-05-12" in {
            val dateString = "GREGORIAN:2017-05-10:2017-05-12"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 BC" in {
            val dateString = "GREGORIAN:500-05-10 BC"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 AD"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:50 BCE" in {
            val dateString = "JULIAN:50 BCE"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:1560-05 CE" in {
            val dateString = "JULIAN:1560-05 CE"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:217-05-10 BCE" in {
            val dateString = "JULIAN:217-05-10 BCE"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:2017-05-10:2017-05-12" in {
            val dateString = "JULIAN:2017-05-10:2017-05-12"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }

        "accept JULIAN:2017:2017-5-12" in {
            val dateString = "JULIAN:2017:2017-5-12"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }
        
        "accept JULIAN:500 BCE:400 BCE" in {
            val dateString = "JULIAN:500 BCE:400 BCE"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }
        
        "accept GREGORIAN:10 BC:1 AD" in {
            val dateString = "GREGORIAN:10 BC:1 AD"
            InputValidation.toDate(dateString, () => throw BadRequestException(s"Not accepted ${dateString}"))
        }
        
        "not accept month 00" in {
            val dateString = "GREGORIAN:2017-00:2017-02"
            assertThrows[IllegalArgumentException] {
                InputValidation.toDate(dateString, () => throw BadRequestException(s"month 00 in ${dateString} Not accepted" ))
            }
        }
        
        "not accept day 00" in {
            val dateString = "GREGORIAN:2017-01-00"
            assertThrows[IllegalArgumentException] {
                InputValidation.toDate(dateString, () => throw BadRequestException(s"day 00 in ${dateString} Not accepted" ))
            }
        }
        
        "not accept year 0 " in {
            val dateString = "GREGORIAN:0 BC"
            assertThrows[IllegalArgumentException] {
                InputValidation.toDate(dateString, () => throw BadRequestException(s"Year 0 is Not accepted ${dateString}"))
            }
        }

        "recognize the url of the dhlab site as a valid IRI" in {
            val testUrl: String = "http://dhlab.unibas.ch/"

            val validIri = InputValidation.toIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))

            validIri should be(testUrl)
        }

        "recognize the url of the DaSCH site as a valid IRI" in {
            val testUrl = "http://dasch.swiss"

            val validIri = InputValidation.toIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))

            validIri should be(testUrl)
        }

    }
}
