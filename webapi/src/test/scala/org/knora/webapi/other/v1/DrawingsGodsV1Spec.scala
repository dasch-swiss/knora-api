/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.other.v1

import java.util.UUID

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.permissionmessages.{DefaultObjectAccessPermissionsStringForPropertyGetV1, DefaultObjectAccessPermissionsStringForResourceClassGetV1, DefaultObjectAccessPermissionsStringResponseV1}
import org.knora.webapi.messages.v1.responder.resourcemessages.{ResourceCreateRequestV1, ResourceCreateResponseV1, _}
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileByIRIGetV1, UserProfileTypeV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages.{CreateValueV1WithComment, TextValueSimpleV1, _}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, TriplestoreJsonProtocol}
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{MutableTestIri, MutableUserProfileV1}
import org.knora.webapi.{CoreSpec, LiveActorMaker, OntologyConstants, SharedAdminTestData}

import scala.concurrent.duration._

object DrawingsGodsV1Spec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Test specification for testing a complex permissions structure of the drawings-gods-project.
  */
class DrawingsGodsV1Spec extends CoreSpec(DrawingsGodsV1Spec.config) with TriplestoreJsonProtocol {

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds
    implicit val log = akka.event.Logging(system, this.getClass())

    val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_admin-data.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_ontology.ttl", name = "http://www.knora.org/ontology/drawings-gods"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1Spec/drawings-gods_data.ttl", name = "http://www.knora.org/data/drawings-gods")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    /**
      * issues:
      * - https://github.com/dhlab-basel/Knora/issues/416
      * - https://github.com/dhlab-basel/Knora/issues/610
      */
    "Using the DrawingsGods project data" should {

        val drawingsGodsProjectIri = "http://data.knora.org/projects/0105"
        val rootUserIri = "http://data.knora.org/users/root"
        val rootUser = new MutableUserProfileV1
        val ddd1UserIri = "http://rdfh.ch/users/drawings-gods-test-ddd1"
        val ddd1 = new MutableUserProfileV1
        val ddd2UserIri = "http://rdfh.ch/users/drawings-gods-test-ddd2"
        val ddd2 = new MutableUserProfileV1
        val testPass = "test"
        val thingIri = new MutableTestIri
        val firstValueIri = new MutableTestIri
        val secondValueIri = new MutableTestIri

        "retrieve the drawings gods user's profile" in {
            responderManager ! UserProfileByIRIGetV1(rootUserIri, UserProfileTypeV1.FULL)
            rootUser.set(expectMsgType[Option[UserProfileV1]](timeout).get)

            responderManager ! UserProfileByIRIGetV1(ddd1UserIri, UserProfileTypeV1.FULL)
            ddd1.set(expectMsgType[Option[UserProfileV1]](timeout).get)

            responderManager ! UserProfileByIRIGetV1(ddd2UserIri, UserProfileTypeV1.FULL)
            ddd2.set(expectMsgType[Option[UserProfileV1]](timeout).get)
        }

        "return correct drawings-gods:QualityData resource permissions string for drawings-gods-test-ddd2 user" in {
            val qualityDataResourceClass = "http://www.knora.org/ontology/0105#QualityData"
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(drawingsGodsProjectIri, qualityDataResourceClass, ddd2.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR http://rdfh.ch/groups/drawings-gods-admin|D http://rdfh.ch/groups/drawings-gods-snf-team,knora-base:Creator|M http://rdfh.ch/groups/drawings-gods-meta-annotators,http://rdfh.ch/groups/drawings-gods-add-drawings"))
        }

        "return correct drawings-gods:Person resource class permissions string for drawings-gods-test-ddd1 user" in {
            val personResourceClass = "http://www.knora.org/ontology/drawings-gods#Person"
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(drawingsGodsProjectIri, personResourceClass, ddd1.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR http://rdfh.ch/groups/drawings-gods-admin|D http://rdfh.ch/groups/drawings-gods-snf-team,knora-base:Creator|M http://rdfh.ch/groups/drawings-gods-meta-annotators,http://rdfh.ch/groups/drawings-gods-add-drawings|V knora-base:KnownUser,knora-base:UnknownUser,knora-base:ProjectMember"))
        }

        "return correct drawings-gods:hasLastname property permissions string for drawings-gods-test-ddd1 user" in {
            val personResourceClass = "http://www.knora.org/ontology/drawings-gods#Person"
            val hasLastnameProperty = "http://www.knora.org/ontology/drawings-gods#hasLastname"
            responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetV1(drawingsGodsProjectIri, personResourceClass, hasLastnameProperty, ddd1.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR http://rdfh.ch/groups/drawings-gods-admin|D http://rdfh.ch/groups/drawings-gods-snf-team"))
        }

        "return correct drawings-gods:DrawingPublic / knora-base:hasStillImageFileValue combination permissions string for drawings-gods-test-ddd1 user" in {
            val drawingPublicResourceClass = "http://www.knora.org/ontology/drawings-gods#DrawingPublic"
            val hasStillImageFileValue = OntologyConstants.KnoraBase.HasStillImageFileValue
            responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetV1(drawingsGodsProjectIri, drawingPublicResourceClass, hasStillImageFileValue, ddd1.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR http://rdfh.ch/groups/drawings-gods-admin|D http://rdfh.ch/groups/drawings-gods-snf-team|M http://rdfh.ch/groups/drawings-gods-add-drawings|V knora-base:KnownUser,knora-base:UnknownUser,knora-base:ProjectMember,http://rdfh.ch/groups/drawings-gods-meta-annotators"))
        }

        "return correct drawings-gods:DrawingPrivate / knora-base:hasStillImageFileValue combination permissions string for drawings-gods-test-ddd1 user" in {
            val drawingPrivateResourceClass = "http://www.knora.org/ontology/drawings-gods#DrawingPrivate"
            val hasStillImageFileValue = OntologyConstants.KnoraBase.HasStillImageFileValue
            responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetV1(drawingsGodsProjectIri, drawingPrivateResourceClass, hasStillImageFileValue, ddd1.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR http://rdfh.ch/groups/drawings-gods-admin|D http://rdfh.ch/groups/drawings-gods-snf-team|M http://rdfh.ch/groups/drawings-gods-add-drawings,http://rdfh.ch/groups/drawings-gods-meta-annotators|V knora-base:ProjectMember"))
        }

        "allow drawings-gods-test-ddd1 user to create a resource, then query it and see its label and properties" in {

            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/drawings-gods#hasLastname" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("PersonTest DDD1"))),
                "http://www.knora.org/ontology/drawings-gods#hasCodePerson" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("Code"))),
                "http://www.knora.org/ontology/drawings-gods#hasPersonGender" -> Vector(CreateValueV1WithComment(HierarchicalListValueV1("http://data.knora.org/lists/drawings-gods-2016-list-FiguresHList-polysexual"))),
                "http://www.knora.org/ontology/drawings-gods#hasDrawingChildTotal" -> Vector(CreateValueV1WithComment(IntegerValueV1(99)))
            )

            responderManager ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/drawings-gods#Person",
                label = "Test-Person",
                projectIri = drawingsGodsProjectIri,
                values = valuesToBeCreated,
                file = None,
                userProfile = ddd1.get,
                apiRequestID = UUID.randomUUID
            )

            val createResponse = expectMsgType[ResourceCreateResponseV1](timeout)
            val resourceIri = createResponse.res_id

            responderManager ! ResourceFullGetRequestV1(iri = resourceIri, userProfile = ddd1.get)

            val getResponse = expectMsgType[ResourceFullResponseV1](timeout)

            val maybeLabel: Option[String] = getResponse.resinfo.get.firstproperty
            assert(maybeLabel.isDefined, "Response returned no resource label")
            assert(maybeLabel.get == "Test-Person")

            val maybeLastNameProp: Option[PropertyV1] = getResponse.props.get.properties.find(prop => prop.pid == "http://www.knora.org/ontology/drawings-gods#hasLastname")
            assert(maybeLastNameProp.isDefined, "Response returned no property hasLastname")
            assert(maybeLastNameProp.get.values.head.asInstanceOf[TextValueV1].utf8str == "PersonTest DDD1")
        }

        "allow root user to create a resource" in {

            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/drawings-gods#hasLastname" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("PersonTest DDD1"))),
                "http://www.knora.org/ontology/drawings-gods#hasCodePerson" -> Vector(CreateValueV1WithComment(TextValueSimpleV1("Code"))),
                "http://www.knora.org/ontology/drawings-gods#hasPersonGender" -> Vector(CreateValueV1WithComment(HierarchicalListValueV1("http://data.knora.org/lists/drawings-gods-2016-list-FiguresHList-polysexual"))),
                "http://www.knora.org/ontology/drawings-gods#hasDrawingChildTotal" -> Vector(CreateValueV1WithComment(IntegerValueV1(99)))
            )

            responderManager ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/drawings-gods#Person",
                label = "Test-Person",
                projectIri = drawingsGodsProjectIri,
                values = valuesToBeCreated,
                file = None,
                userProfile = rootUser.get,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ResourceCreateResponseV1](timeout)
        }
    }
}
