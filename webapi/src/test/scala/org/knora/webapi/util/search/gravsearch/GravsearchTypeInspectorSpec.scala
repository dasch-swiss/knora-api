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

package org.knora.webapi.util.search.gravsearch

import akka.actor.{ActorSelection, Props}
import akka.testkit.ImplicitSender
import org.knora.webapi.messages.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, RESPONDER_MANAGER_ACTOR_PATH, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.search._
import org.knora.webapi.{CoreSpec, KnoraSystemInstances, LiveActorMaker, SharedTestDataADM}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Tests Gravsearch type inspection.
  */
class GravsearchTypeInspectorSpec extends CoreSpec()  with ImplicitSender {
    val searchParserV2Spec = new GravsearchParserSpec

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)
    private val anythingAdminUser = SharedTestDataADM.anythingAdminUser

    private implicit val ec: ExecutionContextExecutor = system.dispatcher

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser)
        expectMsgType[SuccessResponseV2](10.seconds)
    }

    "The type inspection runner" should {
        "remove the type annotations from a WHERE clause" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = false)
            val parsedQuery = GravsearchParser.parseQuery(searchParserV2Spec.QueryWithExplicitTypeAnnotations)
            val whereClauseWithoutAnnotations = typeInspectionRunner.removeTypeAnnotations(parsedQuery.whereClause)
            whereClauseWithoutAnnotations should ===(whereClauseWithoutAnnotations)
        }

    }

    "The explicit type inspector" should {
        "get type information from a simple query" in {
            val typeInspectionRunner = new GravsearchTypeInspectionRunner(system = system, inferTypes = false)
            val parsedQuery = GravsearchParser.parseQuery(searchParserV2Spec.QueryWithExplicitTypeAnnotations)
            val typeInspectionResult: Future[GravsearchTypeInspectionResult] = typeInspectionRunner.inspectTypes(parsedQuery.whereClause, requestingUser = anythingAdminUser)

            typeInspectionResult.map {
                result => assert(result == SimpleTypeInspectionResult)
            }
        }
    }

    val SimpleTypeInspectionResult = GravsearchTypeInspectionResult(typedEntities = Map(
        TypeableVariable(variableName = "linkingProp1") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "letter") -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableVariable(variableName = "linkingProp2") -> PropertyTypeInfo(objectTypeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        TypeableIri(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri) -> NonPropertyTypeInfo(typeIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
    ))

    val WhereClauseWithoutAnnotations = WhereClause(patterns = Vector(
        StatementPattern(
            obj = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            subj = QueryVariable(variableName = "letter")
        ),
        StatementPattern(
            obj = IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri),
            pred = QueryVariable(variableName = "linkingProp1"),
            subj = QueryVariable(variableName = "letter")
        ),
        StatementPattern(
            obj = IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri),
            pred = QueryVariable(variableName = "linkingProp2"),
            subj = QueryVariable(variableName = "letter")
        ),
        FilterPattern(expression = OrExpression(
            rightArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp2")
            ),
            leftArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp2")
            )
        )),
        FilterPattern(expression = OrExpression(
            rightArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp1")
            ),
            leftArg = CompareExpression(
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "linkingProp1")
            )
        ))
    ))
}
