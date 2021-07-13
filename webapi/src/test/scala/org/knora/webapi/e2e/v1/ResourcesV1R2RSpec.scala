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

package org.knora.webapi.e2e.v1

import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.zip.{ZipEntry, ZipInputStream}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.exceptions.{
  AssertionException,
  InvalidApiJsonException,
  NotFoundException,
  TriplestoreResponseException
}
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.{SparqlSelectResult, VariableResultsRow}
import org.knora.webapi.messages.v1.responder.resourcemessages.PropsGetForRegionV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.routing.v1.{ResourcesRouteV1, ValuesRouteV1}
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM._
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.scalatest.Assertion
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Random, Try}
import scala.xml.{Node, NodeSeq, XML}

/**
  * End-to-end test specification for the resources endpoint.
  */
class ResourcesV1R2RSpec extends R2RSpec {

  override def testConfigSource: String =
    """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

  private val resourcesPathV1 = DSPApiDirectives.handleErrors(system) { new ResourcesRouteV1(routeData).knoraApiPath }
  private val resourcesPathV2 = DSPApiDirectives.handleErrors(system) { new ResourcesRouteV2(routeData).knoraApiPath }
  private val valuesPathV1 = DSPApiDirectives.handleErrors(system) { new ValuesRouteV1(routeData).knoraApiPath }

  private val superUser = SharedTestDataADM.superUser
  private val superUserEmail = superUser.email

  private val imagesUser = SharedTestDataADM.imagesUser01
  private val imagesUserEmail = imagesUser.email

  private val incunabulaUser = SharedTestDataADM.incunabulaProjectAdminUser
  private val incunabulaUserEmail = incunabulaUser.email

  private val incunabulaUser2 = SharedTestDataADM.incunabulaCreatorUser
  private val incunabulaUserEmail2 = incunabulaUser2.email

  private val anythingUser = SharedTestDataADM.anythingUser1
  private val anythingUserEmail = anythingUser.email

  private val anythingAdmin = SharedTestDataADM.anythingAdminUser
  private val anythingAdminEmail = anythingAdmin.email

  private val beolUser = SharedTestDataADM.beolUser
  private val beolUserEmail = beolUser.email

  private val password = SharedTestDataADM.testPass

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout * 2)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/ontologies/example-box.ttl",
                  name = "http://www.knora.org/ontology/shared/example-box"),
    RdfDataObject(path = "test_data/ontologies/example-ibox.ttl",
                  name = "http://www.knora.org/ontology/shared/example-ibox"),
    RdfDataObject(path = "test_data/ontologies/empty-thing-onto.ttl",
                  name = "http://www.knora.org/ontology/0001/empty-thing"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
  )

  private val firstThingIri = new MutableTestIri
  private val firstTextValueIRI = new MutableTestIri
  private val secondThingIri = new MutableTestIri
  private val thirdThingIri = new MutableTestIri
  private val fourthThingIri = new MutableTestIri
  private val fifthThingIri = new MutableTestIri
  private val sixthThingIri = new MutableTestIri
  private val seventhThingIri = new MutableTestIri
  private val eighthThingIri = new MutableTestIri
  private val abelAuthorIri = new MutableTestIri
  private val mathIntelligencerIri = new MutableTestIri
  private val deutschesDingIri = new MutableTestIri
  private val standoffLangDingIri = new MutableTestIri
  private val thingWithString = new MutableTestIri
  private val thingWithCreationDate = new MutableTestIri

  // incunabula book with title "Eyn biechlin ..."
  private val incunabulaBookBiechlin = "http://rdfh.ch/resources/60QF3uJ-SdKOj6Awfb5XIg"

  // incunabula book with title Quadragesimale
  private val incunabulaBookQuadra = "http://rdfh.ch/resources/rGHDByCCTFeJpHbdnkk8OA"

  private val notTheMostBoringComment = "This is not the most boring comment I have seen."

  private val mappingIri = OntologyConstants.KnoraBase.StandardMapping

  private val xml1 =
    """<?xml version="1.0" encoding="UTF-8"?>
          |<text><strong>Test</strong><br/>text</text>
        """.stripMargin

  private val xml2 =
    """<?xml version="1.0" encoding="UTF-8"?>
          |<text>a <strong>new</strong> value</text>
        """.stripMargin

  private val xml3 =
    s"""<?xml version="1.0" encoding="UTF-8"?>
           |<text>
           |    This text links to <a href="http://www.google.ch">Google</a> and a Knora <a class="salsah-link" href="$incunabulaBookBiechlin">resource</a>.
           |</text>
         """.stripMargin

  private val xml4 =
    s"""<?xml version="1.0" encoding="UTF-8"?>
           |<text>
           |    This text links to <a href="http://www.google.ch">Google</a> and a Knora <a class="salsah-link" href="$incunabulaBookBiechlin">resource</a> and another Knora resource <a class="salsah-link" href="$incunabulaBookQuadra">resource</a>.
           |</text>
         """.stripMargin

  /**
    * Gets the field `res_id` from a JSON response to resource creation.
    *
    * @param response the response sent back from the API.
    * @return the value of `res_id`.
    */
  private def getResIriFromJsonResponse(response: HttpResponse) = {
    AkkaHttpUtils.httpResponseToJson(response).fields.get("res_id") match {
      case Some(JsString(resourceId)) => resourceId
      case None                       => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
      case other =>
        throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but $other")
    }
  }

  /**
    * Gets the field `id` from a JSON response to value creation (new value).
    *
    * @param response the response sent back from the API.
    * @return the value of `res_id`.
    */
  private def getNewValueIriFromJsonResponse(response: HttpResponse) = {
    AkkaHttpUtils.httpResponseToJson(response).fields.get("id") match {
      case Some(JsString(resourceId)) => resourceId
      case None                       => throw InvalidApiJsonException(s"The response does not contain a field called 'res_id'")
      case other =>
        throw InvalidApiJsonException(s"The response does not contain a res_id of type JsString, but $other")
    }
  }

  /**
    * Gets the given property's values from a resource full response.
    *
    * @param response the response to a resource full request.
    * @param prop     the given property IRI.
    * @return the property's values.
    */
  private def getValuesForProp(response: HttpResponse, prop: IRI): JsValue = {
    AkkaHttpUtils.httpResponseToJson(response).fields("props").asJsObject.fields(prop).asJsObject.fields("values")
  }

  /**
    * Gets the given property's comments from a resource full response.
    *
    * @param response the response to a resource full request.
    * @param prop     the given property IRI.
    * @return the property's comments.
    */
  private def getCommentsForProp(response: HttpResponse, prop: IRI): JsValue = {
    AkkaHttpUtils.httpResponseToJson(response).fields("props").asJsObject.fields(prop).asJsObject.fields("comments")
  }

  /**
    * Creates a SPARQL query string to get the standoff links (direct links) for a given resource.
    *
    * @param resIri the resource whose standoff links are to be queried.
    * @return SPARQL query string.
    */
  private def getDirectLinksSPARQL(resIri: IRI): String = {
    s"""
           |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |
           |SELECT ?referredResourceIRI WHERE {
           |    BIND(IRI("$resIri") as ?resIRI)
           |    ?resIRI knora-base:hasStandoffLinkTo ?referredResourceIRI .
           |}
         """.stripMargin
  }

  /**
    * Creates a SPARQL query to get the standoff links reifications to check for the target resource and the reference count.
    *
    * @param resIri the resource whose standoff reifications are to be queried.
    * @return SPARQL query string.
    */
  private def getRefCountsSPARQL(resIri: IRI): String = {
    s"""
           |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |
           |SELECT DISTINCT ?reificationIRI ?object ?refCnt WHERE {
           |    BIND(IRI("$resIri") as ?resIRI)
           |    ?resIRI knora-base:hasStandoffLinkToValue ?reificationIRI .
           |    ?reificationIRI rdf:object ?object .
           |    ?reificationIRI knora-base:valueHasRefCount ?refCnt .
           |}
         """.stripMargin
  }

  private val search = "/v1/resources?restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23Thing"
  private val filter = "&searchstr=value*"

  /**
    * Test the result of two subsequent requests nearly identical requests
    * (used here for requesting different number of properties to be displayed)
    * @param search : search query as a string
    * @return : nothing, assert is called within this function
    */
  private def checkSearchWithDifferentNumberOfProperties(search: String): Assertion = {

    Get(search) ~> resourcesPathV1 ~> check {

      assert(status == StatusCodes.OK, response.toString)

      val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
      val resources = responseJson
        .fields("resources")
        .asInstanceOf[JsArray]
        .elements

      val labels: Set[String] = resources.map { element =>
        element.asJsObject.fields("value").asInstanceOf[JsArray].elements.head.asInstanceOf[JsString].value
      }.toSet

      val expectedLabels = Set(
        "A thing that has a BCE date value",
        "A thing that has a list value",
        "A thing that has two list values",
        "visible thing with hidden int values"
      )

      assert(expectedLabels.subsetOf(labels))
    }
  }

  "The Resources Endpoint" should {
    "provide a HTML representation of the resource properties " in {
      /* Incunabula resources*/

      /* A Book without a preview image */
      Get(
        "/v1/resources.html/http%3A%2F%2Frdfh.ch%2Fresources%2F7dGkt1CLKdZbrxVj324eaw?noresedit=true&reqtype=properties") ~> resourcesPathV1 ~> check {
        //log.debug("==>> " + responseAs[String])
        assert(status === StatusCodes.OK)
        assert(responseAs[String] contains "Physical description")
        assert(responseAs[String] contains "Location")
        assert(responseAs[String] contains "Publication location")
        assert(responseAs[String] contains "URI")
        assert(responseAs[String] contains "Title")
        assert(responseAs[String] contains "Datum der Herausgabe")
        assert(responseAs[String] contains "Citation/reference")
        assert(responseAs[String] contains "Publisher")
      }

      /* A Page with a preview image */
      Get(
        "/v1/resources.html/http%3A%2F%2Frdfh.ch%2Fresources%2FP7tF3-BXQH6xli9mw45jIQ?noresedit=true&reqtype=properties") ~> resourcesPathV1 ~> check {
        //log.debug("==>> " + responseAs[String])
        assert(status === StatusCodes.OK)
        assert(responseAs[String] contains "preview")
        assert(responseAs[String] contains "Original filename")
        assert(responseAs[String] contains "Page identifier")
      }
    }

    "get the regions of a page when doing a context query with resinfo set to true" in {

      Get("/v1/resources/http%3A%2F%2Frdfh.ch%2Fresources%2FfE-I7Nv6TG2is88g_nYXSw?resinfo=true&reqtype=context") ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val responseJson: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields
        val resourceContext: Map[String, JsValue] = responseJson("resource_context").asJsObject.fields
        val resinfo: Map[String, JsValue] = resourceContext("resinfo").asJsObject.fields

        resinfo.get("regions") match {
          case Some(JsArray(regionsVector)) =>
            val regions: Vector[PropsGetForRegionV1] = regionsVector.map(_.convertTo[PropsGetForRegionV1])

            val region1 = regions.filter { region =>
              region.res_id == "http://rdfh.ch/resources/i4OGCOEvQ6CEiaLpUWZ7Qg"
            }

            val region2 = regions.filter { region =>
              region.res_id == "http://rdfh.ch/resources/IBkTWwgaSsqCmOGa_uOUsw"
            }

            assert(region1.length == 1, "No region found with Iri 'http://rdfh.ch/resources/i4OGCOEvQ6CEiaLpUWZ7Qg'")

            assert(region2.length == 1, "No region found with Iri 'http://rdfh.ch/resources/IBkTWwgaSsqCmOGa_uOUsw'")

          case None => throw AssertionException("No regions given, but 2 were expected")
          case _    => throw AssertionException("No valid regions given")
        }
      }
    }

    "create a resource of type 'images:person' in 'images' project" in {

      val params =
        s"""
                   |{
                   |    "restype_id": "$IMAGES_ONTOLOGY_IRI#person",
                   |    "label": "Testperson",
                   |    "project_id": "$IMAGES_PROJECT_IRI",
                   |    "properties": {
                   |        "$IMAGES_ONTOLOGY_IRI#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
                   |        "$IMAGES_ONTOLOGY_IRI#firstname": [{"richtext_value":{"utf8str":"Name"}}]
                   |    }
                   |}
                """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(imagesUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)
      }
    }

    "get a resource of type 'knora-base:Resource' with text with standoff" in {

      val expectedXML =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text><p>Derselbe Holzschnitt wird auf Seite <a href="http://rdfh.ch/resources/BXp1vFkyR2GY2due2Xgj5w" class="salsah-link">c7r</a> der lateinischen Ausgabe des Narrenschiffs verwendet.</p></text>
                """.stripMargin

      Get("/v1/resources/http%3A%2F%2Frdfh.ch%2Fresources%2FyfyTM5lBR6GSbTJtQsQBOw") ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/knora-base#hasComment")

        val xml: String = text match {
          case vals: JsArray =>
            vals.elements.head.asJsObject.fields("xml") match {
              case JsString(xml: String) => xml
              case _                     => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        // Compare the original XML with the regenerated XML.
        val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(expectedXML)).withTest(Input.fromString(xml)).build()

        xmlDiff.hasDifferences should be(false)
      }
    }

    "get a resource of type 'anything:thing' with two text with standoff" in {

      val expectedXML1 =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>Na ja, die <a href="http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA" class="salsah-link">Dinge</a> sind OK.</text>
                """.stripMargin

      val expectedXML2 =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>Ich liebe die <a href="http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA" class="salsah-link">Dinge</a>, sie sind alles für mich.</text>
                """.stripMargin

      Get("/v1/resources/http%3A%2F%2Frdfh.ch%2Fresources%2FjT0UHG9_wtaX23VoYydmGg") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText")

        val textValues: Seq[JsValue] = text match {
          case vals: JsArray =>
            vals.elements
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        val xmlStrings: Seq[String] = textValues.map { textVal: JsValue =>
          textVal.asJsObject.fields("xml") match {
            case JsString(xml: String) => xml
            case _                     => throw new InvalidApiJsonException("member 'xml' not given")
          }
        }

        assert(xmlStrings.length == 2)

        // determine the index of the first and the second expected text value
        val (dingeOk: Int, allesFuerMich: Int) = if (xmlStrings.head.contains("sind OK")) {

          // expectedXML1 comes first, expectedXML2 comes second
          (0, 1)

        } else {

          // expectedXML1 comes second, expectedXML2 comes first
          (1, 0)
        }

        // Compare the original XML with the regenerated XML.
        val xmlDiff1: Diff =
          DiffBuilder.compare(Input.fromString(expectedXML1)).withTest(Input.fromString(xmlStrings(dingeOk))).build()
        val xmlDiff2: Diff = DiffBuilder
          .compare(Input.fromString(expectedXML2))
          .withTest(Input.fromString(xmlStrings(allesFuerMich)))
          .build()

        xmlDiff1.hasDifferences should be(false)
        xmlDiff2.hasDifferences should be(false)
      }
    }

    "perform a search for an anything:Thing matching 'thing'" in {

      checkSearchWithDifferentNumberOfProperties(search + filter)

    }

    "perform a search for an anything:Thing matching 'thing' with 2 numprops displayed" in {

      checkSearchWithDifferentNumberOfProperties(search + filter + "&numprops=2")

    }

    "create a first resource of type anything:Thing" in {

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml": ${xml1.toJson.compactPrint}, "mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}],
                   |        "http://www.knora.org/ontology/0001/anything#hasTimeStamp": [{"time_value": "2019-08-28T15:13:10.968318Z"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasBoolean": [{"boolean_value":true}]
                   |    }
                   |}
                """.stripMargin

      // TODO: these properties have been commented out in the thing test ontology because of compatibility with the GUI
      // "http://www.knora.org/ontology/0001/anything#hasGeoname": [{"geoname_value": "2661602"}]
      // "http://www.knora.org/ontology/0001/anything#hasGeometry": [{"geom_value":"{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}"}],

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        firstThingIri.set(resId)
      }
    }

    "get the created resource and check its standoff in the response" in {

      Get("/v1/resources/" + URLEncoder.encode(firstThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText")

        val xml: String = text match {
          case vals: JsArray =>
            vals.elements.head.asJsObject.fields("xml") match {
              case JsString(xml: String) => xml
              case _                     => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        // Compare the original XML with the regenerated XML.
        val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xml1)).withTest(Input.fromString(xml)).build()

        xmlDiff.hasDifferences should be(false)
      }
    }

    "create a new text value for the first thing resource" in {

      val newValueParams =
        s"""
                   |{
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "res_id": "${firstThingIri.get}",
                   |    "prop": "http://www.knora.org/ontology/0001/anything#hasText",
                   |    "richtext_value": {
                   |        "xml": ${xml2.toJson.compactPrint},
                   |        "mapping_id": "$mappingIri"
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val xml = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
          case Some(value: JsObject) =>
            value.fields.get("xml") match {
              case Some(JsString(xml: String)) => xml
              case _                           => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ => throw new InvalidApiJsonException("member 'value' not given")
        }

        // Compare the original XML with the regenerated XML.
        val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xml2)).withTest(Input.fromString(xml)).build()

        xmlDiff.hasDifferences should be(false)

        val resId = getNewValueIriFromJsonResponse(response)

        firstTextValueIRI.set(resId)
      }
    }

    "change the created text value above for the first thing resource so it has a standoff link to incunabulaBookBiechlin" in {

      val xml =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<text>a <u>new</u> value with a standoff <a class="salsah-link" href="$incunabulaBookBiechlin">link</a></text>
                 """.stripMargin

      val newValueParams =
        s"""
                   |{
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "richtext_value": {
                   |        "xml": ${xml.toJson.compactPrint},
                   |        "mapping_id": "$mappingIri"
                   |    }
                   |}
                 """.stripMargin

      Put("/v1/values/" + URLEncoder.encode(firstTextValueIRI.get, "UTF-8"),
          HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val resId = getNewValueIriFromJsonResponse(response)

        firstTextValueIRI.set(resId)
      }
    }

    "make sure that the first thing resource contains a direct standoff link to incunabulaBookBiechlin now" in {

      val sparqlQuery = getDirectLinksSPARQL(firstThingIri.get)

      Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

        case response: SparqlSelectResult =>
          val ref: Boolean = response.results.bindings.exists { row: VariableResultsRow =>
            row.rowMap("referredResourceIRI") == incunabulaBookBiechlin
          }

          assert(ref, s"No direct link to '$incunabulaBookBiechlin' found")

        case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")
      }
    }

    "check that the first thing resource's standoff link reification has the correct reference count" in {

      val sparqlQuery = getRefCountsSPARQL(firstThingIri.get)

      Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

        case response: SparqlSelectResult =>
          val refCnt: Boolean = response.results.bindings.exists { row: VariableResultsRow =>
            row.rowMap("object") == incunabulaBookBiechlin &&
            row.rowMap("refCnt").toInt == 1
          }

          assert(refCnt, s"Ref count for '$incunabulaBookBiechlin' should be 1")

        case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")
      }
    }

    "create a second resource of type anything:Thing linking to the first thing via standoff" in {

      val xml =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<text>This text <a class="salsah-link" href="${firstThingIri.get}">links</a> to a thing</text>
                 """.stripMargin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id" :"$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        secondThingIri.set(resId)
      }
    }

    "get the second resource of type anything:Thing, containing the correct standoff link" in {

      Get("/v1/resources/" + URLEncoder.encode(secondThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText")

        val xmlString: String = text match {
          case vals: JsArray =>
            vals.elements.head.asJsObject.fields("xml") match {
              case JsString(xml: String) => xml
              case _                     => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        // make sure that the xml contains a link to "firstThingIri"
        val xml = XML.loadString(xmlString)

        val link: NodeSeq = xml \ "a"

        assert(link.nonEmpty)

        val target: Seq[Node] = link.head.attributes("href")

        assert(target.nonEmpty && target.head.text == firstThingIri.get)
      }
    }

    "get the first thing resource that is referred to by the second thing resource" in {

      Get("/v1/resources/" + URLEncoder.encode(firstThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        // check if this resource is referred to by the second thing resource
        val incoming = AkkaHttpUtils.httpResponseToJson(response).fields.get("incoming") match {
          case Some(incomingRefs: JsArray) => incomingRefs
          case None                        => throw InvalidApiJsonException(s"The response does not contain a field called 'incoming'")
          case other =>
            throw InvalidApiJsonException(s"The response does not contain a res_id of type JsObject, but $other")
        }

        val firstElement = incoming.elements.headOption match {
          case Some(incomingRef: JsObject) => incomingRef
          case None                        => throw NotFoundException("Field 'incoming' is empty, but one incoming reference is expected")
          case _                           => throw InvalidApiJsonException("First element in 'incoming' is not a JsObject")
        }

        firstElement.fields.get("ext_res_id") match {
          case Some(extResObj: JsObject) =>
            // get the Iri of the referring resource
            val idJsString = extResObj.fields.getOrElse("id", throw InvalidApiJsonException("No member 'id' given"))

            // get the Iri of the property pointing to this resource
            val propIriJsString =
              extResObj.fields.getOrElse("pid", throw InvalidApiJsonException("No member 'pid' given"))

            idJsString match {
              case JsString(id) =>
                assert(id == secondThingIri.get, "This resource should be referred to by the second thing resource")
              case _ => throw InvalidApiJsonException("Id is not a JsString")
            }

            propIriJsString match {
              case JsString(pid) =>
                assert(pid == OntologyConstants.KnoraBase.HasStandoffLinkTo,
                       s"This resource should be referred to by ${OntologyConstants.KnoraBase.HasStandoffLinkTo}")
              case _ => throw InvalidApiJsonException("pid is not a JsString")
            }

          case None => throw InvalidApiJsonException("Element in 'incoming' does not have a member 'ext_res_id'")
          case _    => throw InvalidApiJsonException("Element in 'incoming' is not a JsObject")
        }
      }
    }

    "not create a resource of type thing with an invalid standoff tag name" in {

      // use a tag name that is not defined in the standard mapping ("trong" instead of "strong")
      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>This <trong>text</trong></text>
                """.stripMargin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint}, "mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        // the route should reject the request because `trong` is not a tag name supported by the standard mapping
        assert(status == StatusCodes.BadRequest, response.toString)
      }
    }

    "not create a resource of type thing submitting a wrong standoff link" in {

      val xml =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<text><u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="$incunabulaBookQuadra">links</a> to <a class="salsah-link" href="http://rdfh.ch/resources/9935159f">two</a> things</text>
                 """.stripMargin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                   |
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        //println(response)

        // the route should reject the request because an IRI is wrong (formally valid though)
        assert(status == StatusCodes.NotFound, response.toString)
      }
    }

    "create a third resource of type thing with two standoff links to the same resource and a standoff link to another one" in {

      val firstXML =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<text><u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="$incunabulaBookQuadra">links</a> to a thing</text>
                 """.stripMargin

      val secondXML =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<text><u><strong>This</strong></u> <u>text</u> <a class="salsah-link" href="$incunabulaBookBiechlin">links</a> to the same thing <a class="salsah-link" href="$incunabulaBookBiechlin">twice</a> and to another <a class="salsah-link" href="$incunabulaBookQuadra">thing</a></text>
                 """.stripMargin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${firstXML.toJson.compactPrint},"mapping_id": "$mappingIri"}}, {"richtext_value":{"xml":${secondXML.toJson.compactPrint},"mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        thirdThingIri.set(resId)
      }
    }

    "check that the third thing resource has two direct standoff links" in {

      val sparqlQuery = getDirectLinksSPARQL(thirdThingIri.get)

      Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

        case response: SparqlSelectResult =>
          val ref1: Boolean = response.results.bindings.exists { row: VariableResultsRow =>
            row.rowMap("referredResourceIRI") == incunabulaBookQuadra
          }

          val ref2: Boolean = response.results.bindings.exists { row: VariableResultsRow =>
            row.rowMap("referredResourceIRI") == incunabulaBookBiechlin
          }

          assert(ref1, s"No direct link to '$incunabulaBookQuadra' found")

          assert(ref2, s"No direct link to '$incunabulaBookBiechlin' found")

        case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")
      }
    }

    "check that the third thing resource's standoff link reifications have the correct reference counts" in {

      val sparqlQuery = getRefCountsSPARQL(thirdThingIri.get)

      Await.result(storeManager ? SparqlSelectRequest(sparqlQuery), 30.seconds) match {

        case response: SparqlSelectResult =>
          val refCnt1: Boolean = response.results.bindings.exists { row: VariableResultsRow =>
            row.rowMap("object") == incunabulaBookQuadra &&
            row.rowMap("refCnt").toInt == 2
          }

          val refCnt2: Boolean = response.results.bindings.exists { row: VariableResultsRow =>
            row.rowMap("object") == incunabulaBookBiechlin &&
            row.rowMap("refCnt").toInt == 1
          }

          assert(refCnt1, s"Ref count for '$incunabulaBookQuadra' should be 2")

          assert(refCnt2, s"Ref count for '$incunabulaBookBiechlin' should be 1")

        case _ => throw TriplestoreResponseException("Expected a SparqlSelectResponse")
      }
    }

    "mark a resource as deleted" in {

      Delete(
        "/v1/resources/http%3A%2F%2Frdfh.ch%2Fresources%2FfE-I7Nv6TG2is88g_nYXSw?deleteComment=deleted%20for%20testing") ~> addCredentials(
        BasicHttpCredentials(incunabulaUserEmail2, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)
      }
    }

    "create a fourth resource of type anything:Thing with a hyperlink in standoff" in {

      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>This text links to <a href="http://www.google.ch">Google</a>.</text>
                """.stripMargin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id":"$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        fourthThingIri.set(resId)
      }
    }

    "get the fourth resource of type anything:Thing, containing the hyperlink in standoff" in {

      Get("/v1/resources/" + URLEncoder.encode(fourthThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText")

        val xmlString: String = text match {
          case vals: JsArray =>
            vals.elements.head.asJsObject.fields("xml") match {
              case JsString(xml: String) => xml
              case _                     => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        // make sure that the xml contains a link to http://www.google.ch
        val xml = XML.loadString(xmlString)

        val link: NodeSeq = xml \ "a"

        assert(link.nonEmpty)

        val target: Seq[Node] = link.head.attributes("href")

        assert(target.nonEmpty && target.head.text == "http://www.google.ch")
      }
    }

    "create a fifth resource of type anything:Thing with various standoff markup including internal links and hyperlinks" in {

      // xml3 contains a link to google.ch and to incunabulaBookBiechlin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${xml3.toJson.compactPrint}, "mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        fifthThingIri.set(resId)
      }
    }

    "get the fifth resource of type anything:Thing, containing various standoff markup" in {

      Get("/v1/resources/" + URLEncoder.encode(fifthThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText")

        val xmlString: String = text match {
          case vals: JsArray =>
            vals.elements.head.asJsObject.fields("xml") match {
              case JsString(xml: String) => xml
              case _                     => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        // make sure that the correct standoff links and references
        // xml3 contains a link to google.ch and to incunabulaBookBiechlin

        val xml = XML.loadString(xmlString)

        val links: NodeSeq = xml \ "a"

        // there should be two links
        assert(links.length == 2)

        assert(links.head.attributes.asAttrMap.keySet.size == 1) // The URL has no class attribute
        val linkToGoogle: Seq[Node] = links.head.attributes("href")

        assert(linkToGoogle.nonEmpty && linkToGoogle.head.text == "http://www.google.ch")

        assert(links(1).attributes.asAttrMap("class") == "salsah-link") // The link to a resource IRI has class="salsah-link"
        val linkKnoraResource: Seq[Node] = links(1).attributes("href")

        assert(linkKnoraResource.nonEmpty && linkKnoraResource.head.text == incunabulaBookBiechlin)

        // Compare the original XML with the regenerated XML.
        val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xmlString)).withTest(Input.fromString(xml3)).build()

        xmlDiff.hasDifferences should be(false)
      }
    }

    "create a sixth resource of type anything:Thing with internal links to two different resources" in {

      // xml4 contains a link to google.ch, to incunabulaBookBiechlin and to incunabulaBookQuadra

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml": ${xml4.toJson.compactPrint},"mapping_id": "$mappingIri"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}],
                   |        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://rdfh.ch/lists/0001/treeList10"}],
                   |        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        sixthThingIri.set(resId)
      }
    }

    "get the sixth resource of type anything:Thing with internal links to two different resources" in {

      Get("/v1/resources/" + URLEncoder.encode(sixthThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val text: JsValue = getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText")

        val xmlString: String = text match {
          case vals: JsArray =>
            vals.elements.head.asJsObject.fields("xml") match {
              case JsString(xml: String) => xml
              case _                     => throw new InvalidApiJsonException("member 'xml' not given")
            }
          case _ =>
            throw new InvalidApiJsonException("values is not an array")
        }

        // make sure that the correct standoff links and references
        // xml4 contains a link to google.ch, to incunabulaBookBiechlin and to incunabulaBookQuadra
        val xml = XML.loadString(xmlString)

        val links: NodeSeq = xml \ "a"

        // there should be three links
        assert(links.length == 3)

        val linkToGoogle: Seq[Node] = links.head.attributes("href")

        assert(linkToGoogle.nonEmpty && linkToGoogle.head.text == "http://www.google.ch")

        val linkKnoraResource: Seq[Node] = links(1).attributes("href")

        assert(linkKnoraResource.nonEmpty && linkKnoraResource.head.text == incunabulaBookBiechlin)

        val linkKnoraResource2: Seq[Node] = links(2).attributes("href")

        assert(linkKnoraResource2.nonEmpty && linkKnoraResource2.head.text == incunabulaBookQuadra)

        // Compare the original XML with the regenerated XML.
        val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(xmlString)).withTest(Input.fromString(xml4)).build()

        xmlDiff.hasDifferences should be(false)
      }
    }

    "change a resource's label" in {

      val newLabel = "my new label"

      val params =
        s"""
                   |{
                   |    "label": "$newLabel"
                   |}
                 """.stripMargin

      Put(
        "/v1/resources/label/" + URLEncoder.encode("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", "UTF-8"),
        HttpEntity(ContentTypes.`application/json`, params)
      ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val label = AkkaHttpUtils.httpResponseToJson(response).fields.get("label") match {
          case Some(JsString(str)) => str
          case None                => throw InvalidApiJsonException(s"The response does not contain a field called 'label'")
          case other =>
            throw InvalidApiJsonException(s"The response does not contain a label of type JsString, but $other")
        }

        assert(label == newLabel, "label has not been updated correctly")
      }
    }

    "create a resource of type anything:Thing with a link (containing a comment) to another resource" in {

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A thing with a link value that has a comment",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value": {"utf8str": "simple text"}}],
                   |        "http://www.knora.org/ontology/0001/anything#hasOtherThing": [{"link_value":"${sixthThingIri.get}", "comment":"$notTheMostBoringComment"}]
                   |    }
                   }
                """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        seventhThingIri.set(resId)
      }
    }

    "get the created resource and check the comment on the link value" in {

      Get("/v1/resources/" + URLEncoder.encode(seventhThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val targetResourceIri: String =
          getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasOtherThing") match {
            case vals: JsArray =>
              vals.elements.head.asInstanceOf[JsString].value
            case _ =>
              throw new InvalidApiJsonException("values is not an array")
          }

        assert(targetResourceIri == sixthThingIri.get)

        val linkValueComment: String =
          getCommentsForProp(response, "http://www.knora.org/ontology/0001/anything#hasOtherThing") match {
            case vals: JsArray =>
              vals.elements.head.asInstanceOf[JsString].value
            case _ =>
              throw new InvalidApiJsonException("comments is not an array")
          }

        assert(linkValueComment == notTheMostBoringComment)
      }
    }

    "add a simple TextValue to the seventh resource" in {

      val newValueParams =
        s"""
                   |{
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "res_id": "${seventhThingIri.get}",
                   |    "prop": "http://www.knora.org/ontology/0001/anything#hasText",
                   |    "richtext_value": {
                   |        "utf8str": "another simple text"
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val utf8str = AkkaHttpUtils.httpResponseToJson(response).fields.get("value") match {
          case Some(value: JsObject) =>
            value.fields.get("utf8str") match {
              case Some(JsString(xml: String)) => xml
              case _                           => throw new InvalidApiJsonException("member 'utf8str' not given")
            }
          case _ => throw new InvalidApiJsonException("member 'value' not given")
        }

        assert(utf8str == "another simple text")
      }
    }

    "create eighth resource of type anything:Thing with the date of the murder of Caesar" in {

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A thing with a BCE date of the murder of Caesar",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value": "JULIAN:44-03-15 BCE"}]
                   |    }
                   }
                """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        eighthThingIri.set(resId)
      }
    }

    "get the eighth resource and check its date" in {

      Get("/v1/resources/" + URLEncoder.encode(eighthThingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val dateObj: JsObject =
          getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasDate") match {
            case vals: JsArray =>
              vals.elements.head.asInstanceOf[JsObject]
            case _ =>
              throw new InvalidApiJsonException("values is not an array")
          }

        // expected result:
        // {"dateval1":"0044-03-15","calendar":"JULIAN","era1":"BCE","dateval2":"0044-03-15","era2":"BCE"}

        dateObj.fields.get("dateval1") match {
          case Some(JsString(dateval1)) => assert(dateval1 == "0044-03-15")

          case None => throw InvalidApiJsonException("No member 'dateval1' given for date value")

          case _ => throw InvalidApiJsonException("'dateval1' is not a JsString")

        }

        dateObj.fields.get("era1") match {
          case Some(JsString(era1)) => assert(era1 == "BCE")

          case None => throw InvalidApiJsonException("No member 'era1' given for date value")

          case _ => throw InvalidApiJsonException("'era1' is not a JsString")

        }

        dateObj.fields.get("dateval2") match {
          case Some(JsString(dateval1)) => assert(dateval1 == "0044-03-15")

          case None => throw InvalidApiJsonException("No member 'dateval1' given for date value")

          case _ => throw InvalidApiJsonException("'dateval1' is not a JsString")

        }

        dateObj.fields.get("era2") match {
          case Some(JsString(era2)) => assert(era2 == "BCE")

          case None => throw InvalidApiJsonException("No member 'era2' given for date value")

          case _ => throw InvalidApiJsonException("'era2' is not a JsString")

        }

      }

    }

    "create resources from an XML import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0801/biblio/xml-import/v1# p0801-biblio.xsd"
                   |    xmlns:p0801-biblio="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
                   |    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0801-beol:person id="abel">
                   |        <knoraXmlImport:label>Niels Henrik Abel</knoraXmlImport:label>
                   |        <p0801-beol:hasFamilyName knoraType="richtext_value">Abel</p0801-beol:hasFamilyName>
                   |        <p0801-beol:hasGivenName knoraType="richtext_value">Niels Henrik</p0801-beol:hasGivenName>
                   |        <p0801-beol:personHasTitle knoraType="richtext_value" lang="en">Sir</p0801-beol:personHasTitle>
                   |    </p0801-beol:person>
                   |    <p0801-beol:person id="holmes">
                   |        <knoraXmlImport:label>Sherlock Holmes</knoraXmlImport:label>
                   |        <p0801-beol:hasFamilyName knoraType="richtext_value">Holmes</p0801-beol:hasFamilyName>
                   |        <p0801-beol:hasGivenName knoraType="richtext_value">Sherlock</p0801-beol:hasGivenName>
                   |    </p0801-beol:person>
                   |    <p0801-biblio:Journal id="math_intelligencer">
                   |        <knoraXmlImport:label>Math Intelligencer</knoraXmlImport:label>
                   |        <p0801-biblio:hasName knoraType="richtext_value">Math Intelligencer</p0801-biblio:hasName>
                   |    </p0801-biblio:Journal>
                   |    <p0801-biblio:JournalArticle id="strings_in_the_16th_and_17th_centuries">
                   |        <knoraXmlImport:label>Strings in the 16th and 17th Centuries</knoraXmlImport:label>
                   |        <p0801-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="$mappingIri">
                   |            <text xmlns="">The most <strong>interesting</strong> article in <a class="salsah-link" href="ref:math_intelligencer">Math Intelligencer</a>.</text>
                   |        </p0801-biblio:p0801-beol__comment>
                   |        <p0801-biblio:endPage knoraType="richtext_value">73</p0801-biblio:endPage>
                   |        <p0801-biblio:isPartOfJournal>
                   |            <p0801-biblio:Journal knoraType="link_value" target="math_intelligencer" linkType="ref"/>
                   |        </p0801-biblio:isPartOfJournal>
                   |        <p0801-biblio:journalVolume knoraType="richtext_value">27</p0801-biblio:journalVolume>
                   |        <p0801-biblio:publicationHasAuthor>
                   |            <p0801-beol:person knoraType="link_value" linkType="ref" target="abel"/>
                   |        </p0801-biblio:publicationHasAuthor>
                   |        <p0801-biblio:publicationHasAuthor>
                   |            <p0801-beol:person knoraType="link_value" linkType="ref" target="holmes"/>
                   |        </p0801-biblio:publicationHasAuthor>
                   |        <p0801-biblio:publicationHasDate knoraType="date_value">GREGORIAN:500 BC:400 BC</p0801-biblio:publicationHasDate>
                   |        <p0801-biblio:publicationHasTitle knoraType="richtext_value">Strings in the 16th and 17th Centuries</p0801-biblio:publicationHasTitle>
                   |        <p0801-biblio:publicationHasTitle knoraType="richtext_value">An alternate title</p0801-biblio:publicationHasTitle>
                   |        <p0801-biblio:startPage knoraType="richtext_value">48</p0801-biblio:startPage>
                   |    </p0801-biblio:JournalArticle>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(beolUserEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr: String = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")

        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
        val createdResources: Seq[JsValue] = responseJson.fields("createdResources").asInstanceOf[JsArray].elements
        abelAuthorIri.set(createdResources.head.asJsObject.fields("resourceIri").asInstanceOf[JsString].value)
        mathIntelligencerIri.set(createdResources(2).asJsObject.fields("resourceIri").asInstanceOf[JsString].value)
      }
    }

    "reject XML import data that fails schema validation" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0801/biblio/xml-import/v1# p0801-biblio.xsd"
                   |    xmlns:p0801-biblio="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
                   |    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0801-beol:person id="abel">
                   |        <knoraXmlImport:label>Niels Henrik Abel</knoraXmlImport:label>
                   |        <p0801-beol:hasFamilyName knoraType="richtext_value">Abel</p0801-beol:hasFamilyName>
                   |        <p0801-beol:hasGivenName knoraType="richtext_value">Niels Henrik</p0801-beol:hasGivenName>
                   |    </p0801-beol:person>
                   |    <p0801-beol:person id="holmes">
                   |        <knoraXmlImport:label>Sherlock Holmes</knoraXmlImport:label>
                   |        <p0801-beol:hasFamilyName knoraType="richtext_value">Holmes</p0801-beol:hasFamilyName>
                   |        <p0801-beol:hasGivenName knoraType="richtext_value">Sherlock</p0801-beol:hasGivenName>
                   |    </p0801-beol:person>
                   |    <p0801-biblio:Journal id="math_intelligencer">
                   |        <knoraXmlImport:label>Math Intelligencer</knoraXmlImport:label>
                   |        <p0801-biblio:hasName knoraType="richtext_value">Math Intelligencer</p0801-biblio:hasName>
                   |    </p0801-biblio:Journal>
                   |    <p0801-biblio:JournalArticle id="strings_in_the_16th_and_17th_centuries">
                   |        <knoraXmlImport:label>Strings in the 16th and 17th Centuries</knoraXmlImport:label>
                   |        <p0801-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="$mappingIri">
                   |            <text xmlns="">The most <strong>interesting</strong> article in <a class="salsah-link" href="ref:math_intelligencer">Math Intelligencer</a>.</text>
                   |        </p0801-biblio:p0801-beol__comment>
                   |        <p0801-biblio:endPage knoraType="richtext_value">73</p0801-biblio:endPage>
                   |        <p0801-biblio:isPartOfJournal>
                   |            <p0801-biblio:Journal knoraType="link_value" target="math_intelligencer" linkType="ref"/>
                   |        </p0801-biblio:isPartOfJournal>
                   |        <p0801-biblio:journalVolume knoraType="richtext_value">27</p0801-biblio:journalVolume>
                   |        <p0801-biblio:publicationHasAuthor>
                   |            <p0801-beol:person knoraType="link_value" linkType="ref" target="abel"/>
                   |        </p0801-biblio:publicationHasAuthor>
                   |        <p0801-biblio:publicationHasAuthor>
                   |            <p0801-beol:person knoraType="link_value" linkType="ref" target="holmes"/>
                   |        </p0801-biblio:publicationHasAuthor>
                   |        <p0801-biblio:publicationHasDate knoraType="date_value">GREGORIAN:19foo76</p0801-biblio:publicationHasDate>
                   |        <p0801-biblio:publicationHasTitle knoraType="richtext_value" lang="en">Strings in the 16th and 17th Centuries</p0801-biblio:publicationHasTitle>
                   |        <p0801-biblio:publicationHasTitle knoraType="richtext_value">An alternate title</p0801-biblio:publicationHasTitle>
                   |        <p0801-biblio:startPage knoraType="richtext_value">48</p0801-biblio:startPage>
                   |    </p0801-biblio:JournalArticle>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(beolUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.BadRequest, response.toString)
        val responseStr = responseAs[String]
        responseStr should include("org.xml.sax.SAXParseException")
        responseStr should include("cvc-pattern-valid")
      }
    }

    "refer to existing resources in an XML import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0801/biblio/xml-import/v1# p0801-biblio.xsd"
                   |    xmlns:p0801-biblio="http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
                   |    xmlns:p0801-beol="http://api.knora.org/ontology/0801/beol/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0801-biblio:JournalArticle id="strings_in_the_18th_century">
                   |        <knoraXmlImport:label>Strings in the 18th Century</knoraXmlImport:label>
                   |        <p0801-biblio:p0801-beol__comment knoraType="richtext_value" mapping_id="$mappingIri">
                   |            <text xmlns="">The most <strong>boring</strong> article in <a class="salsah-link" href="${mathIntelligencerIri.get}">Math Intelligencer</a>.</text>
                   |        </p0801-biblio:p0801-beol__comment>
                   |        <p0801-biblio:endPage knoraType="richtext_value">76</p0801-biblio:endPage>
                   |        <p0801-biblio:isPartOfJournal>
                   |            <p0801-biblio:Journal knoraType="link_value" linkType="iri" target="${mathIntelligencerIri.get}"/>
                   |        </p0801-biblio:isPartOfJournal>
                   |        <p0801-biblio:journalVolume knoraType="richtext_value">27</p0801-biblio:journalVolume>
                   |        <p0801-biblio:publicationHasAuthor>
                   |            <p0801-beol:person knoraType="link_value" linkType="iri" target="${abelAuthorIri.get}"/>
                   |        </p0801-biblio:publicationHasAuthor>
                   |        <p0801-biblio:publicationHasDate knoraType="date_value">GREGORIAN:1977</p0801-biblio:publicationHasDate>
                   |        <p0801-biblio:publicationHasTitle knoraType="richtext_value">Strings in the 18th Century</p0801-biblio:publicationHasTitle>
                   |        <p0801-biblio:startPage knoraType="richtext_value">52</p0801-biblio:startPage>
                   |    </p0801-biblio:JournalArticle>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(beolUserEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")
      }
    }

    "create an anything:Thing with all data types from an XML import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:Thing id="test_thing">
                   |        <knoraXmlImport:label>These are a few of my favorite things</knoraXmlImport:label>
                   |        <p0001-anything:hasBoolean knoraType="boolean_value">true</p0001-anything:hasBoolean>
                   |        <p0001-anything:hasColor knoraType="color_value">#4169E1</p0001-anything:hasColor>
                   |        <p0001-anything:hasDate knoraType="date_value">JULIAN:1291-08-01:1291-08-01</p0001-anything:hasDate>
                   |        <p0001-anything:hasDecimal knoraType="decimal_value">5.6</p0001-anything:hasDecimal>
                   |        <p0001-anything:hasInteger knoraType="int_value">12345</p0001-anything:hasInteger>
                   |        <p0001-anything:hasInterval knoraType="interval_value">1000000000000000.0000000000000001,1000000000000000.0000000000000002</p0001-anything:hasInterval>
                   |        <p0001-anything:hasListItem knoraType="hlist_value">http://rdfh.ch/lists/0001/treeList10</p0001-anything:hasListItem>
                   |        <p0001-anything:hasOtherThing>
                   |            <p0001-anything:Thing knoraType="link_value" linkType="iri" target="${sixthThingIri.get}"/>
                   |        </p0001-anything:hasOtherThing>
                   |        <p0001-anything:hasText knoraType="richtext_value">This is a test.</p0001-anything:hasText>
                   |        <p0001-anything:hasUri knoraType="uri_value">http://dhlab.unibas.ch</p0001-anything:hasUri>
                   |    </p0001-anything:Thing>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")
      }
    }

    "not create an anything:Thing in the incunabula project in a bulk import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:Thing id="test_thing">
                   |        <knoraXmlImport:label>These are a few of my favorite things</knoraXmlImport:label>
                   |        <p0001-anything:hasText knoraType="richtext_value">This is a test.</p0001-anything:hasText>
                   |    </p0001-anything:Thing>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0803", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(incunabulaUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.BadRequest, response.toString)
        val responseStr = responseAs[String]
        responseStr should include("not shared")
      }
    }

    "not create a resource in a shared ontologies project in a bulk import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/shared/example-box/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/shared/example-box/xml-import/v1# p0000-example-box.xsd"
                   |    xmlns:p0000-example-box="http://api.knora.org/ontology/shared/example-box/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0000-example-box:Box id="test_box">
                   |        <knoraXmlImport:label>test box</knoraXmlImport:label>
                   |        <p0000-example-box:hasName knoraType="richtext_value">This is a test.</p0000-example-box:hasName>
                   |    </p0000-example-box:Box>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri =
        URLEncoder.encode("http://www.knora.org/ontology/knora-admin#DefaultSharedOntologiesProject", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(superUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.BadRequest, response.toString)
        val responseStr = responseAs[String]
        responseStr should include("Resources cannot be created in project")
      }
    }

    "create a resource in the incunabula project using a class from the default shared ontologies project" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/shared/example-box/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/shared/example-box/xml-import/v1# p0000-example-box.xsd"
                   |    xmlns:p0000-example-box="http://api.knora.org/ontology/shared/example-box/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0000-example-box:Box id="test_box">
                   |        <knoraXmlImport:label>test box</knoraXmlImport:label>
                   |        <p0000-example-box:hasName knoraType="richtext_value">This is a test.</p0000-example-box:hasName>
                   |    </p0000-example-box:Box>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0803", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(incunabulaUserEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")
      }
    }

    "use a knora-base property directly in a bulk import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:ThingWithSeqnum id="thing_with_seqnum">
                   |        <knoraXmlImport:label>Thing with seqnum</knoraXmlImport:label>
                   |        <p0001-anything:knoraXmlImport__seqnum knoraType="int_value">3</p0001-anything:knoraXmlImport__seqnum>
                   |    </p0001-anything:ThingWithSeqnum>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")
      }
    }

    "serve a Zip file containing XML schemas for validating an XML import" in {
      val ontologyIri = URLEncoder.encode("http://www.knora.org/ontology/0801/biblio", "UTF-8")

      Get(s"/v1/resources/xmlimportschemas/$ontologyIri") ~> addCredentials(
        BasicHttpCredentials(beolUserEmail, password)) ~> resourcesPathV1 ~> check {
        val responseBodyFuture: Future[Array[Byte]] = response.entity.toStrict(5.seconds).map(_.data.toArray)
        val responseBytes: Array[Byte] = Await.result(responseBodyFuture, 5.seconds)
        val zippedFilenames = collection.mutable.Set.empty[String]
        val zipInputStream = new ZipInputStream(new ByteArrayInputStream(responseBytes))
        var zipEntry: ZipEntry = null

        while ({
          zipEntry = zipInputStream.getNextEntry
          zipEntry != null
        }) {
          zippedFilenames.add(zipEntry.getName)
        }

        zipInputStream.close()
        assert(zippedFilenames == Set("p0801-beol.xsd", "p0801-biblio.xsd", "knoraXmlImport.xsd"))
      }
    }

    "consider inherited cardinalities when generating XML schemas for referenced ontologies in an XML import" in {
      val ontologyIri = URLEncoder.encode("http://www.knora.org/ontology/0001/something", "UTF-8")

      Get(s"/v1/resources/xmlimportschemas/$ontologyIri") ~> addCredentials(
        BasicHttpCredentials(beolUserEmail, password)) ~> resourcesPathV1 ~> check {
        val responseBodyFuture: Future[Array[Byte]] = response.entity.toStrict(5.seconds).map(_.data.toArray)
        val responseBytes: Array[Byte] = Await.result(responseBodyFuture, 5.seconds)
        val zippedFilenames = collection.mutable.Set.empty[String]

        val zipInputStream = new ZipInputStream(new ByteArrayInputStream(responseBytes))
        var zipEntry: ZipEntry = null

        while ({
          zipEntry = zipInputStream.getNextEntry
          zipEntry != null
        }) {
          zippedFilenames.add(zipEntry.getName)
        }

        zipInputStream.close()
        assert(zippedFilenames == Set("p0001-something.xsd", "knoraXmlImport.xsd", "p0001-anything.xsd"))
      }
    }

    "follow rdfs:subClassOf when generating XML schemas for referenced ontologies in an XML import" in {
      val ontologyIri = URLEncoder.encode("http://www.knora.org/ontology/0001/empty-thing", "UTF-8")

      Get(s"/v1/resources/xmlimportschemas/$ontologyIri") ~> addCredentials(
        BasicHttpCredentials(beolUserEmail, password)) ~> resourcesPathV1 ~> check {
        val responseBodyFuture: Future[Array[Byte]] = response.entity.toStrict(5.seconds).map(_.data.toArray)
        val responseBytes: Array[Byte] = Await.result(responseBodyFuture, 5.seconds)
        val zippedFilenames = collection.mutable.Set.empty[String]
        val zipInputStream = new ZipInputStream(new ByteArrayInputStream(responseBytes))
        var zipEntry: ZipEntry = null

        while ({
          zipEntry = zipInputStream.getNextEntry
          zipEntry != null
        }) {
          zippedFilenames.add(zipEntry.getName)
        }

        zipInputStream.close()
        assert(zippedFilenames == Set("p0001-empty-thing.xsd", "knoraXmlImport.xsd", "p0001-anything.xsd"))
      }
    }

    "create 10,000 anything:Thing resources with random contents" in {
      def maybeAppendValue(random: Random, xmlStringBuilder: StringBuilder, value: String): Unit = {
        if (random.nextBoolean()) {
          xmlStringBuilder.append(value)
        }
      }

      val xmlStringBuilder = new StringBuilder
      val random = new Random

      xmlStringBuilder.append(
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                  |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# anything.xsd"
                  |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                  |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                  |
                """.stripMargin)

      for (i <- 1 to 10000) {
        xmlStringBuilder.append(s"""
                       |<p0001-anything:Thing id="test_thing_$i">
                       |<knoraXmlImport:label>This is thing $i</knoraXmlImport:label>
                    """.stripMargin)

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = """
                          |<p0001-anything:hasBoolean knoraType="boolean_value">true</p0001-anything:hasBoolean>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = """
                          |<p0001-anything:hasColor knoraType="color_value">#4169E1</p0001-anything:hasColor>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = """
                          |<p0001-anything:hasDate knoraType="date_value">JULIAN:1291-08-01:1291-08-01</p0001-anything:hasDate>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = s"""
                           |<p0001-anything:hasDecimal knoraType="decimal_value">$i.$i</p0001-anything:hasDecimal>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = s"""
                           |<p0001-anything:hasInteger knoraType="int_value">$i</p0001-anything:hasInteger>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value =
            """
                          |<p0001-anything:hasInterval knoraType="interval_value">1000000000000000.0000000000000001,1000000000000000.0000000000000002</p0001-anything:hasInterval>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value =
            """
                          |<p0001-anything:hasListItem knoraType="hlist_value">http://rdfh.ch/lists/0001/treeList10</p0001-anything:hasListItem>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = s"""
                           |<p0001-anything:hasText knoraType="richtext_value">This is a test in thing $i.</p0001-anything:hasText>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = s"""
                           |<p0001-anything:hasTimeStamp knoraType="time_value">2019-08-28T15:13:10.968318Z</p0001-anything:hasTimeStamp>
                        """.stripMargin
        )

        maybeAppendValue(
          random = random,
          xmlStringBuilder = xmlStringBuilder,
          value = """
                          |<p0001-anything:hasUri knoraType="uri_value">http://dhlab.unibas.ch</p0001-anything:hasUri>
                        """.stripMargin
        )

        xmlStringBuilder.append("""
                      |</p0001-anything:Thing>
                    """.stripMargin)
      }

      xmlStringBuilder.append("""
                  |</knoraXmlImport:resources>
                """.stripMargin)

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(
        s"/v1/resources/xmlimport/$projectIri",
        HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlStringBuilder.toString)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")
      }
    }

    "create a resource of type anything:Thing with textValue which has language" in {

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "Ein Ding auf deutsch",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |      "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value": {"utf8str": "Ein deutscher Text", "language": "de"}}]
                   |    }
                   }
                """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)

        deutschesDingIri.set(resId)
      }
    }

    "get the deutschesDing Resource and check its textValue" in {

      Get("/v1/resources/" + URLEncoder.encode(deutschesDingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val textObj: JsObject =
          getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText") match {
            case vals: JsArray =>
              vals.elements.head.asInstanceOf[JsObject]
            case _ =>
              throw new InvalidApiJsonException("values is not an array")
          }

        textObj.fields.get("utf8str") match {
          case Some(JsString(textVal)) => assert(textVal == "Ein deutscher Text")

          case _ => throw InvalidApiJsonException("'utf8str' is not a JsString")

        }

        textObj.fields.get("language") match {
          case Some(JsString(lang)) => assert(lang == "de")

          case _ => throw InvalidApiJsonException("'lang' is not a JsString")

        }

      }

    }

    "get the resource created by bulk import and check language of its textValue" in {

      Get("/v1/resources/" + URLEncoder.encode(abelAuthorIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val textObj: JsObject =
          getValuesForProp(response, "http://www.knora.org/ontology/0801/beol#personHasTitle") match {
            case vals: JsArray =>
              vals.elements.head.asInstanceOf[JsObject]
            case _ =>
              throw new InvalidApiJsonException("values is not an array")
          }

        textObj.fields.get("utf8str") match {
          case Some(JsString(textVal)) => assert(textVal == "Sir")

          case _ => throw InvalidApiJsonException("'utf8str' is not a JsString")

        }

        textObj.fields.get("language") match {
          case Some(JsString(lang)) => assert(lang == "en")

          case _ => throw InvalidApiJsonException("'lang' is not a JsString")

        }
      }

    }

    "create a resource of type anything:Thing with textValueWithStandoff which has language" in {

      val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
                  |<text>This text links to <a href="http://www.google.ch">Google</a>.</text>
                """.stripMargin

      val params =
        s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
                   |    "label": "A second thing",
                   |    "project_id": "http://rdfh.ch/projects/0001",
                   |    "properties": {
                   |        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"xml":${xml.toJson.compactPrint},"mapping_id":"$mappingIri", "language": "en"}}]
                   |    }
                   |}
                 """.stripMargin

      Post("/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)

        val resId = getResIriFromJsonResponse(response)
        standoffLangDingIri.set(resId)
      }
    }

    "get the Resource with standoff and language and check its textValue" in {

      Get("/v1/resources/" + URLEncoder.encode(standoffLangDingIri.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val textObj: JsObject =
          getValuesForProp(response, "http://www.knora.org/ontology/0001/anything#hasText") match {
            case vals: JsArray =>
              vals.elements.head.asInstanceOf[JsObject]
            case _ =>
              throw new InvalidApiJsonException("values is not an array")
          }

        textObj.fields.get("language") match {
          case Some(JsString(lang)) => assert(lang == "en")
          case None                 => throw InvalidApiJsonException("'lang' is not specified but expected")
          case _                    => throw InvalidApiJsonException("'lang' is not a JsString")

        }

      }

    }

    "create a string value with chars encoded as entities but without markup in a bulk import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:Thing id="thing_with_string">
                   |        <knoraXmlImport:label>Thing with string</knoraXmlImport:label>
                   |        <p0001-anything:hasText knoraType="richtext_value">test &amp; &apos; &gt; &lt; test</p0001-anything:hasText>
                   |    </p0001-anything:Thing>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")

        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
        val createdResources: Seq[JsValue] = responseJson.fields("createdResources").asInstanceOf[JsArray].elements
        thingWithString.set(createdResources.head.asJsObject.fields("resourceIri").asInstanceOf[JsString].value)

      }
    }

    "get the resource created by bulk import and check for entities in string value" in {

      Get("/v1/resources/" + URLEncoder.encode(thingWithString.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
        val stringVal = responseJson
          .fields("props")
          .asInstanceOf[JsObject]
          .fields("http://www.knora.org/ontology/0001/anything#hasText")
          .asInstanceOf[JsObject]
          .fields("values")
          .asInstanceOf[JsArray]
          .elements
          .head
          .asInstanceOf[JsObject]
          .fields("utf8str")
          .asInstanceOf[JsString]
          .value

        assert(!(stringVal contains "&amp;"))
        assert(stringVal contains "&")

        assert(!(stringVal contains "&lt;"))
        assert(stringVal contains "<")

        assert(!(stringVal contains "&gt;"))
        assert(stringVal contains ">")

        assert(!(stringVal contains "&apos;"))
        assert(stringVal contains "'")

      }
    }

    "create a string value with a newline in a bulk import" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:Thing id="thing_with_string">
                   |        <knoraXmlImport:label>Thing with string</knoraXmlImport:label>
                   |        <p0001-anything:hasText knoraType="richtext_value">test
                   |        test</p0001-anything:hasText>
                   |    </p0001-anything:Thing>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")

        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
        val createdResources: Seq[JsValue] = responseJson.fields("createdResources").asInstanceOf[JsArray].elements
        thingWithString.set(createdResources.head.asJsObject.fields("resourceIri").asInstanceOf[JsString].value)

      }
    }

    "get the resource created by bulk import and check for the newline in the string value" in {

      Get("/v1/resources/" + URLEncoder.encode(thingWithString.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV1 ~> check {

        assert(status == StatusCodes.OK, response.toString)

        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)

        val stringVal = responseJson
          .fields("props")
          .asInstanceOf[JsObject]
          .fields("http://www.knora.org/ontology/0001/anything#hasText")
          .asInstanceOf[JsObject]
          .fields("values")
          .asInstanceOf[JsArray]
          .elements
          .head
          .asInstanceOf[JsObject]
          .fields("utf8str")
          .asInstanceOf[JsString]
          .value

        assert(!(stringVal contains "\\n"))
        assert(stringVal contains "\n")

      }
    }

    "create a resource whose label ends in a double quote" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:Thing id="thing_with_string">
                   |        <knoraXmlImport:label>Thing with "label"</knoraXmlImport:label>
                   |    </p0001-anything:Thing>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, responseStr)
        responseStr should include("createdResources")

        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
        val createdResources: Seq[JsValue] = responseJson.fields("createdResources").asInstanceOf[JsArray].elements
        thingWithString.set(createdResources.head.asJsObject.fields("resourceIri").asInstanceOf[JsString].value)

      }
    }

    "create a resource with a custom creation date in a bulk import" in {
      val creationDateStr = "2019-01-09T15:45:54.502951Z"

      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |    xsi:schemaLocation="http://api.knora.org/ontology/0001/anything/xml-import/v1# p0001-anything.xsd"
                   |    xmlns:p0001-anything="http://api.knora.org/ontology/0001/anything/xml-import/v1#"
                   |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |    <p0001-anything:Thing id="thing_with_creation_date" creationDate="$creationDateStr">
                   |        <knoraXmlImport:label>Thing with creation date</knoraXmlImport:label>
                   |        <p0001-anything:hasText knoraType="richtext_value">test</p0001-anything:hasText>
                   |    </p0001-anything:Thing>
                   |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseStr = responseAs[String]
        responseStr should include("createdResources")
        val responseJson: JsObject = AkkaHttpUtils.httpResponseToJson(response)
        val createdResources: Seq[JsValue] = responseJson.fields("createdResources").asInstanceOf[JsArray].elements
        thingWithCreationDate.set(createdResources.head.asJsObject.fields("resourceIri").asInstanceOf[JsString].value)
      }

      Get("/v2/resourcespreview/" + URLEncoder.encode(thingWithCreationDate.get, "UTF-8")) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)) ~> resourcesPathV2 ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseStr = responseAs[String]
        responseStr should include(creationDateStr)
      }
    }

    "create a resource belonging to a class in a shared ontology that refers to a property in another shared ontology" in {
      val xmlImport =
        s"""<?xml version="1.0" encoding="UTF-8"?>
                   |  <knoraXmlImport:resources xmlns="http://api.knora.org/ontology/shared/example-ibox/xml-import/v1#"
                   |  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   |  xsi:schemaLocation="http://api.knora.org/ontology/004D/kuno-raeber/xml-import/v1#"
                   |  xmlns:p0000-example-box="http://api.knora.org/ontology/shared/example-box/xml-import/v1#"
                   |  xmlns:p0000-example-ibox="http://api.knora.org/ontology/shared/example-ibox/xml-import/v1#"
                   |  xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
                   |  <p0000-example-ibox:iBox id="test_box">
                   |    <knoraXmlImport:label>test box 2</knoraXmlImport:label>
                   |    <p0000-example-box__hasName knoraType="richtext_value">This is a test.</p0000-example-box__hasName>
                   |  </p0000-example-ibox:iBox>
                   |</knoraXmlImport:resources>
                 """.stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")

      Post(s"/v1/resources/xmlimport/$projectIri",
           HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), xmlImport)) ~> addCredentials(
        BasicHttpCredentials(anythingAdminEmail, password)) ~> resourcesPathV1 ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseStr = responseAs[String]
        responseStr should include("createdResources")
      }

    }
  }

}
