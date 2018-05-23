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

package org.knora.webapi.routing.v2

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourceTEIGetRequestV2, ResourcesGetRequestV2, ResourcesPreviewGetRequestV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{BadRequestException, IRI, InternalSchema, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
  * Provides a routing function for API v2 routes that deal with resources.
  */
object ResourcesRouteV2 extends Authenticator {
    private val Text_Property = "textProperty"

    /**
      * Gets the Iri of the property that represents the text of the resource.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getTextPropertyFromParams(params: Map[String, String]): SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val textProperty = params.get(Text_Property)

        textProperty match {
            case Some(textPropIriStr: String) =>
                val externalResourceClassIri = textPropIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $textPropIriStr"))

                if (!externalResourceClassIri.isKnoraApiV2EntityIri) {
                    throw BadRequestException(s"$textPropIriStr is not a valid knora-api property IRI")
                }

                externalResourceClassIri.toOntologySchema(InternalSchema)

            case None => throw BadRequestException(s"param $Text_Property not set")
        }
    }


    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        path("v2" / "resources" / Segments) { (resIris: Seq[String]) =>
            get {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    if (resIris.size > settings.v2ResultsPerPage) throw BadRequestException(s"List of provided resource Iris exceeds limit of ${settings.v2ResultsPerPage}")

                    val resourceIris: Seq[IRI] = resIris.map {
                        resIri: String =>
                            stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: '$resIri'"))
                    }

                    val requestMessage = ResourcesGetRequestV2(resourceIris = resourceIris, requestingUser = requestingUser)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }
        } ~ path("v2" / "resourcespreview" / Segments) { (resIris: Seq[String]) =>
            get {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    if (resIris.size > settings.v2ResultsPerPage) throw BadRequestException(s"List of provided resource Iris exceeds limit of ${settings.v2ResultsPerPage}")

                    val resourceIris: Seq[IRI] = resIris.map {
                        resIri: String =>
                            stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: '$resIri'"))
                    }

                    val requestMessage = ResourcesPreviewGetRequestV2(resourceIris = resourceIris, requestingUser = requestingUser)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }

        } ~ path("v2" / "tei" / Segment) { (resIri: String) =>
            get {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    val resourceIri = stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: '$resIri'"))

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    // the the property that represents the text
                    val textProperty: SmartIri = getTextPropertyFromParams(params)

                    val requestMessage = ResourceTEIGetRequestV2(resourceIri = resourceIri, textProperty = textProperty, requestingUser = requestingUser)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }

        }

    }


}