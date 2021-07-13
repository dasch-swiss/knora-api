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

package org.knora.webapi.responders.v1

import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v1.responder.searchmessages._
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.duration._

/**
  * Static data for testing [[SearchResponderV1]].
  */
object SearchResponderV1Spec {

  private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser

  private val anythingUser1 = SharedTestDataADM.anythingUser1

  private val anythingUser2 = SharedTestDataADM.anythingUser2

  private val fulltextThingResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        "Ich liebe die Dinge, sie sind alles f\u00FCr mich.",
        "Na ja, die Dinge sind OK."
      ),
      valuelabel = Vector(
        "Label",
        "Text",
        "Text"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#TextValue",
        "http://www.knora.org/ontology/knora-base#TextValue"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg"
    ))

  private val fulltextValueInThingResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        "Ich liebe die Dinge, sie sind alles f\u00FCr mich."
      ),
      valuelabel = Vector(
        "Label",
        "Text"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#TextValue"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg"
    ))

  private val fulltextThingResultsForUser2 = Vector(
    SearchResultRowV1(
      rights = Some(2),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector("Ein Ding f\u00FCr jemanden, dem die Dinge gefallen"),
      valuelabel = Vector("Label"),
      valuetype_id = Vector("http://www.w3.org/2000/01/rdf-schema#label"),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg"
    ))

  private val hasOtherThingResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "A thing that only project members can see",
        "Another thing that only project members can see"
      ),
      valuelabel = Vector(
        "Label",
        "Ein anderes Ding"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#Resource"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/resources/g_6nDsLHoB8MDPkwVJWHag"
    ))

  private val hasStandoffLinkToResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "A thing that only project members can see",
        "Another thing that only project members can see"
      ),
      valuelabel = Vector(
        "Label",
        "hat Standoff Link zu"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#Resource"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/resources/g_6nDsLHoB8MDPkwVJWHag"
    ))

  private val hasStandoffLinkToResultsForUser2 = Vector(
    SearchResultRowV1(
      rights = Some(2),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "A thing that only project members can see",
        "Another thing that only project members can see"
      ),
      valuelabel = Vector(
        "Label",
        "hat Standoff Link zu"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#Resource"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/resources/g_6nDsLHoB8MDPkwVJWHag"
    ))

}

/**
  * Tests [[SearchResponderV1]].
  */
class SearchResponderV1Spec extends CoreSpec() with ImplicitSender {

  import SearchResponderV1Spec._

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  // The default timeout for receiving reply messages from actors.
  private val timeout = 30.seconds

  // An expected response consisting of two books with the title "Zeitglöcklein des Lebens und Leidens Christi".
  private val twoZeitglöckleinBooksResponse = SearchGetResponseV1(
    thumb_max = SearchPreviewDimensionsV1(
      ny = 32,
      nx = 32
    ),
    paging = Vector(
      SearchResultPage(
        show_nrows = 2,
        start_at = 0,
        current = true
      )),
    nhits = "2",
    subjects = Vector(
      SearchResultRowV1(
        rights = Some(6),
        preview_ny = 32,
        preview_nx = 32,
        value = Vector(
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
        ),
        valuelabel = Vector(
          "Label",
          "Titel"
        ),
        valuetype_id = Vector(
          "http://www.w3.org/2000/01/rdf-schema#label",
          "http://www.knora.org/ontology/knora-base#TextValue"
        ),
        iconlabel = Some("Buch"),
        icontitle = Some("Buch"),
        iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
        preview_path = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
        obj_id = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"
      ),
      SearchResultRowV1(
        rights = Some(6),
        preview_ny = 32,
        preview_nx = 32,
        value = Vector(
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
        ),
        valuelabel = Vector(
          "Label",
          "Titel"
        ),
        valuetype_id = Vector(
          "http://www.w3.org/2000/01/rdf-schema#label",
          "http://www.knora.org/ontology/knora-base#TextValue"
        ),
        iconlabel = Some("Buch"),
        icontitle = Some("Buch"),
        iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
        preview_path = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
        obj_id = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ"
      )
    )
  )

  val bertholdResponse = SearchGetResponseV1(
    subjects = Vector(
      SearchResultRowV1(
        obj_id = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
        preview_path = Some("http://0.0.0.0:3335/project-icons/incunabula/book.gif"),
        iconsrc = Some("http://0.0.0.0:3335/project-icons/incunabula/book.gif"),
        icontitle = Some("Buch"),
        iconlabel = Some("Buch"),
        valuetype_id =
          Vector("http://www.w3.org/2000/01/rdf-schema#label", "http://www.knora.org/ontology/knora-base#TextValue"),
        valuelabel = Vector("Label", "Creator"),
        value = Vector("Zeitglöcklein des Lebens und Leidens Christi", "Berthold, der Bruder"),
        preview_nx = 32,
        preview_ny = 32,
        rights = Some(6)
      ),
      SearchResultRowV1(
        obj_id = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ",
        preview_path = Some("http://0.0.0.0:3335/project-icons/incunabula/book.gif"),
        iconsrc = Some("http://0.0.0.0:3335/project-icons/incunabula/book.gif"),
        icontitle = Some("Buch"),
        iconlabel = Some("Buch"),
        valuetype_id =
          Vector("http://www.w3.org/2000/01/rdf-schema#label", "http://www.knora.org/ontology/knora-base#TextValue"),
        valuelabel = Vector("Label", "Creator"),
        value = Vector("Zeitglöcklein des Lebens und Leidens Christi", "Berthold, der Bruder"),
        preview_nx = 32,
        preview_ny = 32,
        rights = Some(6)
      )
    ),
    nhits = "2",
    paging = Vector(SearchResultPage(current = true, 0, 2)),
    thumb_max = SearchPreviewDimensionsV1(32, 32)
  )

  "The search responder" should {
    "return 3 results when we do a simple search for the word 'Zeitglöcklein' in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Zeitglöcklein?searchtype=fulltext
      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "Zeitglöcklein",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 if response.subjects.size == 3 => ()
      }
    }

    "return 2 results when we do a simple search for the words 'Zeitglöcklein' and 'Lebens' in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Zeitglöcklein%20Lebens?searchtype=fulltext
      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "Zeitglöcklein AND Lebens",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 if response.subjects.size == 2 => ()
      }
    }

    "return 0 results when we do a simple search for the words 'Zeitglöcklein' for the type incunabula:page in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Zeitglöcklein%20Lebens?searchtype=fulltext&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page
      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "Zeitglöcklein AND Lebens",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 if response.subjects.isEmpty => ()
      }
    }

    "return 1 result when we do a simple search for the word 'Orationes' (the rdfs:label and title of a book) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Orationes?searchtype=fulltext
      // TODO: Fuseki and GraphDB actually return different results here: Fuseki returns the match for the resource label, while GraphDB returns
      // the one for the text value. Both appear to be correct: we are using SAMPLE, so each triplestore is returning a different random result.
      // Try to find another approach so that they return the same result. Also, GraphDB returns the wrong label (again, because it seems to be
      // selecting a random one). Instead of getting labels from the search query, the search responder can ask the ontology responder for them.
      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "Orationes",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 if response.subjects.size == 1 => ()
      }
    }

    "return 2 results when we do a simple search for the words 'Berthold and Bruder' in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Berthold%20Bruder?searchtype=fulltext
      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "Berthold AND Bruder",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsg(timeout, bertholdResponse)
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the string 'Zeitglöcklein' (using a regular expression) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=LIKE&searchval=Zeitglöcklein
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein"),
        compareProps = Vector(SearchComparisonOperatorV1.LIKE),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the word 'Zeitglöcklein' (using the full-text search index) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitglöcklein
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein"),
        compareProps = Vector(SearchComparisonOperatorV1.MATCH),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    "return 1 book with the title 'Zeitglöcklein des Lebens und Leidens Christi' that was published in 1490 (Julian Calendar) when we search for book titles containing the word 'Zeitglöcklein' (using the full-text search index) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitglöcklein&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=EQ&searchval=JULIAN:1490
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein", "JULIAN:1490"),
        compareProps = Vector(SearchComparisonOperatorV1.MATCH, SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title",
                             "http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(1)
      }
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the word 'Lebens' but not containing the word 'walfart' (using MATCH BOOLEAN) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH_BOOLEAN&searchval=%2BLebens+-walfart&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("+Lebens -walfart"),
        compareProps = Vector(SearchComparisonOperatorV1.MATCH_BOOLEAN),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    /*

        Previously we used a FILTER NOT EXISTS statement here, but it did not return a value for ?anyLiteral
        So now we use just a negated regex
        Problem: if a resource has two instances of the same property and one of them matches and the other does not,
        it will be contained in the search results (now we have 18 instead of 17 books returned)

     */

    "return 18 books when we search for book titles that do not include the string 'Zeitglöcklein' (using a regular expression) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!LIKE&searchval=Zeitgl%C3%B6cklein
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein"),
        compareProps = Vector(SearchComparisonOperatorV1.NOT_LIKE),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(18)
      }
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for exactly that book title in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein des Lebens und Leidens Christi"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    "return 18 books when we search for all books that have a title that is not exactly 'Zeitglöcklein des Lebens und Leidens Christi' (although they may have another title that is) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein des Lebens und Leidens Christi"),
        compareProps = Vector(SearchComparisonOperatorV1.NOT_EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(18)
      }
    }

    "return 19 books when we search for all books in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector(),
        compareProps = Vector(),
        propertyIri = Vector(),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(19)
      }
    }

    "return 19 books when we search for all books that have a title in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=EXISTS&searchval
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector(""),
        compareProps = Vector(SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(19)
      }
    }

    "return 19 pages when we search for all pages that have a sequence number of 1 in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("1"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(19)
      }
    }

    "return 79 pages when we search for all pages that have an incunabula:seqnum greater than 450 in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=GT&searchval=450
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("450"),
        compareProps = Vector(SearchComparisonOperatorV1.GT),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 100,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(79)
      }
    }

    "return 79 pages when we search for all representations that have an incunabula:seqnum greater than 450 in the Incunabula test data" in {
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("450"),
        compareProps = Vector(SearchComparisonOperatorV1.GT),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/knora-base#Representation"),
        startAt = 0,
        showNRows = 100,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(79)
      }
    }

    "return 2 books when we search for all books that were published in January 1495 (Julian date) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("JULIAN:1495-01"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(2)
      }
    }

    "return 7 books when we search for all books whose publication date is greater than or equal to January 1495 (Julian date) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=GT_EQ&searchval=JULIAN:1495-01
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("JULIAN:1495-01"),
        compareProps = Vector(SearchComparisonOperatorV1.GT_EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(7)
      }
    }

    "return 15 books when we search for all books whose publication date is less than or equal to December 1495 (Julian date) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=LT_EQ&searchval=JULIAN:1495-12
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("JULIAN:1495-12"),
        compareProps = Vector(SearchComparisonOperatorV1.LT_EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(15)
      }
    }

    "return all the pages that are part of Zeitglöcklein des Lebens" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Fc5058f3a
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#partOf"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 500,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(402)
      }
    }

    "return all the pages that have a sequence number of 1 and are part of some book" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EXISTS&searchval=
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("1", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum",
                             "http://www.knora.org/ontology/0803/incunabula#partOf"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(19)
      }
    }

    "return all the representations that have a sequence number of 1 and are part of some book (using knora-base:isPartOf)" in {
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("1", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum",
                             "http://www.knora.org/ontology/knora-base#isPartOf"),
        filterByRestype = Some("http://www.knora.org/ontology/knora-base#Representation"),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(19)
      }
    }

    "return all the pages that are part of Zeitglöcklein des Lebens and have a seqnum" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Fc5058f3a&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EXISTS&searchval=
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#partOf",
                             "http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 500,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(402)
      }

    }

    "return all the representations that are part of Zeitglöcklein des Lebens and have a seqnum (using base properties from knora-base)" in {
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#isPartOf",
                             "http://www.knora.org/ontology/knora-base#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/knora-base#Representation"),
        startAt = 0,
        showNRows = 500,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(402)
      }

    }

    "return all the pages that are part of Zeitglöcklein des Lebens, have a seqnum less than or equal to 200, and have a page number that is not 'a1r, Titelblatt'" in {
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", "200", "a1r, Titelblatt"),
        compareProps =
          Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.LT_EQ, SearchComparisonOperatorV1.NOT_EQ),
        propertyIri = Vector(
          "http://www.knora.org/ontology/0803/incunabula#partOf",
          "http://www.knora.org/ontology/0803/incunabula#seqnum",
          "http://www.knora.org/ontology/0803/incunabula#pagenum"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 300,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(199)
      }

    }

    "return all the images from the images-demo project whose title belong to the category 'Sport'" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id%5B%5D=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23titel&compop%5B%5D=EQ&searchval%5B%5D=http%3A%2F%2Frdfh.ch%2Flists%2F71a1543cce&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23bild
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/lists/00FF/71a1543cce"), // list node SPORT
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(IMAGES_TITEL_PROPERTY),
        filterByRestype = Some(IMAGES_BILD_RESOURCE_CLASS),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(10)
      }

    }

    "return all the images from the images-demo project whose title belong to the category 'Spazieren'" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id%5B%5D=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23titel&compop%5B%5D=EQ&searchval%5B%5D=http%3A%2F%2Frdfh.ch%2Flists%2F38c73482e3&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23bild
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/lists/00FF/38c73482e3"), // list node SPAZIEREN
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(IMAGES_TITEL_PROPERTY),
        filterByRestype = Some(IMAGES_BILD_RESOURCE_CLASS),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(1)
      }

    }

    "return all the images from the images-demo project whose title belong to the category 'Alpinismus'" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id%5B%5D=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23titel&compop%5B%5D=EQ&searchval%5B%5D=http%3A%2F%2Frdfh.ch%2Flists%2F3bc59463e2&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23bild
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/lists/00FF/3bc59463e2"), // list node ALPINISMUS
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(IMAGES_TITEL_PROPERTY),
        filterByRestype = Some(IMAGES_BILD_RESOURCE_CLASS),
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(4)
      }

    }

    "filter full-text search results using permissions on resources and values" in {
      // When the owner of the resource and its values, anythingUser1, searches for something that matches the resource's label
      // as well as both values, the search result should include the resource and show that both values matched.

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "die AND Dinge",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser1,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(fulltextThingResultsForUser1)
      }

      // Another user in the same project, anythingUser2, should get the resource as a search result, but should not see the values.

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "die AND Dinge",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser2,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(fulltextThingResultsForUser2)
      }

      // User anythingUser2 should also get the resource as a search result by searching for something that matches the resource's label, but not the values.

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "für AND jemanden",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser2,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(fulltextThingResultsForUser2)
      }

      // If user anythingUser1 searches for something that matches one of the values, but doesn't match the resource's label, the result should include the
      // value that matched, but not the value that didn't match.

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "alles AND für AND mich",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser1,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(fulltextValueInThingResultsForUser1)
      }

      // If user anythingUser2 searches for something that matches one of the values, but doesn't match the resource's label, no results should be returned.

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "alles AND für AND mich",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser2,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(0)
      }

      // A user in another project shouldn't get any results for any of those queries.

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "die AND Dinge",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "für AND jemanden",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

      responderManager ! FulltextSearchGetRequestV1(
        searchValue = "alles AND für AND mich",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "should not show resources that the user doesn't have permission to see in an extended search" in {
      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(0)
      }

      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(0)
      }
    }

    "should show standoff links if the user has view permission on both resources, but show other links only if the user also has view permission on the link" in {
      // The link's owner, anythingUser1, should see the hasOtherThing link as well as the hasStandoffLinkTo link.

      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser1,
        searchValue = Vector("http://rdfh.ch/resources/lBGY9SS0rIr-bHv4iPePHw"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(hasOtherThingResultsForUser1)
      }

      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser1,
        searchValue = Vector("http://rdfh.ch/resources/lBGY9SS0rIr-bHv4iPePHw"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(hasStandoffLinkToResultsForUser1)
      }

      // But another user in the Anything project should see only the hasStandoffLinkTo link.

      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser2,
        searchValue = Vector("http://rdfh.ch/resources/lBGY9SS0rIr-bHv4iPePHw"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects.size should ===(0)
      }

      responderManager ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser2,
        searchValue = Vector("http://rdfh.ch/resources/lBGY9SS0rIr-bHv4iPePHw"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10,
        featureFactoryConfig = defaultFeatureFactoryConfig
      )

      expectMsgPF(timeout) {
        case response: SearchGetResponseV1 => response.subjects should ===(hasStandoffLinkToResultsForUser2)
      }
    }

  }
}
