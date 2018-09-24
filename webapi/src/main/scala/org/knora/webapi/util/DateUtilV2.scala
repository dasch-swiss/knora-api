/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.util.{Calendar, GregorianCalendar}

import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.{AssertionException, IRI, InconsistentTriplestoreDataException, OntologyConstants}

/**
  * Utility functions for converting dates.
  */
object DateUtilV2 {

    /**
      * Enumeration for era.
      */
    object KnoraEraV2 extends Enumeration {
        val BCE: KnoraEraV2.Value = Value(0, "BCE")
        val CE: KnoraEraV2.Value = Value(1, "CE")

        val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

        /**
          * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
          * [[InconsistentTriplestoreDataException]].
          *
          * @param name the name of the value.
          * @return the requested value.
          */
        def lookup(name: String): Value = {
            valueMap.get(name) match {
                case Some(value) => value
                case None => throw InconsistentTriplestoreDataException(s"Calendar era not supported: $name")
            }
        }
    }

    /**
      * Represents a date as year, month, day including the given precision.
      *
      * @param year       the date's year.
      * @param maybeMonth the date's month, if given.
      * @param maybeDay   the date's day, if given.
      * @param era        the date's era.
      */
    case class DateYearMonthDay(year: Int, maybeMonth: Option[Int], maybeDay: Option[Int], era: KnoraEraV2.Value) {
        if (maybeMonth.isEmpty && maybeDay.isDefined) {
            throw AssertionException(s"Invalid date: ${super.toString}")
        }

        /**
          * Determines the precision of this date.
          *
          * @return the precision of the date.
          */
        def getPrecision: KnoraPrecisionV1.Value = {
            (maybeMonth, maybeDay) match {
                case (Some(_), Some(_)) => KnoraPrecisionV1.DAY
                case (Some(_), None) => KnoraPrecisionV1.MONTH
                case (None, None) => KnoraPrecisionV1.YEAR
                case _ => throw AssertionException("Unreachable code")
            }
        }

        override def toString: String = {
            (maybeMonth, maybeDay) match {
                case (Some(month), Some(day)) =>
                    // Day precision: include the year, the month, and the day.
                    f"$year%04d${StringFormatter.PrecisionSeparator}$month%02d${StringFormatter.PrecisionSeparator}$day%02d${StringFormatter.EraSeparator}$era"

                case (Some(month), None) =>
                    // Month precision: include the year and the month.
                    f"$year%04d${StringFormatter.PrecisionSeparator}$month%02d${StringFormatter.EraSeparator}$era"

                case (None, None) =>
                    // Year precision: just include the year.
                    f"$year%04d${StringFormatter.EraSeparator}$era"

                case _ => throw AssertionException("Unreachable code")
            }
        }

        /**
          * Converts the [[DateYearMonthDay]] to knora-api assertions representing a start date.
          *
          * @return a Map of knora-api value properties to numbers (year, month, day) taking into account the given precision.
          */
        def toStartDateAssertions: Map[IRI, Int] = {
            Map(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear -> year) ++
                maybeMonth.map(month => OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth -> month) ++
                maybeDay.map(day => OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay -> day)
        }

        /**
          * Converts the era to knora-api assertions representing a start era.
          *
          * @return a map of knora-api value StartEra property to era
          */
        def toStartEraAssertion: Map[IRI, String] = {
            Map(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra -> era.toString)
        }

        /**
          * Converts the [[DateYearMonthDay]] to knora-api assertions representing an end date.
          *
          * @return a Map of knora-api value properties to numbers (year, month, day) taking into account the given precision.
          */
        def toEndDateAssertions: Map[IRI, Int] = {
            Map(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear -> year) ++
                maybeMonth.map(month => OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth -> month) ++
                maybeDay.map(day => OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay -> day)
        }

        /**
          * Converts the era to knora-api assertions representing an end era.
          *
          * @return a map of knora-api value EndEra property to era
          */
        def toEndEraAssertion: Map[IRI, String] = {
            Map(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra -> era.toString)
        }
    }

    /**
      * Converts a date range to a string for use in API v2 responses.
      *
      * @param startDate the start date.
      * @param endDate   the end date.
      * @param calendar  the calendar.
      * @return a string representing the date range.
      */
    def dateRangeToString(startDate: DateYearMonthDay, endDate: DateYearMonthDay, calendar: KnoraCalendarV1.Value): String = {
        val str = new StringBuilder(calendar.toString).append(StringFormatter.CalendarSeparator)
        str.append(startDate.toString)

        // Can we represent the start and end dates as a single date?
        if (startDate != endDate) {
            // No. Include the end date.
            str.append(StringFormatter.CalendarSeparator).append(endDate.toString)
        }

        str.toString
    }

    /**
      * Converts a JDN to a [[DateYearMonthDay]] using the given calendar.
      *
      * @param julianDayNumber the Julian Day Number.
      * @param precision       the precision for the given JDN.
      * @param calendar        the calendar to which the JDN should be converted.
      * @return a [[DateYearMonthDay]].
      */
    def jdnToDateYearMonthDay(julianDayNumber: Int, precision: KnoraPrecisionV1.Value, calendar: KnoraCalendarV1.Value): DateYearMonthDay = {
        val javaGregorianCalendarDate: GregorianCalendar = DateUtilV1.convertJulianDayNumberToJavaGregorianCalendar(julianDayNumber, calendar)

        val year: Int = javaGregorianCalendarDate.get(Calendar.YEAR)
        val month: Int = javaGregorianCalendarDate.get(Calendar.MONTH) + 1 // Attention: in java.util.Calendar, month count starts with 0
        val day: Int = javaGregorianCalendarDate.get(Calendar.DAY_OF_MONTH)
        val era: String = DateUtilV1.eraToString(javaGregorianCalendarDate.get(Calendar.ERA))

        val (maybeMonth, maybeDay) = precision match {
            case KnoraPrecisionV1.YEAR => (None, None)
            case KnoraPrecisionV1.MONTH => (Some(month), None)
            case KnoraPrecisionV1.DAY => (Some(month), Some(day))
        }

        DateYearMonthDay(year = year, maybeMonth = maybeMonth, maybeDay = maybeDay, era = KnoraEraV2.lookup(era))

    }


}