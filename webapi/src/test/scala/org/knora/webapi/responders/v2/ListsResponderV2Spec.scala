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


import akka.actor.Props
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listadminmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v2.responder.listmessages._
import org.knora.webapi.responders._
import org.knora.webapi.responders.admin.ListsAdminResponder
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{MessageUtil, MutableTestIri}

import scala.concurrent.duration._


/**
  * Static data for testing [[ListsAdminResponder]].
  */
object ListsResponderV2Spec {
    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Tests [[ListsAdminResponder]].
  */
class ListsResponderV2Spec extends CoreSpec(ListsResponderV2Spec.config) with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ListsAdminResponder]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val log = akka.event.Logging(system, this.getClass())

    // The default timeout for receiving reply messages from actors.
    implicit val timeout = 5.seconds

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    // A test UserProfileV1.
    private val userProfile = SharedAdminTestData.incunabulaProjectAdminUser

    // A test UserDataV1.
    private val userData = userProfile.userData


    private val keywordRootNode = ListRootNode (
        id = "http://data.knora.org/lists/73d0ec0302",
        projectIri = Some("http://data.knora.org/projects/images"),
        labels = Seq(StringV2("Title", Some("en")), StringV2("Titel", Some("de")), StringV2("Titre", Some("fr"))),
        comments = Seq(StringV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de"))),
        children = Seq.empty[ListChildNode]
    )

    private val keywordChildNodes: Seq[ListChildNode] = Seq.empty[ListChildNode]

    private val keywordList: ListNode = ListRootNode(
            children = Vector(
                ListChildNode(
                    position = Some(0),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "BIBLIOTHEKEN ST. MORITZ"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/412821d3a6"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SAMMELB\u00C4NDE, FOTOALBEN"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/da5b740ca7"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "ALLGEMEINES"
                    )),
                    name = Some("1ALL"),
                    id = "http://data.knora.org/lists/a8f4cd99a6"
                ),
                ListChildNode(
                    position = Some(1),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Vector(ListChildNode(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringV2(
                                    language = None,
                                    value = "Personen"
                                )),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/a5f66db8a7"
                            )),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SCHWEIZ"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/0cc31a7fa7"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(ListChildNode(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringV2(
                                    language = None,
                                    value = "Personen"
                                )),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/d75d142ba8"
                            )),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "GRAUB\u00DCNDEN"
                            )),
                            name = Some("2"),
                            id = "http://data.knora.org/lists/3e2ac1f1a7"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Flugaufnahmen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/09c5ba9da8"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaft Sommer ohne Ortschaften"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/a2f80dd7a8"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaft Sommer mit Ortschaften"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/3b2c6110a9"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaft Winter ohne Ortschaften"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/d45fb449a9"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaft Winter mit Ortschaften"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/6d930783a9"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaft Seen"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/06c75abca9"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaft Berge"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/9ffaadf5a9"
                                ),
                                ListChildNode(
                                    position = Some(7),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Maloja"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/d1615468aa"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Sils"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/6a95a7a1aa"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Silvaplana"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/03c9fadaaa"
                                        ),
                                        ListChildNode(
                                            position = Some(3),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Surlej"
                                            )),
                                            name = Some("4"),
                                            id = "http://data.knora.org/lists/9cfc4d14ab"
                                        ),
                                        ListChildNode(
                                            position = Some(4),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Champf\u00E8r"
                                            )),
                                            name = Some("5"),
                                            id = "http://data.knora.org/lists/3530a14dab"
                                        ),
                                        ListChildNode(
                                            position = Some(5),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Pontresina"
                                            )),
                                            name = Some("6"),
                                            id = "http://data.knora.org/lists/ce63f486ab"
                                        ),
                                        ListChildNode(
                                            position = Some(6),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Celerina"
                                            )),
                                            name = Some("7"),
                                            id = "http://data.knora.org/lists/679747c0ab"
                                        ),
                                        ListChildNode(
                                            position = Some(7),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Samedan"
                                            )),
                                            name = Some("8"),
                                            id = "http://data.knora.org/lists/00cb9af9ab"
                                        ),
                                        ListChildNode(
                                            position = Some(8),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Bever"
                                            )),
                                            name = Some("9"),
                                            id = "http://data.knora.org/lists/99feed32ac"
                                        ),
                                        ListChildNode(
                                            position = Some(9),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "La Punt"
                                            )),
                                            name = Some("10"),
                                            id = "http://data.knora.org/lists/3232416cac"
                                        ),
                                        ListChildNode(
                                            position = Some(10),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Chamues-ch"
                                            )),
                                            name = Some("11"),
                                            id = "http://data.knora.org/lists/cb6594a5ac"
                                        ),
                                        ListChildNode(
                                            position = Some(11),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Madulain"
                                            )),
                                            name = Some("12"),
                                            id = "http://data.knora.org/lists/6499e7deac"
                                        ),
                                        ListChildNode(
                                            position = Some(12),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Zuoz"
                                            )),
                                            name = Some("13"),
                                            id = "http://data.knora.org/lists/fdcc3a18ad"
                                        ),
                                        ListChildNode(
                                            position = Some(13),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "S-chanf"
                                            )),
                                            name = Some("14"),
                                            id = "http://data.knora.org/lists/96008e51ad"
                                        ),
                                        ListChildNode(
                                            position = Some(14),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Cinous-chel"
                                            )),
                                            name = Some("15"),
                                            id = "http://data.knora.org/lists/2f34e18aad"
                                        ),
                                        ListChildNode(
                                            position = Some(15),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Fex"
                                            )),
                                            name = Some("16"),
                                            id = "http://data.knora.org/lists/c86734c4ad"
                                        ),
                                        ListChildNode(
                                            position = Some(16),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Unterengadin"
                                            )),
                                            name = Some("17"),
                                            id = "http://data.knora.org/lists/619b87fdad"
                                        ),
                                        ListChildNode(
                                            position = Some(17),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen"
                                            )),
                                            name = Some("18"),
                                            id = "http://data.knora.org/lists/faceda36ae"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ortschaften Sommer"
                                    )),
                                    name = Some("8"),
                                    id = "http://data.knora.org/lists/382e012faa"
                                ),
                                ListChildNode(
                                    position = Some(8),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Maloja"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/2c3681a9ae"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Sils"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/c569d4e2ae"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Silvaplana"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/5e9d271caf"
                                        ),
                                        ListChildNode(
                                            position = Some(3),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Surlej"
                                            )),
                                            name = Some("4"),
                                            id = "http://data.knora.org/lists/f7d07a55af"
                                        ),
                                        ListChildNode(
                                            position = Some(4),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Champf\u00E8r"
                                            )),
                                            name = Some("5"),
                                            id = "http://data.knora.org/lists/9004ce8eaf"
                                        ),
                                        ListChildNode(
                                            position = Some(5),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Pontresina"
                                            )),
                                            name = Some("6"),
                                            id = "http://data.knora.org/lists/293821c8af"
                                        ),
                                        ListChildNode(
                                            position = Some(6),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Celerina"
                                            )),
                                            name = Some("7"),
                                            id = "http://data.knora.org/lists/c26b7401b0"
                                        ),
                                        ListChildNode(
                                            position = Some(7),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Samedan"
                                            )),
                                            name = Some("8"),
                                            id = "http://data.knora.org/lists/5b9fc73ab0"
                                        ),
                                        ListChildNode(
                                            position = Some(8),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Bever"
                                            )),
                                            name = Some("9"),
                                            id = "http://data.knora.org/lists/f4d21a74b0"
                                        ),
                                        ListChildNode(
                                            position = Some(9),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "La Punt"
                                            )),
                                            name = Some("10"),
                                            id = "http://data.knora.org/lists/8d066eadb0"
                                        ),
                                        ListChildNode(
                                            position = Some(10),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Chamues-ch"
                                            )),
                                            name = Some("11"),
                                            id = "http://data.knora.org/lists/263ac1e6b0"
                                        ),
                                        ListChildNode(
                                            position = Some(11),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Madulain"
                                            )),
                                            name = Some("12"),
                                            id = "http://data.knora.org/lists/bf6d1420b1"
                                        ),
                                        ListChildNode(
                                            position = Some(12),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Zuoz"
                                            )),
                                            name = Some("13"),
                                            id = "http://data.knora.org/lists/58a16759b1"
                                        ),
                                        ListChildNode(
                                            position = Some(13),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "S-chanf"
                                            )),
                                            name = Some("14"),
                                            id = "http://data.knora.org/lists/f1d4ba92b1"
                                        ),
                                        ListChildNode(
                                            position = Some(14),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Cinous-chel"
                                            )),
                                            name = Some("15"),
                                            id = "http://data.knora.org/lists/8a080eccb1"
                                        ),
                                        ListChildNode(
                                            position = Some(15),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Fex"
                                            )),
                                            name = Some("16"),
                                            id = "http://data.knora.org/lists/233c6105b2"
                                        ),
                                        ListChildNode(
                                            position = Some(16),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Unterengadin"
                                            )),
                                            name = Some("17"),
                                            id = "http://data.knora.org/lists/bc6fb43eb2"
                                        ),
                                        ListChildNode(
                                            position = Some(17),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen"
                                            )),
                                            name = Some("18"),
                                            id = "http://data.knora.org/lists/55a30778b2"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ortschaften Winter"
                                    )),
                                    name = Some("9"),
                                    id = "http://data.knora.org/lists/93022e70ae"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ENGADIN"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/70916764a8"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Dorf und Bad Winter"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/870aaeeab2"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Dorf Sommer"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/203e0124b3"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Bad Sommer"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/b971545db3"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Denkm\u00E4ler"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/52a5a796b3"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Landschaft Sommer"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/ebd8facfb3"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Landschaft Winter"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/840c4e09b4"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "St. Moritz Schulh\u00E4user"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/1d40a142b4"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ST. MORITZ"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/eed65ab1b2"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ortschaften"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/4fa747b5b4"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landschaften"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/e8da9aeeb4"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Personen"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/45cfa1df0401"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SUEDTAELER"
                            )),
                            name = Some("5"),
                            id = "http://data.knora.org/lists/b673f47bb4"
                        ),
                        ListChildNode(
                            position = Some(5),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Landkarten"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/1a424161b5"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Panoramen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/b375949ab5"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "LANDKARTEN UND PANORAMEN"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/810eee27b5"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "GEOGRAPHIE"
                    )),
                    name = Some("2GEO"),
                    id = "http://data.knora.org/lists/738fc745a7"
                ),
                ListChildNode(
                    position = Some(2),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SCHWEIZ"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/de02f5180501"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "GRAUB\u00DCNDEN"
                            )),
                            name = Some("2"),
                            id = "http://data.knora.org/lists/773648520501"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ENGADIN"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/106a9b8b0501"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ST. MORITZ"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/a99deec40501"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen A"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/1744e17fb6"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen B"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/b07734b9b6"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen C"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/49ab87f2b6"
                                        ),
                                        ListChildNode(
                                            position = Some(3),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen D"
                                            )),
                                            name = Some("4"),
                                            id = "http://data.knora.org/lists/e2deda2bb7"
                                        ),
                                        ListChildNode(
                                            position = Some(4),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen E"
                                            )),
                                            name = Some("5"),
                                            id = "http://data.knora.org/lists/7b122e65b7"
                                        ),
                                        ListChildNode(
                                            position = Some(5),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen F"
                                            )),
                                            name = Some("6"),
                                            id = "http://data.knora.org/lists/1446819eb7"
                                        ),
                                        ListChildNode(
                                            position = Some(6),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen G"
                                            )),
                                            name = Some("7"),
                                            id = "http://data.knora.org/lists/ad79d4d7b7"
                                        ),
                                        ListChildNode(
                                            position = Some(7),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen H"
                                            )),
                                            name = Some("8"),
                                            id = "http://data.knora.org/lists/46ad2711b8"
                                        ),
                                        ListChildNode(
                                            position = Some(8),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen I"
                                            )),
                                            name = Some("9"),
                                            id = "http://data.knora.org/lists/dfe07a4ab8"
                                        ),
                                        ListChildNode(
                                            position = Some(9),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen J"
                                            )),
                                            name = Some("10"),
                                            id = "http://data.knora.org/lists/7814ce83b8"
                                        ),
                                        ListChildNode(
                                            position = Some(10),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen K"
                                            )),
                                            name = Some("11"),
                                            id = "http://data.knora.org/lists/114821bdb8"
                                        ),
                                        ListChildNode(
                                            position = Some(11),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen L"
                                            )),
                                            name = Some("12"),
                                            id = "http://data.knora.org/lists/aa7b74f6b8"
                                        ),
                                        ListChildNode(
                                            position = Some(12),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen M"
                                            )),
                                            name = Some("13"),
                                            id = "http://data.knora.org/lists/43afc72fb9"
                                        ),
                                        ListChildNode(
                                            position = Some(13),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen N"
                                            )),
                                            name = Some("14"),
                                            id = "http://data.knora.org/lists/dce21a69b9"
                                        ),
                                        ListChildNode(
                                            position = Some(14),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen O"
                                            )),
                                            name = Some("15"),
                                            id = "http://data.knora.org/lists/75166ea2b9"
                                        ),
                                        ListChildNode(
                                            position = Some(15),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen P"
                                            )),
                                            name = Some("16"),
                                            id = "http://data.knora.org/lists/0e4ac1dbb9"
                                        ),
                                        ListChildNode(
                                            position = Some(16),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen Q"
                                            )),
                                            name = Some("17"),
                                            id = "http://data.knora.org/lists/a77d1415ba"
                                        ),
                                        ListChildNode(
                                            position = Some(17),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen R"
                                            )),
                                            name = Some("18"),
                                            id = "http://data.knora.org/lists/40b1674eba"
                                        ),
                                        ListChildNode(
                                            position = Some(18),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen S"
                                            )),
                                            name = Some("19"),
                                            id = "http://data.knora.org/lists/d9e4ba87ba"
                                        ),
                                        ListChildNode(
                                            position = Some(19),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen T"
                                            )),
                                            name = Some("20"),
                                            id = "http://data.knora.org/lists/72180ec1ba"
                                        ),
                                        ListChildNode(
                                            position = Some(20),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen U"
                                            )),
                                            name = Some("21"),
                                            id = "http://data.knora.org/lists/0b4c61faba"
                                        ),
                                        ListChildNode(
                                            position = Some(21),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen V"
                                            )),
                                            name = Some("22"),
                                            id = "http://data.knora.org/lists/a47fb433bb"
                                        ),
                                        ListChildNode(
                                            position = Some(22),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen W"
                                            )),
                                            name = Some("23"),
                                            id = "http://data.knora.org/lists/3db3076dbb"
                                        ),
                                        ListChildNode(
                                            position = Some(23),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen X"
                                            )),
                                            name = Some("24"),
                                            id = "http://data.knora.org/lists/d6e65aa6bb"
                                        ),
                                        ListChildNode(
                                            position = Some(24),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen Y"
                                            )),
                                            name = Some("25"),
                                            id = "http://data.knora.org/lists/6f1aaedfbb"
                                        ),
                                        ListChildNode(
                                            position = Some(25),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen Z"
                                            )),
                                            name = Some("26"),
                                            id = "http://data.knora.org/lists/084e0119bc"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Personen A-Z"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/7e108e46b6"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Personen unbekannt"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/a1815452bc"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Gruppen Einheimische"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/3ab5a78bbc"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kinder Winter"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/d3e8fac4bc"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kinder Sommer"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/6c1c4efebc"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Sonnenbadende"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/0550a137bd"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Zuschauer"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/9e83f470bd"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "BIOGRAPHIEN"
                            )),
                            name = Some("5"),
                            id = "http://data.knora.org/lists/e5dc3a0db6"
                        ),
                        ListChildNode(
                            position = Some(5),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "WAPPEN UND FAHNEN"
                            )),
                            name = Some("7"),
                            id = "http://data.knora.org/lists/37b747aabd"
                        ),
                        ListChildNode(
                            position = Some(6),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "KRIEGE UND MILIT\u00C4R"
                            )),
                            name = Some("9"),
                            id = "http://data.knora.org/lists/d0ea9ae3bd"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "GESCHICHTE"
                    )),
                    name = Some("3GES"),
                    id = "http://data.knora.org/lists/4ca9e7d3b5"
                ),
                ListChildNode(
                    position = Some(3),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ausstellungen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/9b85948fbe"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Gem\u00E4lde"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/34b9e7c8be"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Karrikaturen und Kritik"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/cdec3a02bf"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Segantini und Museum"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/66208e3bbf"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Sgrafitti"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/ff53e174bf"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "MALEREI"
                            )),
                            name = Some("5"),
                            id = "http://data.knora.org/lists/02524156be"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kurorchester"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/31bb87e7bf"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Musik"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/caeeda20c0"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Zirkus"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/63222e5ac0"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Theater"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/fc558193c0"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Tanz"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/9589d4ccc0"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "MUSIK, THEATER UND RADIO"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/988734aebf"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Heidi Film"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/c7f07a3fc1"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Foto"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/6024ce78c1"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Film"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/f95721b2c1"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "FILM UND FOTO"
                            )),
                            name = Some("7"),
                            id = "http://data.knora.org/lists/2ebd2706c1"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Modelle"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/2bbfc724c2"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schneeskulpturen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/c4f21a5ec2"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Plastiken"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/5d266e97c2"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Stiche"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/f659c1d0c2"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Bildhauerei"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/8f8d140ac3"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kunstgewerbe"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/28c16743c3"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "BILDHAUEREI UND KUNSTGEWERBE"
                            )),
                            name = Some("8"),
                            id = "http://data.knora.org/lists/928b74ebc1"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Grafiken"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/5a280eb6c3"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Holzschnitte"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/f35b61efc3"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Plakate"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/8c8fb428c4"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ST. MORITZ GRAFIKEN UND PLAKATE"
                            )),
                            name = Some("9"),
                            id = "http://data.knora.org/lists/c1f4ba7cc3"
                        ),
                        ListChildNode(
                            position = Some(5),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Architektur / Inneneinrichtungen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/bef65a9bc4"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Pl\u00E4ne"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/572aaed4c4"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ARCHITEKTUR"
                            )),
                            name = Some("10"),
                            id = "http://data.knora.org/lists/25c30762c4"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "KUNST"
                    )),
                    name = Some("4KUN"),
                    id = "http://data.knora.org/lists/691eee1cbe"
                ),
                ListChildNode(
                    position = Some(4),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "MEDIZIN UND NATURHEILKUNDE"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/89915447c5"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Heilbad aussen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/bbf8fab9c5"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Heilbad innen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/542c4ef3c5"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "HEILBAD UND QUELLEN"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/22c5a780c5"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SPITAL UND KLINIKEN / KINDERHEIME"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/ed5fa12cc6"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "MEDIZIN"
                    )),
                    name = Some("5MED"),
                    id = "http://data.knora.org/lists/f05d010ec5"
                ),
                ListChildNode(
                    position = Some(5),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Fischen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/b8fa9ad8c6"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Jagen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/512eee11c7"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Tiere"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/ea61414bc7"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "FAUNA"
                            )),
                            name = Some("2"),
                            id = "http://data.knora.org/lists/1fc7479fc6"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Blumen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/1cc9e7bdc7"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "B\u00E4ume"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/b5fc3af7c7"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "FLORA"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/83959484c7"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "GEOLOGIE UND MINERALOGIE"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/4e308e30c8"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Gew\u00E4sser und \u00DCberschwemmungen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/809734a3c8"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Gletscher"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/19cb87dcc8"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Lawinen"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/b2feda15c9"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schnee, Raureif, Eisblumen"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/4b322e4fc9"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "KLIMATOLOGIE UND METEOROLOGIE"
                            )),
                            name = Some("5"),
                            id = "http://data.knora.org/lists/e763e169c8"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "UMWELT"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/e4658188c9"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "NATURKUNDE"
                    )),
                    name = Some("6NAT"),
                    id = "http://data.knora.org/lists/8693f465c6"
                ),
                ListChildNode(
                    position = Some(6),
                    children = Vector(ListChildNode(
                        position = Some(0),
                        children = Vector(ListChildNode(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "St. Moritz Kirchen"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/af007b34ca"
                        )),
                        comments = Nil,
                        labels = Vector(StringV2(
                            language = None,
                            value = "RELIGION UND KIRCHEN"
                        )),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/16cd27fbc9"
                    )),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "RELIGION"
                    )),
                    name = Some("7REL"),
                    id = "http://data.knora.org/lists/7d99d4c1c9"
                ),
                ListChildNode(
                    position = Some(7),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "VERFASSUNGEN UND GESETZE"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/e16721a7ca"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Wasserwirtschaft"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/13cfc719cb"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Feuer und Feuerwehr"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/ac021b53cb"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Polizei und Beh\u00F6rde"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/45366e8ccb"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Abfallbewirtschaftung"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/de69c1c5cb"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "GEMEINDEWESEN"
                            )),
                            name = Some("2"),
                            id = "http://data.knora.org/lists/7a9b74e0ca"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SCHULWESEN"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/779d14ffcb"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "B\u00E4lle und Verkleidungen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/a904bb71cc"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Chalandamarz"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/42380eabcc"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Engadiner Museum"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/db6b61e4cc"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Feste und Umz\u00FCge"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/749fb41dcd"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schlitteda"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/0dd30757cd"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Trachten"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/a6065b90cd"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "VOLKSKUNDE"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/10d16738cc"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "PARTEIEN UND GRUPPIERUNGEN"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/3f3aaec9cd"
                        ),
                        ListChildNode(
                            position = Some(5),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SCHWESTERNST\u00C4TDE"
                            )),
                            name = Some("7"),
                            id = "http://data.knora.org/lists/d86d0103ce"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "SOZIALES"
                    )),
                    name = Some("8SOZ"),
                    id = "http://data.knora.org/lists/4834ce6dca"
                ),
                ListChildNode(
                    position = Some(8),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Bridge"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/a308fbaece"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Boxen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/3c3c4ee8ce"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Camping"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/d56fa121cf"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Fechten"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/6ea3f45acf"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Fitness"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/07d74794cf"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "H\u00F6hentraining"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/a00a9bcdcf"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Krafttraining"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/393eee06d0"
                                ),
                                ListChildNode(
                                    position = Some(7),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Leichtathletik"
                                    )),
                                    name = Some("8"),
                                    id = "http://data.knora.org/lists/d2714140d0"
                                ),
                                ListChildNode(
                                    position = Some(8),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Pokale, Preise, Medallien"
                                    )),
                                    name = Some("9"),
                                    id = "http://data.knora.org/lists/6ba59479d0"
                                ),
                                ListChildNode(
                                    position = Some(9),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schiessen"
                                    )),
                                    name = Some("10"),
                                    id = "http://data.knora.org/lists/04d9e7b2d0"
                                ),
                                ListChildNode(
                                    position = Some(10),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Turnen"
                                    )),
                                    name = Some("11"),
                                    id = "http://data.knora.org/lists/9d0c3becd0"
                                ),
                                ListChildNode(
                                    position = Some(11),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Zeitmessung"
                                    )),
                                    name = Some("12"),
                                    id = "http://data.knora.org/lists/36408e25d1"
                                ),
                                ListChildNode(
                                    position = Some(12),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Hornussen"
                                    )),
                                    name = Some("13"),
                                    id = "http://data.knora.org/lists/cf73e15ed1"
                                ),
                                ListChildNode(
                                    position = Some(13),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schwingen"
                                    )),
                                    name = Some("14"),
                                    id = "http://data.knora.org/lists/68a73498d1"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SPORT"
                            )),
                            name = Some("0"),
                            id = "http://data.knora.org/lists/0ad5a775ce"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Cricket"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/9a0edb0ad2"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schlitteln"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/33422e44d2"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schneeschuhlaufen"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/cc75817dd2"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Tailing"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/65a9d4b6d2"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Wind-, Schlittenhundrennen"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/fedc27f0d2"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "WINTERSPORT"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/01db87d1d1"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Verschiedenes"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/3044ce62d3"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skiakrobatik"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/c977219cd3"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski Corvatsch"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/62ab74d5d3"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skifahren"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/fbdec70ed4"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski Kilometer-Lanc\u00E9"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/94121b48d4"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski SOS"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/2d466e81d4"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skitouren"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/c679c1bad4"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SKI"
                            )),
                            name = Some("2"),
                            id = "http://data.knora.org/lists/97107b29d3"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SKISCHULE"
                            )),
                            name = Some("2-2"),
                            id = "http://data.knora.org/lists/5fad14f4d4"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skirennen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/9114bb66d5"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski Rennpisten"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/2a480ea0d5"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Personen"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/c37b61d9d5"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Guardia Grischa"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/5cafb412d6"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski Vorweltmeisterschaft 1973"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/f5e2074cd6"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski Weltmeisterschaft 1974"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/8e165b85d6"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ski Weltmeisterschaft 2003"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/274aaebed6"
                                ),
                                ListChildNode(
                                    position = Some(7),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skispringen"
                                    )),
                                    name = Some("8"),
                                    id = "http://data.knora.org/lists/c07d01f8d6"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SKIRENNEN UND SKISPRINGEN"
                            )),
                            name = Some("2-3"),
                            id = "http://data.knora.org/lists/f8e0672dd5"
                        ),
                        ListChildNode(
                            position = Some(5),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skilanglauf"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/f2e4a76ad7"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Engadin Skimarathon"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/8b18fba3d7"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SKILANGLAUF UND ENGADIN SKIMARATHON"
                            )),
                            name = Some("2-4"),
                            id = "http://data.knora.org/lists/59b15431d7"
                        ),
                        ListChildNode(
                            position = Some(6),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "SNOWBOARD UND SNOWBOARDSCHULE"
                            )),
                            name = Some("2-5"),
                            id = "http://data.knora.org/lists/244c4eddd7"
                        ),
                        ListChildNode(
                            position = Some(7),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Olympiade 1928"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/56b3f44fd8"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Olympiade 1948"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/efe64789d8"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "OLYMPIADEN"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/bd7fa116d8"
                        ),
                        ListChildNode(
                            position = Some(8),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Eishockey und Bandy"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/214eeefbd8"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Gefrorene Seen"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/53b5946ed9"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Gymkhana"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/ece8e7a7d9"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Eisrevue"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/851c3be1d9"
                                        ),
                                        ListChildNode(
                                            position = Some(3),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Paarlauf"
                                            )),
                                            name = Some("4"),
                                            id = "http://data.knora.org/lists/1e508e1ada"
                                        ),
                                        ListChildNode(
                                            position = Some(4),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Schnellauf"
                                            )),
                                            name = Some("5"),
                                            id = "http://data.knora.org/lists/b783e153da"
                                        ),
                                        ListChildNode(
                                            position = Some(5),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Kellner auf Eis"
                                            )),
                                            name = Some("6"),
                                            id = "http://data.knora.org/lists/50b7348dda"
                                        ),
                                        ListChildNode(
                                            position = Some(6),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen"
                                            )),
                                            name = Some("7"),
                                            id = "http://data.knora.org/lists/e9ea87c6da"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Eislaufen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/ba814135d9"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Eissegeln, -Surfen"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/821edbffda"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Eisstadion"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/1b522e39db"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Vector(ListChildNode(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/4db9d4abdb"
                                    )),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Curling"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/b4858172db"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Eisstockschiessen"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/e6ec27e5db"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kunsteisbahn Ludains"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/7f207b1edc"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "EISSPORT"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/881a9bc2d8"
                        ),
                        ListChildNode(
                            position = Some(9),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/4abb74cadc"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "St\u00FCrze"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/e3eec703dd"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Bau"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/7c221b3ddd"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Bob Run"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/b1872191dc"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/ae89c1afdd"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Bau"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/47bd14e9dd"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Cresta Run"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/15566e76dd"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Rodeln"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/42d141fe0501"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "CRESTA RUN UND BOB"
                            )),
                            name = Some("5"),
                            id = "http://data.knora.org/lists/1854ce57dc"
                        ),
                        ListChildNode(
                            position = Some(10),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Concours Hippique"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/7924bb5bde"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Pferderennen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/12580e95de"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Polo"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/ab8b61cede"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Reiten"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/44bfb407df"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Reithalle"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/ddf20741df"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skikj\u00F6ring"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/76265b7adf"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Fahrturnier"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/0f5aaeb3df"
                                ),
                                ListChildNode(
                                    position = Some(7),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Zuschauer"
                                    )),
                                    name = Some("8"),
                                    id = "http://data.knora.org/lists/a88d01eddf"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "PFERDESPORT"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/e0f06722de"
                        ),
                        ListChildNode(
                            position = Some(11),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Billiard"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/daf4a75fe0"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Fussball"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/7328fb98e0"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kegeln"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/0c5c4ed2e0"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Minigolf"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/3ec3f444e1"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Sommergolf"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/d7f6477ee1"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Wintergolf"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/702a9bb7e1"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Golf"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/a58fa10be1"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Tennis"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/095eeef0e1"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Volleyball"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/a291412ae2"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "BALLSPORT"
                            )),
                            name = Some("7"),
                            id = "http://data.knora.org/lists/41c15426e0"
                        ),
                        ListChildNode(
                            position = Some(12),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Alpinismus"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/d4f8e79ce2"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Bergh\u00FCtten und Restaurants"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/6d2c3bd6e2"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Trecking mit Tieren"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/06608e0fe3"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Wandern"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/9f93e148e3"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Spazieren"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/38c73482e3"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ALPINISMUS"
                            )),
                            name = Some("8"),
                            id = "http://data.knora.org/lists/3bc59463e2"
                        ),
                        ListChildNode(
                            position = Some(13),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Ballon"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/6a2edbf4e3"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Delta"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/03622e2ee4"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Flugzeuge"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/9c958167e4"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Helikopter"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/35c9d4a0e4"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Segelflieger"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/cefc27dae4"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "FLIEGEN"
                            )),
                            name = Some("9"),
                            id = "http://data.knora.org/lists/d1fa87bbe3"
                        ),
                        ListChildNode(
                            position = Some(14),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Malojarennen"
                                            )),
                                            name = Some("1"),
                                            id = "http://data.knora.org/lists/99972186e5"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Berninarennen"
                                            )),
                                            name = Some("2"),
                                            id = "http://data.knora.org/lists/32cb74bfe5"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Shellstrasse"
                                            )),
                                            name = Some("3"),
                                            id = "http://data.knora.org/lists/cbfec7f8e5"
                                        ),
                                        ListChildNode(
                                            position = Some(3),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Personen"
                                            )),
                                            name = Some("4"),
                                            id = "http://data.knora.org/lists/64321b32e6"
                                        ),
                                        ListChildNode(
                                            position = Some(4),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Verschiedenes"
                                            )),
                                            name = Some("5"),
                                            id = "http://data.knora.org/lists/fd656e6be6"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Autorennen"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/0064ce4ce5"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Geschicklichkeitsfahren"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/9699c1a4e6"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Sch\u00F6nheitskonkurrenz"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/2fcd14dee6"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Inline Skating"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/c8006817e7"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Montainbiking"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/6134bb50e7"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Radfahren"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/fa670e8ae7"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Motorradfahren"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/939b61c3e7"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "RADSPORT"
                            )),
                            name = Some("10"),
                            id = "http://data.knora.org/lists/67307b13e5"
                        ),
                        ListChildNode(
                            position = Some(15),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schwimmen Hallenb\u00E4der"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/c5020836e8"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schwimmen Seen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/5e365b6fe8"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Rudern"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/f769aea8e8"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Segeln"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/909d01e2e8"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Windsurfen"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/29d1541be9"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Tauchen"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/c204a854e9"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Rafting"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/5b38fb8de9"
                                ),
                                ListChildNode(
                                    position = Some(7),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kitesurfen"
                                    )),
                                    name = Some("8"),
                                    id = "http://data.knora.org/lists/f46b4ec7e9"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "WASSERSPORT"
                            )),
                            name = Some("11"),
                            id = "http://data.knora.org/lists/2ccfb4fce7"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "SPORT"
                    )),
                    name = Some("9SPO"),
                    id = "http://data.knora.org/lists/71a1543cce"
                ),
                ListChildNode(
                    position = Some(9),
                    children = Vector(
                        ListChildNode(
                            position = Some(0),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Autos, Busse und Postautos"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/bf064873ea"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Boote"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/583a9bacea"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Flugplatz Samedan"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/f16deee5ea"
                                ),
                                ListChildNode(
                                    position = Some(3),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kommunikation"
                                    )),
                                    name = Some("4"),
                                    id = "http://data.knora.org/lists/8aa1411feb"
                                ),
                                ListChildNode(
                                    position = Some(4),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Kutschen und Pferdetransporte"
                                    )),
                                    name = Some("5"),
                                    id = "http://data.knora.org/lists/23d59458eb"
                                ),
                                ListChildNode(
                                    position = Some(5),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Luftseilbahnen und Stationen"
                                    )),
                                    name = Some("6"),
                                    id = "http://data.knora.org/lists/bc08e891eb"
                                ),
                                ListChildNode(
                                    position = Some(6),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schneer\u00E4umungs- und Pistenfahrzeuge"
                                    )),
                                    name = Some("7"),
                                    id = "http://data.knora.org/lists/553c3bcbeb"
                                ),
                                ListChildNode(
                                    position = Some(7),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Schneekanonen"
                                    )),
                                    name = Some("8"),
                                    id = "http://data.knora.org/lists/ee6f8e04ec"
                                ),
                                ListChildNode(
                                    position = Some(8),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Skilifte"
                                    )),
                                    name = Some("9"),
                                    id = "http://data.knora.org/lists/87a3e13dec"
                                ),
                                ListChildNode(
                                    position = Some(9),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Standseilbahnen und Stationen"
                                    )),
                                    name = Some("10"),
                                    id = "http://data.knora.org/lists/20d73477ec"
                                ),
                                ListChildNode(
                                    position = Some(10),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Strassen und P\u00E4sse"
                                    )),
                                    name = Some("11"),
                                    id = "http://data.knora.org/lists/b90a88b0ec"
                                ),
                                ListChildNode(
                                    position = Some(11),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Tram"
                                    )),
                                    name = Some("12"),
                                    id = "http://data.knora.org/lists/523edbe9ec"
                                ),
                                ListChildNode(
                                    position = Some(12),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Wegweiser"
                                    )),
                                    name = Some("13"),
                                    id = "http://data.knora.org/lists/eb712e23ed"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "VERKEHR"
                            )),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/26d3f439ea"
                        ),
                        ListChildNode(
                            position = Some(1),
                            children = Vector(ListChildNode(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringV2(
                                    language = None,
                                    value = "Eisenbahnen und Bahnh\u00F6fe"
                                )),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/1dd9d495ed"
                            )),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "EISENBAHNEN"
                            )),
                            name = Some("1-1"),
                            id = "http://data.knora.org/lists/84a5815ced"
                        ),
                        ListChildNode(
                            position = Some(2),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Casino"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/4f407b08ee"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "G\u00E4ste"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/e873ce41ee"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Mode"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/81a7217bee"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "FREMDENVERKEHR"
                            )),
                            name = Some("2"),
                            id = "http://data.knora.org/lists/b60c28cfed"
                        ),
                        ListChildNode(
                            position = Some(3),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Vector(
                                        ListChildNode(
                                            position = Some(0),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel A"
                                            )),
                                            name = Some("hotel_a"),
                                            id = "http://data.knora.org/lists/97744976b801"
                                        ),
                                        ListChildNode(
                                            position = Some(1),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel B"
                                            )),
                                            name = Some("hotel_b"),
                                            id = "http://data.knora.org/lists/30a89cafb801"
                                        ),
                                        ListChildNode(
                                            position = Some(2),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel C"
                                            )),
                                            name = Some("hotel_c"),
                                            id = "http://data.knora.org/lists/c9dbefe8b801"
                                        ),
                                        ListChildNode(
                                            position = Some(3),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel D"
                                            )),
                                            name = Some("hotel_d"),
                                            id = "http://data.knora.org/lists/620f4322b901"
                                        ),
                                        ListChildNode(
                                            position = Some(4),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel E"
                                            )),
                                            name = Some("hotel_e"),
                                            id = "http://data.knora.org/lists/fb42965bb901"
                                        ),
                                        ListChildNode(
                                            position = Some(5),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel F"
                                            )),
                                            name = Some("hotel_f"),
                                            id = "http://data.knora.org/lists/9476e994b901"
                                        ),
                                        ListChildNode(
                                            position = Some(6),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel G"
                                            )),
                                            name = Some("hotel_g"),
                                            id = "http://data.knora.org/lists/2daa3cceb901"
                                        ),
                                        ListChildNode(
                                            position = Some(7),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel H"
                                            )),
                                            name = Some("hotel_h"),
                                            id = "http://data.knora.org/lists/c6dd8f07ba01"
                                        ),
                                        ListChildNode(
                                            position = Some(8),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel I"
                                            )),
                                            name = Some("hotel_i"),
                                            id = "http://data.knora.org/lists/5f11e340ba01"
                                        ),
                                        ListChildNode(
                                            position = Some(9),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel J"
                                            )),
                                            name = Some("hotel_j"),
                                            id = "http://data.knora.org/lists/f844367aba01"
                                        ),
                                        ListChildNode(
                                            position = Some(10),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel K"
                                            )),
                                            name = Some("hotel_k"),
                                            id = "http://data.knora.org/lists/917889b3ba01"
                                        ),
                                        ListChildNode(
                                            position = Some(11),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel L"
                                            )),
                                            name = Some("hotel_l"),
                                            id = "http://data.knora.org/lists/2aacdcecba01"
                                        ),
                                        ListChildNode(
                                            position = Some(12),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel M"
                                            )),
                                            name = Some("hotel_m"),
                                            id = "http://data.knora.org/lists/c3df2f26bb01"
                                        ),
                                        ListChildNode(
                                            position = Some(13),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel N"
                                            )),
                                            name = Some("hotel_n"),
                                            id = "http://data.knora.org/lists/5c13835fbb01"
                                        ),
                                        ListChildNode(
                                            position = Some(14),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel O"
                                            )),
                                            name = Some("hotel_o"),
                                            id = "http://data.knora.org/lists/f546d698bb01"
                                        ),
                                        ListChildNode(
                                            position = Some(15),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel P"
                                            )),
                                            name = Some("hotel_p"),
                                            id = "http://data.knora.org/lists/8e7a29d2bb01"
                                        ),
                                        ListChildNode(
                                            position = Some(16),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel Q"
                                            )),
                                            name = Some("hotel_q"),
                                            id = "http://data.knora.org/lists/27ae7c0bbc01"
                                        ),
                                        ListChildNode(
                                            position = Some(17),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel R"
                                            )),
                                            name = Some("hotel_r"),
                                            id = "http://data.knora.org/lists/c0e1cf44bc01"
                                        ),
                                        ListChildNode(
                                            position = Some(18),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel S"
                                            )),
                                            name = Some("hotel_s"),
                                            id = "http://data.knora.org/lists/5915237ebc01"
                                        ),
                                        ListChildNode(
                                            position = Some(19),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel T"
                                            )),
                                            name = Some("hotel_t"),
                                            id = "http://data.knora.org/lists/f24876b7bc01"
                                        ),
                                        ListChildNode(
                                            position = Some(20),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel U"
                                            )),
                                            name = Some("hotel_u"),
                                            id = "http://data.knora.org/lists/8b7cc9f0bc01"
                                        ),
                                        ListChildNode(
                                            position = Some(21),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel V"
                                            )),
                                            name = Some("hotel_v"),
                                            id = "http://data.knora.org/lists/24b01c2abd01"
                                        ),
                                        ListChildNode(
                                            position = Some(22),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel W"
                                            )),
                                            name = Some("hotel_w"),
                                            id = "http://data.knora.org/lists/9f29173c3b02"
                                        ),
                                        ListChildNode(
                                            position = Some(23),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel X"
                                            )),
                                            name = Some("hotel_x"),
                                            id = "http://data.knora.org/lists/bde36f63bd01"
                                        ),
                                        ListChildNode(
                                            position = Some(24),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel Y"
                                            )),
                                            name = Some("hotel_y"),
                                            id = "http://data.knora.org/lists/5617c39cbd01"
                                        ),
                                        ListChildNode(
                                            position = Some(25),
                                            children = Nil,
                                            comments = Nil,
                                            labels = Vector(StringV2(
                                                language = None,
                                                value = "Hotel Z"
                                            )),
                                            name = Some("hotel_z"),
                                            id = "http://data.knora.org/lists/ef4a16d6bd01"
                                        )
                                    ),
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Hotels und Restaurants A-Z"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/b30ec8edee"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Essen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/4c421b27ef"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Men\u00FCkarten"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/e5756e60ef"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "HOTELLERIE"
                            )),
                            name = Some("3"),
                            id = "http://data.knora.org/lists/1adb74b4ee"
                        ),
                        ListChildNode(
                            position = Some(4),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Personal und B\u00FCro"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/17dd14d3ef"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Anl\u00E4sse und Reisen"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/b010680cf0"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Markenzeichen St. Moritz"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/4944bb45f0"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "KURVEREIN"
                            )),
                            name = Some("4"),
                            id = "http://data.knora.org/lists/7ea9c199ef"
                        ),
                        ListChildNode(
                            position = Some(5),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Arbeitswelt"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/7bab61b8f0"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Reklame"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/14dfb4f1f0"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Bauwesen"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/ad12082bf1"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "GEWERBE"
                            )),
                            name = Some("6"),
                            id = "http://data.knora.org/lists/e2770e7ff0"
                        ),
                        ListChildNode(
                            position = Some(6),
                            children = Vector(
                                ListChildNode(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Elektrizit\u00E4t"
                                    )),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/df79ae9df1"
                                ),
                                ListChildNode(
                                    position = Some(1),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Wasserkraft"
                                    )),
                                    name = Some("2"),
                                    id = "http://data.knora.org/lists/78ad01d7f1"
                                ),
                                ListChildNode(
                                    position = Some(2),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringV2(
                                        language = None,
                                        value = "Solarenergie"
                                    )),
                                    name = Some("3"),
                                    id = "http://data.knora.org/lists/11e15410f2"
                                )
                            ),
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "ENERGIEWIRTSCHAFT"
                            )),
                            name = Some("7"),
                            id = "http://data.knora.org/lists/46465b64f1"
                        ),
                        ListChildNode(
                            position = Some(7),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "AGRARWIRTSCHAFT"
                            )),
                            name = Some("8"),
                            id = "http://data.knora.org/lists/aa14a849f2"
                        ),
                        ListChildNode(
                            position = Some(8),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringV2(
                                language = None,
                                value = "WALDWIRTSCHAFT"
                            )),
                            name = Some("9"),
                            id = "http://data.knora.org/lists/4348fb82f2"
                        )
                    ),
                    comments = Nil,
                    labels = Vector(StringV2(
                        language = None,
                        value = "WIRTSCHAFT"
                    )),
                    name = Some("10WIR"),
                    id = "http://data.knora.org/lists/8d9fa100ea"
                )
            ),
            comments = Vector(StringV2(
                language = Some("de"),
                value = "Hierarchisches Stichwortverzeichnis / Signatur der Bilder"
            )),
            labels = Vector(
                StringV2(
                    language = Some("en"),
                    value = "Title"
                ),
                StringV2(
                    language = Some("de"),
                    value = "Titel"
                ),
                StringV2(
                    language = Some("fr"),
                    value = "Titre"
                )
            ),
            projectIri = Some("http://data.knora.org/projects/images"),
            id = "http://data.knora.org/lists/73d0ec0302"
        )

    private val imageCategory = Seq.empty[ListChildNode]

    private val season = Seq(
        ListChildNode(
            id = "http://data.knora.org/lists/526f26ed04",
            name = Some("sommer"),
            labels = Seq(StringV2("Sommer")),
            comments = Seq.empty[StringV2],
            children = Seq.empty[ListChildNode],
            position = Some(0)
        ),
        ListChildNode(
            id = "http://data.knora.org/lists/eda2792605",
            name = Some("winter"),
            labels = Seq(StringV2("Winter")),
            comments = Seq.empty[StringV2],
            children = Seq.empty[ListChildNode],
            position = Some(1)
        )
    )

    private val nodePath = Seq(
        ListChildNode(
            id = "http://data.knora.org/lists/691eee1cbe",
            name = Some("4KUN"),
            labels = Seq(StringV2("KUNST")),
            comments = Seq.empty[StringV2],
            children = Seq.empty[ListChildNode],
            position = None

        ),
        ListChildNode(
            id = "http://data.knora.org/lists/2ebd2706c1",
            name = Some("7"),
            labels = Seq(StringV2("FILM UND FOTO")),
            comments = Seq.empty[StringV2],
            children = Seq.empty[ListChildNode],
            position = None
        ),
        ListChildNode(
            id = "http://data.knora.org/lists/c7f07a3fc1",
            name = Some("1"),
            labels = Seq(StringV2("Heidi Film")),
            comments = Seq.empty[StringV2],
            children = Seq.empty[ListChildNode],
            position = None
        )
    )

    "Load test data " in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The Lists Responder" when {

        "used to query information about lists" should {

            "return all lists" in {
                actorUnderTest ! ListsGetAdminRequest(userProfile = userProfile)

                val received: ReadListsSequenceV2 = expectMsgType[ReadListsSequenceV2](timeout)

                received.items.size should be(6)
            }

            "return all lists belonging to the images project" in {
                actorUnderTest ! ListsGetAdminRequest(projectIri = Some(SharedAdminTestData.IMAGES_PROJECT_IRI), userProfile = userProfile)

                val received: ReadListsSequenceV2 = expectMsgType[ReadListsSequenceV2](timeout)

                // log.debug("received: " + received)

                received.items.size should be(4)
            }

            "return basic list node information" in {
                actorUnderTest ! ListNodeInfoGetAdminRequest(
                    iri = "http://data.knora.org/lists/73d0ec0302",
                    userProfile = userProfile
                )

                val received: ReadListsSequenceV2 = expectMsgType[ReadListsSequenceV2](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.items.head.sorted should be(keywordRootNode.sorted)
            }

            "return an extended list response" in {
                actorUnderTest ! ListGetAdminRequest(
                    iri = "http://data.knora.org/lists/73d0ec0302",
                    userProfile = userProfile
                )

                val received: ReadListsSequenceV2 = expectMsgType[ReadListsSequenceV2](timeout)

                // log.debug("returned whole keyword list: {}", MessageUtil.toSource(received.items.head))

                received.items.head.sorted should be(keywordList.sorted)
            }
        }

        "used to modify lists" should {

            val newListIri = new MutableTestIri

            "create a list" ignore {

            }

            "update basic list information" ignore {

            }

            "add flat nodes" ignore {

            }

            "add hierarchical nodes" ignore {

            }

            "change node order" ignore {

            }

            "delete node if not in use" ignore {

            }

        }
    }
}
