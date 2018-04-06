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

package org.knora.webapi.messages.v2.responder.standoffmessages

import java.util.UUID

import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{KnoraJsonLDRequestReaderV2, KnoraRequestV2, KnoraResponseV2}
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDObject, JsonLDValue}
import org.knora.webapi.{ApiV2Schema, IRI, OntologyConstants, SettingsImpl}

/**
  * An abstract trait representing a Knora v2 API request message that can be sent to `StandoffResponderV2`.
  */
sealed trait StandoffResponderRequestV2 extends KnoraRequestV2

/**
  * Represents a request to create a mapping between XML elements and attributes and standoff classes and properties.
  * A successful response will be a [[CreateMappingResponseV2]].
  *
  * @param xml         the mapping in XML.
  * @param projectIri  the IRI of the project the mapping belongs to.
  * @param mappingName the name of the mapping to be created.
  * @param userProfile the profile of the user making the request.
  */
case class CreateMappingRequestV2(xml: String, label: String, projectIri: SmartIri, mappingName: String, userProfile: UserProfileV1, apiRequestID: UUID) extends StandoffResponderRequestV2

object CreateMappingRequestV2 extends KnoraJsonLDRequestReaderV2[CreateMappingRequestV2] {
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            userProfile: UserProfileV1): CreateMappingRequestV2 = {

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val label: String = jsonLDDocument.requireString(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)

        val projectIri: SmartIri = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject, stringFormatter.toSmartIriWithErr)

        val mappingName: String = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.MappingHasName, stringFormatter.toSparqlEncodedString)

        CreateMappingRequestV2(
            xml = "", // TODO: get xml from multipart request
            label = label,
            projectIri = projectIri,
            mappingName = mappingName,
            userProfile = userProfile,
            apiRequestID = apiRequestID
        )
    }
}



/**
  * Provides the IRI of the created mapping.
  *
  * @param mappingIri the IRI of the resource (knora-base:XMLToStandoffMapping) representing the mapping that has been created.
  */
case class CreateMappingResponseV2(mappingIri: IRI) extends KnoraResponseV2 {
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {

        // TODO: implement this
        JsonLDDocument(JsonLDObject(Map.empty[IRI, JsonLDValue]), JsonLDObject(Map.empty[IRI, JsonLDValue]))
    }
}

/**
  * Represents an API request to create a mapping.
  *
  * @param project_id  the project in which the mapping is to be added.
  * @param label       the label describing the mapping.
  * @param mappingName the name of the mapping (will be appended to the mapping IRI).
  */
case class CreateMappingApiRequestV2(project_id: IRI, label: String, mappingName: String) {

}

