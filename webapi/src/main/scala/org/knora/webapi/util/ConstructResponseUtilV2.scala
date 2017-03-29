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

import org.knora.webapi.messages.v1.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.v2.responder.{ResourceRowV2, ValueRowV2}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}

object ConstructResponseUtilV2 {

    case class ResourcesAndValueObjects(resources: Map[IRI, Seq[(IRI, String)]], valueObjects: Map[IRI, Seq[(IRI, String)]])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value objects.
      * This method splits the results by their type and returns them as separate structures.
      *
      * @param constructQueryResults the results of a SPARQL construct query.
      * @return a [[ResourcesAndValueObjects]].
      */
    def splitResourcesAndValueObjects(constructQueryResults: SparqlConstructResponse): ResourcesAndValueObjects = {

        val (valueObjects: Map[IRI, Seq[(IRI, String)]], resources: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {
            case (subject: IRI, assertions: Seq[(IRI, String)]) =>

                // group assertions by predicate (assertions is a sequence of 2 tuples: pred, obj)
                val groupedByPredicate: Map[IRI, Seq[(IRI, String)]] = assertions.groupBy {
                    case (pred: IRI, obj: String) =>
                        pred
                }

                val rdfType = getObjectForUniquePredicateFromAssertions(subject, OntologyConstants.Rdf.Type, assertions)

                // returns true if it is a valueObject, false in case of a resource
                OntologyConstants.KnoraBase.ValueClasses.contains(rdfType)

        }

        ResourcesAndValueObjects(resources = resources, valueObjects = valueObjects)

    }

    /**
      * Gets the value (object) for the given predicate.
      * This method assumes that there is only one value (object) for the given predicate.
      *
      * @param subjectIri the subject the assertions belong to.
      * @param predicate the predicate whose value should be returned.
      * @param assertions the assertions to search in.
      * @return the given predicate's value (object).
      */
    def getObjectForUniquePredicateFromAssertions(subjectIri: IRI, predicate: IRI, assertions: Seq[(IRI, String)]): String = {

        // make predicate the keys of a map
        val predicateMap: Map[IRI, String] = assertions.toMap

        // get the assertion representing the predicate
        predicateMap.getOrElse(predicate, throw InconsistentTriplestoreDataException(s"no $predicate given for $subjectIri"))

    }

    /**
      * Gets the predicate for the given value (object).
      * cThis method assumes that the given value (object) is unique in the assertions (no combination of the same value with another predicate).
      *
      * @param subjectIri the subject the assertions belong to.
      * @param objectValue the object (value) whose predicate should be returned.
      * @param assertions the assertions to search in.
      * @return the given predicate for the given object (value).
      */
    def getPredicateForUniqueObjectFromAssertions(subjectIri: IRI, objectValue: String, assertions: Seq[(IRI, String)]) = {

        // make the objects keys of a map
        val objMap = assertions.map {
            case (pred, obj) =>
                (obj, pred)
        }.toMap

        objMap.getOrElse(objectValue, throw InconsistentTriplestoreDataException(s"no object (value) $objectValue found in assertions for $subjectIri"))

    }

    def createResponseForResources(queryResultsSeparated: ResourcesAndValueObjects): Seq[ResourceRowV2] = {

        val resourceResultRows: Seq[ResourceRowV2] = queryResultsSeparated.resources.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                val rdfLabel = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(subjectIri = resourceIri, predicate = OntologyConstants.Rdfs.Label, assertions = assertions)

                val resourceClass = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(subjectIri = resourceIri, predicate = OntologyConstants.Rdf.Type, assertions = assertions)

                // get all the objects from the assertions
                val objects: Seq[String] = assertions.map {
                    case (pred, obj) =>
                        obj
                }

                // check if one or more of the objects points to a value object
                val valueObjectIris: Set[IRI] = queryResultsSeparated.valueObjects.keySet.intersect(objects.toSet)

                ResourceRowV2(
                    resourceIri = resourceIri,
                    resourceClass = resourceClass,
                    label = rdfLabel,
                    valueObjects = valueObjectIris.map {
                        (valObj: IRI) =>

                            val valueObjectClass = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(subjectIri = valObj, predicate = OntologyConstants.Rdf.Type, assertions = queryResultsSeparated.valueObjects.
                                getOrElse(valObj, throw InconsistentTriplestoreDataException(s"value object not found $valObj")))



                            val valueObjectValueHasString: String = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(subjectIri = valObj, predicate = OntologyConstants.KnoraBase.ValueHasString, assertions = queryResultsSeparated.valueObjects.
                                getOrElse(valObj, throw InconsistentTriplestoreDataException(s"value object not found $valObj")))

                            val value = valueObjectClass match {
                                case OntologyConstants.KnoraBase.TextValue =>
                                    val textValue = ConstructResponseUtilV2.getObjectForUniquePredicateFromAssertions(subjectIri = valObj, predicate = OntologyConstants.KnoraBase.ValueHasString, assertions = queryResultsSeparated.valueObjects.
                                        getOrElse(valObj, throw InconsistentTriplestoreDataException(s"value object not found $valObj")))
                                    Map(OntologyConstants.KnoraBase.ValueHasString -> textValue)

                                // TODO: implement all value object classes
                                case other =>
                                    Map(other -> "not yet implemented")

                            }

                            // get the property that points from the resource to the value object
                            val propertyIri = ConstructResponseUtilV2.getPredicateForUniqueObjectFromAssertions(subjectIri = resourceIri, objectValue = valObj, assertions)

                            ValueRowV2(
                                valueClass = valueObjectClass,
                                value = value,
                                valueObjectIri = valObj,
                                propertyIri = propertyIri
                            )
                    }.toVector
                )
        }.toVector

        resourceResultRows

    }
    
}