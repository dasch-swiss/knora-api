/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
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

package org.knora.webapi.messages.v2.responder.metadatamessages

import java.util.UUID

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder._

/**
 * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
 */
sealed trait MetadataResponderRequestV2 extends KnoraRequestV2 {
    /**
     * The user that made the request.
     */
    def requestingUser: UserADM
}

/**
 * Requests metadata about a project. A successful response will be a [[MetadataGetResponseV2]].
 *
 * @param projectADM     the project for which metadata is requested.
 * @param requestingUser the user making the request.
 */
case class MetadataGetRequestV2(projectADM: ProjectADM,
                                requestingUser: UserADM) extends MetadataResponderRequestV2

/**
 * Represents metadata about a project.
 *
 * @param turtle project metadata in Turtle format.
 */
case class MetadataGetResponseV2(turtle: String) extends KnoraTurtleResponseV2

/**
 * A request to create or update metadata about a project. If metadata already exists
 * for the project, it will be replaced by the metadata in this message. A successful response
 * will be a [[SuccessResponseV2]].
 *
 * @param turtle         the project metadata to be stored.
 * @param projectADM     the project.
 * @param requestingUser the user making the request.
 * @param apiRequestID   the API request ID.
 */
case class MetadataPutRequestV2(turtle: String,
                                projectADM: ProjectADM,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends MetadataResponderRequestV2
