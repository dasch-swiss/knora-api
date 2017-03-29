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

package org.knora.webapi.responders.v2

import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourceV2, ResourcesGetRequestV2, ResourcesResponseV2}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import akka.pattern._
import org.knora.webapi.util.ConstructResponseUtilV2
import org.knora.webapi.util.ConstructResponseUtilV2.ResourcesAndValueObjects

import scala.concurrent.Future

class ResourcesResponderV2 extends Responder {

    def receive = {
        case resourcesGetRequest: ResourcesGetRequestV2 => future2Message(sender(), getResources(resourcesGetRequest.resourceIris), log)
    }

    private def getResources(resourceIris: Seq[IRI]): Future[ResourcesResponseV2] = {

        // TODO: get all the resources
        val resourceIri = resourceIris.head

        for {
            resourceRequestSparql <- Future(queries.sparql.v2.txt.getResourcePropertiesAndValues(
                triplestore = settings.triplestoreType,
                resourceIri = resourceIri
            ).toString())

            resourceRequestResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(resourceRequestSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: ResourcesAndValueObjects = ConstructResponseUtilV2.splitResourcesAndValueObjects(constructQueryResults = resourceRequestResponse)

            // there should be exactly one resource
            _ = if (queryResultsSeparated.resources.size != 1) throw InconsistentTriplestoreDataException("there was expected to be exactly one resource in the results")

            rdfLabel = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(
                subjectIri = resourceIri,
                predicate = OntologyConstants.Rdfs.Label,
                assertions = queryResultsSeparated.resources.getOrElse(resourceIri, throw InconsistentTriplestoreDataException(s"no assertions returned for $resourceIri"))
            )

            resourceClass = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(
                subjectIri = resourceIri,
                predicate = OntologyConstants.Rdf.Type,
                assertions = queryResultsSeparated.resources.getOrElse(resourceIri, throw InconsistentTriplestoreDataException(s"no assertions returned for $resourceIri"))
            )



        } yield ResourcesResponseV2(resources = Vector(ResourceV2(label = rdfLabel, resourceClass = resourceClass)))

    }

}

