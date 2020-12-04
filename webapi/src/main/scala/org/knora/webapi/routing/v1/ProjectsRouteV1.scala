/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV1}

class ProjectsRouteV1(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with ProjectV1JsonProtocol {

  /**
    * Returns the route.
    */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {

    path("v1" / "projects") {
      get {
        /* returns all projects */
        requestContext =>
          val requestMessage = for {
            userProfile <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            ).map(_.asUserProfileV1)
          } yield
            ProjectsGetRequestV1(
              featureFactoryConfig = featureFactoryConfig,
              userProfile = Some(userProfile)
            )

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessage,
            requestContext,
            settings,
            responderManager,
            log
          )
      }
    } ~ path("v1" / "projects" / Segment) { value =>
      get {
        /* returns a single project identified either through iri or shortname */
        parameters("identifier" ? "iri") { identifier: String => requestContext =>
          val requestMessage = if (identifier != "iri") { // identify project by shortname.
            val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
            for {
              userProfile <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              ).map(_.asUserProfileV1)
            } yield
              ProjectInfoByShortnameGetRequestV1(
                shortname = shortNameDec,
                featureFactoryConfig = featureFactoryConfig,
                userProfileV1 = Some(userProfile)
              )
          } else { // identify project by iri. this is the default case.
            val checkedProjectIri =
              stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
            for {
              userProfile <- getUserADM(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              ).map(_.asUserProfileV1)
            } yield
              ProjectInfoByIRIGetRequestV1(
                iri = checkedProjectIri,
                featureFactoryConfig = featureFactoryConfig,
                userProfileV1 = Some(userProfile)
              )
          }

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessage,
            requestContext,
            settings,
            responderManager,
            log
          )
        }
      }
    }
  }
}
