/*
 * Copyright © 2015-2020 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.http.version.versioninfo

/** This object was generated by //tools/version_info. */
case object VersionInfo {

  val name: String = "webapi"

  val webapiVersion: String = "{BUILD_TAG}"

  val scalaVersion: String = "{SCALA_VERSION}"

  val akkaHttpVersion: String = "{AKKA_HTTP_VERSION}"

  val sipiVersion: String = "{SIPI_VERSION}"

  val jenaFusekiVersion = "{FUSEKI_VERSION}"

  override val toString: String = {
    "name: %s, version: %s, scalaVersion: %s, akkaHttpVersion: %s, sipiVersion: %s, jenaFusekiVersion: %s".format(
      name,
      webapiVersion,
      scalaVersion,
      akkaHttpVersion,
      sipiVersion,
      jenaFusekiVersion
    )
  }
}
