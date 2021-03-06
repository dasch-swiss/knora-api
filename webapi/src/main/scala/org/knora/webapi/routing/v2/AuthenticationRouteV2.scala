/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.{
  AuthenticationV2JsonProtocol,
  KnoraPasswordCredentialsV2,
  LoginApiRequestPayloadV2
}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData}

/**
  * A route providing API v2 authentication support. It allows the creation of "sessions", which are used in the SALSAH app.
  */
class AuthenticationRouteV2(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with AuthenticationV2JsonProtocol {

  /**
    * Returns the route.
    */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {

    path("v2" / "authentication") {
      get { // authenticate credentials
        requestContext =>
          {
            requestContext.complete {
              doAuthenticateV2(
                requestContext = requestContext,
                featureFactoryConfig = featureFactoryConfig
              )
            }
          }
      } ~
        post { // login
          /* send iri, email, or username, and password in body as:
           * {
           *   "iri|username|email": "value_of_iri_username_or_email",
           *   "password": "userspassword"
           * }, e.g., for email:
           * {
           *   "email": "email@example.com",
           *   "password": "userspassword"
           * }
           *
           * Returns a JWT token (and session cookie), which can be supplied with every request thereafter in
           * the authorization header with the bearer scheme: 'Authorization: Bearer abc.def.ghi'
           */
          entity(as[LoginApiRequestPayloadV2]) { apiRequest => requestContext =>
            requestContext.complete {
              doLoginV2(
                credentials = KnoraPasswordCredentialsV2(
                  UserIdentifierADM(
                    maybeIri = apiRequest.iri,
                    maybeEmail = apiRequest.email,
                    maybeUsername = apiRequest.username
                  ),
                  password = apiRequest.password
                ),
                featureFactoryConfig = featureFactoryConfig
              )
            }
          }
        } ~
        delete { // logout
          requestContext =>
            requestContext.complete {
              doLogoutV2(requestContext)
            }
        }
    } ~
      path("v2" / "login") {
        get { // html login interface (necessary for IIIF Authentication API support)
          requestContext =>
            {
              requestContext.complete {
                presentLoginFormV2(requestContext)
              }
            }
        } ~
          post { // called by html login interface (necessary for IIIF Authentication API support)
            formFields('username, 'password) { (username, password) => requestContext =>
              {
                requestContext.complete {
                  doLoginV2(
                    credentials = KnoraPasswordCredentialsV2(
                      UserIdentifierADM(
                        maybeUsername = Some(username)
                      ),
                      password = password
                    ),
                    featureFactoryConfig = featureFactoryConfig
                  )
                }
              }
            }
          }
      }
  }
}
