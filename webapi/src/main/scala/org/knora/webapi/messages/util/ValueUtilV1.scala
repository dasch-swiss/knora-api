/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.util

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.exceptions.{
  InconsistentRepositoryDataException,
  NotImplementedException,
  OntologyConstraintException
}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.GroupedProps._
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.{
  LiteralValueType,
  LocationV1,
  ResourceCreateValueObjectResponseV1,
  ResourceCreateValueResponseV1
}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.{ExecutionContext, Future}

/**
  * Converts data from SPARQL query results into [[ApiValueV1]] objects.
  */
class ValueUtilV1(private val settings: KnoraSettingsImpl) {

  private val stringFormatter = StringFormatter.getGeneralInstance

  /**
    * Given a [[ValueProps]] containing details of a `knora-base:Value` object, creates a [[ApiValueV1]].
    *
    * @param valueProps           a [[GroupedProps.ValueProps]] resulting from querying the `Value`, in which the keys are RDF predicates,
    *                             and the values are lists of the objects of each predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @return a [[ApiValueV1]] representing the `Value`.
    */
  def makeValueV1(
      valueProps: ValueProps,
      projectShortcode: String,
      responderManager: ActorRef,
      featureFactoryConfig: FeatureFactoryConfig,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
    val valueTypeIri = valueProps.literalData(OntologyConstants.Rdf.Type).literals.head

    valueTypeIri match {
      case OntologyConstants.KnoraBase.TextValue =>
        makeTextValue(valueProps, responderManager, featureFactoryConfig, userProfile)
      case OntologyConstants.KnoraBase.IntValue      => makeIntValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.DecimalValue  => makeDecimalValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.BooleanValue  => makeBooleanValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.UriValue      => makeUriValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.DateValue     => makeDateValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.ColorValue    => makeColorValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.GeomValue     => makeGeomValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.GeonameValue  => makeGeonameValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.ListValue     => makeListValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.IntervalValue => makeIntervalValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.TimeValue     => makeTimeValue(valueProps, responderManager, userProfile)
      case OntologyConstants.KnoraBase.StillImageFileValue =>
        makeStillImageValue(valueProps, projectShortcode, responderManager, userProfile)
      case OntologyConstants.KnoraBase.TextFileValue =>
        makeTextFileValue(valueProps, projectShortcode, responderManager, userProfile)
      case OntologyConstants.KnoraBase.AudioFileValue =>
        makeAudioFileValue(valueProps, projectShortcode, responderManager, userProfile)
      case OntologyConstants.KnoraBase.DocumentFileValue =>
        makeDocumentFileValue(valueProps, projectShortcode, responderManager, userProfile)
      case OntologyConstants.KnoraBase.LinkValue => makeLinkValue(valueProps, responderManager, userProfile)
    }
  }

  def makeSipiImagePreviewGetUrlFromFilename(projectShortcode: String, filename: String): String = {
    s"${settings.externalSipiIIIFGetUrl}/$projectShortcode/$filename/full/!128,128/0/default.jpg"
  }

  /**
    * Creates a IIIF URL for accessing an image file via Sipi.
    *
    * @param imageFileValueV1 the image file value representing the image.
    * @return a Sipi IIIF URL.
    */
  def makeSipiImageGetUrlFromFilename(imageFileValueV1: StillImageFileValueV1): String = {
    s"${settings.externalSipiIIIFGetUrl}/${imageFileValueV1.projectShortcode}/${imageFileValueV1.internalFilename}/full/${imageFileValueV1.dimX},${imageFileValueV1.dimY}/0/default.jpg"
  }

  /**
    * Creates a URL for accessing a document file via Sipi.
    *
    * @param documentFileValueV1 the document file value.
    * @return a Sipi  URL.
    */
  def makeSipiDocumentGetUrlFromFilename(documentFileValueV1: DocumentFileValueV1): String = {
    s"${settings.externalSipiIIIFGetUrl}/${documentFileValueV1.projectShortcode}/${documentFileValueV1.internalFilename}/file"
  }

  /**
    * Creates a URL for accessing a text file via Sipi.
    *
    * @param textFileValue the text file value representing the text file.
    * @return a Sipi URL.
    */
  def makeSipiTextFileGetUrlFromFilename(textFileValue: TextFileValueV1): String = {
    s"${settings.externalSipiBaseUrl}/${textFileValue.projectShortcode}/${textFileValue.internalFilename}"
  }

  /**
    * Creates a URL for accessing an audio file via Sipi.
    *
    * @param audioFileValue the file value representing the audio file.
    * @return a Sipi URL.
    */
  def makeSipiAudioFileGetUrlFromFilename(audioFileValue: AudioFileValueV1): String = {
    s"${settings.externalSipiIIIFGetUrl}/${audioFileValue.projectShortcode}/${audioFileValue.internalFilename}/file"
  }

  // A Map of MIME types to Knora API v1 binary format name.
  private val mimeType2V1Format = new ErrorHandlingMap(
    Map(
      "application/octet-stream" -> "BINARY-UNKNOWN",
      "image/jpeg" -> "JPEG",
      "image/jp2" -> "JPEG2000",
      "image/jpx" -> "JPEG2000",
      "application/pdf" -> "PDF",
      "application/postscript" -> "POSTSCRIPT",
      "application/vnd.ms-powerpoint" -> "PPT",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPTX",
      "application/rtf" -> "RTF",
      "video/salsah" -> "WEBVIDEO",
      "text/sgml" -> "SGML",
      "image/tiff" -> "TIFF",
      "application/msword" -> "WORD",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "WORDX",
      "application/vnd.ms-excel" -> "XLS",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLSX",
      "application/xml" -> "XML",
      "text/xml" -> "XML",
      "text/csv" -> "CSV",
      "text/plain" -> "TEXT",
      "application/zip" -> "ZIP",
      "application/x-compressed-zip" -> "ZIP",
      "audio/mpeg" -> "AUDIO",
      "audio/mp4" -> "AUDIO",
      "audio/wav" -> "AUDIO",
      "audio/x-wav" -> "AUDIO",
      "audio/vnd.wave" -> "AUDIO"
    ), { key: String =>
      s"Unknown MIME type: $key"
    }
  )

  /**
    * Converts a [[FileValueV1]] (which is used internally by the Knora API server) to a [[LocationV1]] (which is
    * used in certain API responses).
    *
    * @param fileValueV1 a [[FileValueV1]].
    * @return a [[LocationV1]].
    */
  def fileValueV12LocationV1(fileValueV1: FileValueV1): LocationV1 = {
    fileValueV1 match {
      case stillImageFileValueV1: StillImageFileValueV1 =>
        LocationV1(
          format_name = mimeType2V1Format(stillImageFileValueV1.internalMimeType),
          origname = stillImageFileValueV1.originalFilename,
          nx = Some(stillImageFileValueV1.dimX),
          ny = Some(stillImageFileValueV1.dimY),
          path = makeSipiImageGetUrlFromFilename(stillImageFileValueV1)
        )

      case documentFileValueV1: DocumentFileValueV1 =>
        LocationV1(
          format_name = mimeType2V1Format(documentFileValueV1.internalMimeType),
          origname = documentFileValueV1.originalFilename,
          nx = documentFileValueV1.dimX,
          ny = documentFileValueV1.dimY,
          path = makeSipiDocumentGetUrlFromFilename(documentFileValueV1)
        )

      case textFileValue: TextFileValueV1 =>
        LocationV1(
          format_name = mimeType2V1Format(textFileValue.internalMimeType),
          origname = textFileValue.originalFilename,
          path = makeSipiTextFileGetUrlFromFilename(textFileValue)
        )

      case audioFileValue: AudioFileValueV1 =>
        LocationV1(
          format_name = mimeType2V1Format(audioFileValue.internalMimeType),
          origname = audioFileValue.originalFilename,
          path = makeSipiAudioFileGetUrlFromFilename(audioFileValue)
        )

      case otherType => throw NotImplementedException(s"Type not yet implemented: ${otherType.valueTypeIri}")
    }
  }

  /**
    * Creates a URL pointing to the given resource class icon. From the resource class IRI it gets the ontology specific path, i.e. the ontology name.
    * If the resource class IRI is "http://www.knora.org/ontology/knora-base#Region", the ontology name would be "knora-base".
    * To the base path, the icon name is appended. In case of a region with the icon name "region.gif",
    * "http://salsahapp:port/project-icons-basepath/knora-base/region.gif" is returned.
    *
    * This method requires the IRI segment before the last slash to be a unique identifier for all the ontologies used with Knora..
    *
    * @param resourceClassIri the IRI of the resource class in question.
    * @param iconsSrc         the name of the icon file.
    */
  def makeResourceClassIconURL(resourceClassIri: IRI, iconsSrc: String): IRI = {
    // get ontology name, e.g. "knora-base" from "http://www.knora.org/ontology/knora-base#Region"
    // add +1 to ignore the slash
    val ontologyName =
      resourceClassIri.substring(resourceClassIri.lastIndexOf('/') + 1, resourceClassIri.lastIndexOf('#'))

    // create URL: combine salsah-address and port, project icons base path, ontology name, icon name
    settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + ontologyName + '/' + iconsSrc
  }

  /**
    * Creates [[ValueProps]] from a List of [[VariableResultsRow]] representing a value object
    * (the triples where the given value object is the subject in).
    *
    * A [[VariableResultsRow]] is expected to have the following members (SPARQL variable names):
    *
    * - objPred: the object predicate (e.g. http://www.knora.org/ontology/knora-base#valueHasString).
    * - objObj: The string representation of the value assigned to objPred.
    *
    * In one given row, objPred **must** indicate the type of the given value object using rdfs:type (e.g. http://www.knora.org/ontology/knora-base#TextValue)
    *
    * In case the given value object contains standoff (objPred is http://www.knora.org/ontology/knora-base#valueHasStandoff),
    * it has the following additional members compared those mentioned above:
    *
    * - predStandoff: the standoff predicate (e.g. http://www.knora.org/ontology/knora-base#standoffHasStart)
    * - objStandoff: the string representation of the value assigned to predStandoff
    *
    * @param valueIri the IRI of the value that was queried.
    * @param objRows  SPARQL results.
    * @return a [[ValueProps]] representing the SPARQL results.
    */
  def createValueProps(valueIri: IRI, objRows: Seq[VariableResultsRow]): ValueProps = {

    val groupedValueObject = groupKnoraValueObjectPredicateRows(objRows.map(_.rowMap))

    ValueProps(valueIri, new ErrorHandlingMap(groupedValueObject.valuesLiterals, { key: IRI =>
      s"Predicate $key not found in value $valueIri"
    }), groupedValueObject.standoff)
  }

  /**
    * Converts three lists of SPARQL query results representing all the properties of a resource into a [[GroupedPropertiesByType]].
    *
    * Each [[VariableResultsRow]] is expected to have the following SPARQL variables:
    *
    * - prop: the IRI of the resource property (e.g. http://www.knora.org/ontology/knora-base#hasComment)
    * - obj: the IRI of the object that the property points to, which may be either a value object (an ordinary value or a reification) or another resource
    * - objPred: the IRI of each predicate of `obj` (e.g. for its literal contents, or for its permissions)
    * - objObj: the object of each `objPred`
    *
    * The remaining members are identical to those documented in [[createValueProps]].
    *
    * @param rowsWithOrdinaryValues SPARQL result rows describing properties that point to ordinary values (not link values).
    * @param rowsWithLinkValues     SPARQL result rows describing properties that point link values (reifications of links to resources).
    * @param rowsWithLinks          SPARQL result rows describing properties that point to resources.
    * @return a [[GroupedPropertiesByType]] representing the SPARQL results.
    */
  def createGroupedPropsByType(rowsWithOrdinaryValues: Seq[VariableResultsRow],
                               rowsWithLinkValues: Seq[VariableResultsRow],
                               rowsWithLinks: Seq[VariableResultsRow]): GroupedPropertiesByType = {
    GroupedPropertiesByType(
      groupedOrdinaryValueProperties = groupKnoraPropertyRows(rowsWithOrdinaryValues),
      groupedLinkValueProperties = groupKnoraPropertyRows(rowsWithLinkValues),
      groupedLinkProperties = groupKnoraPropertyRows(rowsWithLinks)
    )
  }

  /**
    * Checks that a value type is valid for the `knora-base:objectClassConstraint` of a property.
    *
    * @param propertyIri                   the IRI of the property.
    * @param valueType                     the IRI of the value type.
    * @param propertyObjectClassConstraint the IRI of the property's `knora-base:objectClassConstraint`.
    * @param responderManager              a reference to the Knora API Server responder manager.
    * @return A future containing Unit on success, or a failed future if the value type is not valid for the property's range.
    */
  def checkValueTypeForPropertyObjectClassConstraint(
      propertyIri: IRI,
      valueType: IRI,
      propertyObjectClassConstraint: IRI,
      responderManager: ActorRef,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Unit] = {
    if (propertyObjectClassConstraint == valueType) {
      Future.successful(())
    } else {
      for {
        checkSubClassResponse <- (responderManager ? CheckSubClassRequestV1(
          subClassIri = valueType,
          superClassIri = propertyObjectClassConstraint,
          userProfile = userProfile
        )).mapTo[CheckSubClassResponseV1]

        _ = if (!checkSubClassResponse.isSubClass) {
          throw OntologyConstraintException(
            s"Property $propertyIri requires a value of type $propertyObjectClassConstraint")
        }
      } yield ()
    }
  }

  /**
    * Converts a [[CreateValueResponseV1]] returned by the values responder on value creation
    * to the expected format for the resources responder [[ResourceCreateValueResponseV1]], which describes a value
    * added to a new resource.
    *
    * @param resourceIri   the IRI of the created resource.
    * @param creatorIri    the creator of the resource.
    * @param propertyIri   the property the valueResponse belongs to.
    * @param valueResponse the value that has been attached to the resource.
    * @return a [[ResourceCreateValueResponseV1]] representing the created value.
    */
  def convertCreateValueResponseV1ToResourceCreateValueResponseV1(
      resourceIri: IRI,
      creatorIri: IRI,
      propertyIri: IRI,
      valueResponse: CreateValueResponseV1): ResourceCreateValueResponseV1 = {

    val basicObjectResponse = ResourceCreateValueObjectResponseV1(
      textval = Map(LiteralValueType.StringValue -> valueResponse.value.toString),
      resource_id = Map(LiteralValueType.StringValue -> resourceIri),
      property_id = Map(LiteralValueType.StringValue -> propertyIri),
      person_id = Map(LiteralValueType.StringValue -> creatorIri),
      order = Map(LiteralValueType.IntegerValue -> 1) // TODO: include correct order: valueHasOrder
    )

    val objectResponse = valueResponse.value match {
      case integerValue: IntegerValueV1 =>
        basicObjectResponse.copy(
          ival = Some(Map(LiteralValueType.IntegerValue -> integerValue.ival))
        )

      case decimalValue: DecimalValueV1 =>
        basicObjectResponse.copy(
          dval = Some(Map(LiteralValueType.DecimalValue -> decimalValue.dval))
        )

      case dateValue: DateValueV1 =>
        val julianDayCountValue = DateUtilV1.dateValueV1ToJulianDayNumberValueV1(dateValue)
        basicObjectResponse.copy(
          dateval1 = Some(Map(LiteralValueType.StringValue -> dateValue.dateval1)),
          dateval2 = Some(Map(LiteralValueType.StringValue -> dateValue.dateval2)),
          dateprecision1 = Some(Map(LiteralValueType.StringValue -> julianDayCountValue.dateprecision1)),
          dateprecision2 = Some(Map(LiteralValueType.StringValue -> julianDayCountValue.dateprecision2)),
          calendar = Some(Map(LiteralValueType.StringValue -> julianDayCountValue.calendar))
        )

      case _: TextValueV1 => basicObjectResponse

      case _: LinkV1 => basicObjectResponse

      case _: StillImageFileValueV1 => basicObjectResponse

      case _: TextFileValueV1 => basicObjectResponse

      case _: DocumentFileValueV1 => basicObjectResponse

      case _: AudioFileValueV1 => basicObjectResponse

      case _: HierarchicalListValueV1 => basicObjectResponse

      case _: ColorValueV1 => basicObjectResponse

      case _: GeomValueV1 => basicObjectResponse

      case intervalValue: IntervalValueV1 =>
        basicObjectResponse.copy(
          timeval1 = Some(Map(LiteralValueType.DecimalValue -> intervalValue.timeval1)),
          timeval2 = Some(Map(LiteralValueType.DecimalValue -> intervalValue.timeval2))
        )

      case _: TimeValueV1 => basicObjectResponse

      case _: GeonameValueV1 => basicObjectResponse

      case _: BooleanValueV1 => basicObjectResponse

      case _: UriValueV1 => basicObjectResponse

      case other =>
        throw new Exception(s"Resource creation response format not implemented for value type ${other.valueTypeIri}") // TODO: implement remaining types.
    }

    ResourceCreateValueResponseV1(
      value = objectResponse,
      id = valueResponse.id
    )

  }

  /**
    * Creates a tuple that can be turned into a [[ValueProps]] representing both literal values and standoff.
    *
    * It expects the members documented in [[createValueProps]].
    *
    * @param objRows a value object's predicates.
    * @return a [[GroupedValueObject]] containing the values (literal or linking) and standoff nodes if given.
    */
  private def groupKnoraValueObjectPredicateRows(objRows: Seq[Map[String, String]]): GroupedValueObject = {

    // get rid of the value object IRI `obj` and group by predicate IRI `objPred` (e.g. `valueHasString`)
    val valuesGroupedByPredicate = objRows.map(_ - "obj").groupBy(_("objPred"))

    valuesGroupedByPredicate.foldLeft(
      GroupedValueObject(valuesLiterals = Map.empty[String, ValueLiterals],
                         standoff = Map.empty[IRI, Map[IRI, String]])) {
      case (acc: GroupedValueObject, (objPredIri: IRI, values: Seq[Map[String, String]])) =>
        if (objPredIri == OntologyConstants.KnoraBase.ValueHasStandoff) {
          // standoff information

          val groupedByStandoffNodeIri: Map[IRI, Seq[Map[String, String]]] = values.groupBy(_("objObj"))

          val standoffNodeAssertions: Map[IRI, Map[String, String]] = groupedByStandoffNodeIri.map {
            case (standoffNodeIri: IRI, values: Seq[Map[String, String]]) =>
              val valuesMap: Map[String, String] = values
                .map {
                  // make a Map with the standoffPred as the key and the objStandoff as the value
                  value: Map[String, String] =>
                    Map(value("predStandoff") -> value("objStandoff"))
                }
                .foldLeft(Map.empty[String, String]) {
                  // for each standoff node, we want to have just one Map
                  // this foldLeft turns a Sequence of Maps into one Map (a predicate can only occur once)
                  case (nodeValues: Map[String, String], value: Map[String, String]) =>
                    nodeValues ++ value
                }

              standoffNodeIri -> valuesMap

          }

          acc.copy(
            standoff = acc.standoff ++ standoffNodeAssertions
          )

        } else {
          // non standoff value

          val value: (String, ValueLiterals) = (objPredIri, ValueLiterals(values.map { value: Map[String, String] =>
            value("objObj")
          }))

          acc.copy(
            valuesLiterals = acc.valuesLiterals + value
          )

        }

    }

  }

  /**
    *
    * Given a list of result rows from the `get-resource-properties-and-values` SPARQL query, groups the rows first by property,
    * then by property object, and finally by property object predicate. In case the results contain standoff information, the standoff nodes are grouped
    * according to their blank node IRI. If the first row of results has a `linkValue` column, this is taken to mean that the property
    * is a link property and that the value of `linkValue` is the IRI of the corresponding `knora-base:LinkValue`; that IRI is then
    * added to the literals in the results, with the key [[OntologyConstants.KnoraBase.LinkValue]].
    *
    * For example, suppose we have the following rows for a property that points to Knora values.
    *
    * {{{
    * prop                obj                                                     objPred                            objObj
    * ---------------------------------------------------------------------------------------------------------------------------------------------------
    * incunabula:pagenum       http://rdfh.ch/8a0b1e75/values/61cb927602        knora-base:valueHasString          a1r, Titelblatt
    * incunabula:pagenum       http://rdfh.ch/8a0b1e75/values/61cb927602        knora-base:hasViewPermission       knora-base:KnownUser
    * incunabula:pagenum       http://rdfh.ch/8a0b1e75/values/61cb927602        knora-base:hasViewPermission       knora-base:UnknownUser
    * }}}
    *
    * The result will be a [[GroupedProperties]] containing a [[ValueProps]] with two keys, `valueHasString` and `hasPermission`.
    *
    * @param rows the SPARQL query result rows to group, which are expected to contain the columns given in the description of the `createGroupedPropsByType`
    *             method.
    * @return a [[GroupedProperties]] representing the SPARQL results.
    */
  private def groupKnoraPropertyRows(rows: Seq[VariableResultsRow]): GroupedProperties = {
    val gp: Map[String, ValueObjects] = rows.groupBy(_.rowMap("prop")).map {
      // grouped by resource property (e.g. hasComment)
      case (resProp: String, rows: Seq[VariableResultsRow]) =>
        val vo = (resProp, rows.map(_.rowMap - "prop").groupBy(_("obj")).map {
          // grouped by value object IRI
          case (objIri: IRI, objRows: Seq[Map[String, String]]) =>
            val groupedValueObject = groupKnoraValueObjectPredicateRows(objRows)

            val vp: ValueProps = ValueProps(
              valueIri = objIri,
              new ErrorHandlingMap(groupedValueObject.valuesLiterals, { key: IRI =>
                s"Predicate $key not found for property object $objIri"
              }),
              groupedValueObject.standoff
            )

            (objIri, vp)
        })

        (resProp, GroupedProps.ValueObjects(vo._2))
    }

    GroupedProps.GroupedProperties(gp)
  }

  /**
    * Converts a [[ValueProps]] into an [[IntegerValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return an [[IntegerValueV1]].
    */
  private def makeIntValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(IntegerValueV1(predicates(OntologyConstants.KnoraBase.ValueHasInteger).literals.head.toInt))
  }

  /**
    * Converts a [[ValueProps]] into a [[DecimalValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[DecimalValueV1]].
    */
  private def makeDecimalValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(DecimalValueV1(BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasDecimal).literals.head)))
  }

  /**
    * Converts a [[ValueProps]] into a [[BooleanValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[BooleanValueV1]].
    */
  private def makeBooleanValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(BooleanValueV1(predicates(OntologyConstants.KnoraBase.ValueHasBoolean).literals.head.toBoolean))
  }

  /**
    * Converts a [[ValueProps]] into a [[UriValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[UriValueV1]].
    */
  private def makeUriValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(UriValueV1(predicates(OntologyConstants.KnoraBase.ValueHasUri).literals.head))
  }

  /**
    * Converts a [[ValueProps]] into a [[DateValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[DateValueV1]].
    */
  private def makeDateValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    val julianDayNumberValueV1 = JulianDayNumberValueV1(
      dateval1 = predicates(OntologyConstants.KnoraBase.ValueHasStartJDN).literals.head.toInt,
      dateval2 = predicates(OntologyConstants.KnoraBase.ValueHasEndJDN).literals.head.toInt,
      dateprecision1 =
        KnoraPrecisionV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasStartPrecision).literals.head),
      dateprecision2 =
        KnoraPrecisionV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasEndPrecision).literals.head),
      calendar = KnoraCalendarV1.lookup(predicates(OntologyConstants.KnoraBase.ValueHasCalendar).literals.head)
    )

    Future(DateUtilV1.julianDayNumberValueV1ToDateValueV1(julianDayNumberValueV1))
  }

  /**
    * Converts a [[ValueProps]] into an [[IntervalValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return an [[IntervalValueV1]].
    */
  private def makeIntervalValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(
      IntervalValueV1(
        timeval1 = BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasIntervalStart).literals.head),
        timeval2 = BigDecimal(predicates(OntologyConstants.KnoraBase.ValueHasIntervalEnd).literals.head)
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[TimeValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[TimeValueV1]].
    */
  private def makeTimeValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData
    val timeStampStr = predicates(OntologyConstants.KnoraBase.ValueHasTimeStamp).literals.head

    Future(
      TimeValueV1(
        timeStamp = stringFormatter.xsdDateTimeStampToInstant(
          timeStampStr,
          throw InconsistentRepositoryDataException(s"Can't parse timestamp: $timeStampStr"))
      ))
  }

  /**
    * Creates a [[TextValueWithStandoffV1]] from the given string and the standoff nodes.
    *
    * @param utf8str              the string representation.
    * @param valueProps           the properties of the TextValue with standoff.
    * @param responderManager     the responder manager.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the client that is making the request.
    * @return a [[TextValueWithStandoffV1]].
    */
  private def makeTextValueWithStandoff(utf8str: String,
                                        language: Option[String] = None,
                                        valueProps: ValueProps,
                                        responderManager: ActorRef,
                                        featureFactoryConfig: FeatureFactoryConfig,
                                        userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[TextValueWithStandoffV1] = {

    // get the IRI of the mapping
    val mappingIri = valueProps.literalData
      .getOrElse(
        OntologyConstants.KnoraBase.ValueHasMapping,
        throw InconsistentRepositoryDataException(
          s"no mapping IRI associated with standoff belonging to textValue ${valueProps.valueIri}")
      )
      .literals
      .head

    for {

      // get the mapping and the related standoff entities
      // v2 responder is used here directly, v1 responder would inernally use v2 responder anyway and do unnecessary back and forth conversions
      mappingResponse: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(
        mappingIri = mappingIri,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = userProfile
      )).mapTo[GetMappingResponseV2]

      standoffTags: Seq[StandoffTagV2] <- StandoffTagUtilV2.createStandoffTagsV2FromSelectResults(
        standoffAssertions = valueProps.standoff,
        responderManager = responderManager,
        requestingUser = userProfile
      )

    } yield
      TextValueWithStandoffV1(
        utf8str = utf8str,
        language = language,
        standoff = standoffTags,
        mappingIri = mappingIri,
        mapping = mappingResponse.mapping,
        resource_reference = stringFormatter.getResourceIrisFromStandoffTags(standoffTags)
      )

  }

  /**
    * Creates a [[TextValueSimpleV1]] from the given string.
    *
    * @param utf8str the string representation of the TextValue.
    * @return a [[TextValueSimpleV1]].
    */
  private def makeTextValueSimple(utf8str: String, language: Option[String] = None)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[TextValueSimpleV1] = {
    Future(
      TextValueSimpleV1(
        utf8str = utf8str,
        language = language
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[TextValueV1]].
    *
    * @param valueProps           a [[ValueProps]] representing the SPARQL query results to be converted.
    * @param featureFactoryConfig the feature factory configuration.
    * @return a [[TextValueV1]].
    */
  private def makeTextValue(
      valueProps: ValueProps,
      responderManager: ActorRef,
      featureFactoryConfig: FeatureFactoryConfig,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {

    val valueHasString: String = valueProps.literalData
      .get(OntologyConstants.KnoraBase.ValueHasString)
      .map(_.literals.head)
      .getOrElse(
        throw InconsistentRepositoryDataException(s"Value ${valueProps.valueIri} has no knora-base:valueHasString"))
    val valueHasLanguage: Option[String] =
      valueProps.literalData.get(OntologyConstants.KnoraBase.ValueHasLanguage).map(_.literals.head)

    if (valueProps.standoff.nonEmpty) {
      // there is standoff markup
      makeTextValueWithStandoff(
        utf8str = valueHasString,
        language = valueHasLanguage,
        valueProps = valueProps,
        responderManager = responderManager,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

    } else {
      // there is no standoff markup
      makeTextValueSimple(valueHasString, valueHasLanguage)

    }
  }

  /**
    * Converts a [[ValueProps]] into a [[ColorValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[ColorValueV1]].
    */
  private def makeColorValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(ColorValueV1(predicates(OntologyConstants.KnoraBase.ValueHasColor).literals.head))
  }

  /**
    * Converts a [[ValueProps]] into a [[GeomValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[GeomValueV1]].
    */
  private def makeGeomValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(GeomValueV1(predicates(OntologyConstants.KnoraBase.ValueHasGeometry).literals.head))
  }

  /**
    * Converts a [[ValueProps]] into a [[HierarchicalListValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[HierarchicalListValueV1]].
    */
  private def makeListValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(HierarchicalListValueV1(predicates(OntologyConstants.KnoraBase.ValueHasListNode).literals.head))
  }

  /**
    * Converts a [[ValueProps]] into a [[StillImageFileValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[StillImageFileValueV1]].
    */
  private def makeStillImageValue(
      valueProps: ValueProps,
      projectShortcode: String,
      responderManager: ActorRef,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(
      StillImageFileValueV1(
        internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
        internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
        originalFilename = predicates.get(OntologyConstants.KnoraBase.OriginalFilename).map(_.literals.head),
        projectShortcode = projectShortcode,
        dimX = predicates(OntologyConstants.KnoraBase.DimX).literals.head.toInt,
        dimY = predicates(OntologyConstants.KnoraBase.DimY).literals.head.toInt
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[TextFileValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[TextFileValueV1]].
    */
  private def makeTextFileValue(
      valueProps: ValueProps,
      projectShortcode: String,
      responderManager: ActorRef,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(
      TextFileValueV1(
        internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
        internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
        originalFilename = predicates.get(OntologyConstants.KnoraBase.OriginalFilename).map(_.literals.head),
        projectShortcode = projectShortcode
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[DocumentFileValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[DocumentFileValueV1]].
    */
  private def makeDocumentFileValue(
      valueProps: ValueProps,
      projectShortcode: String,
      responderManager: ActorRef,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(
      DocumentFileValueV1(
        internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
        internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
        originalFilename = predicates.get(OntologyConstants.KnoraBase.OriginalFilename).map(_.literals.head),
        projectShortcode = projectShortcode,
        pageCount = predicates.get(OntologyConstants.KnoraBase.PageCount).flatMap(_.literals.headOption.map(_.toInt)),
        dimX = predicates.get(OntologyConstants.KnoraBase.DimX).flatMap(_.literals.headOption.map(_.toInt)),
        dimY = predicates.get(OntologyConstants.KnoraBase.DimY).flatMap(_.literals.headOption.map(_.toInt))
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[AudioFileValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[DocumentFileValueV1]].
    */
  private def makeAudioFileValue(
      valueProps: ValueProps,
      projectShortcode: String,
      responderManager: ActorRef,
      userProfile: UserADM)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(
      AudioFileValueV1(
        internalMimeType = predicates(OntologyConstants.KnoraBase.InternalMimeType).literals.head,
        internalFilename = predicates(OntologyConstants.KnoraBase.InternalFilename).literals.head,
        originalFilename = predicates.get(OntologyConstants.KnoraBase.OriginalFilename).map(_.literals.head),
        projectShortcode = projectShortcode,
        duration = predicates
          .get(OntologyConstants.KnoraBase.Duration)
          .map(valueLiterals => BigDecimal(valueLiterals.literals.head))
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[LinkValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[LinkValueV1]].
    */
  private def makeLinkValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(
      LinkValueV1(
        subjectIri = predicates(OntologyConstants.Rdf.Subject).literals.head,
        predicateIri = predicates(OntologyConstants.Rdf.Predicate).literals.head,
        objectIri = predicates(OntologyConstants.Rdf.Object).literals.head,
        referenceCount = predicates(OntologyConstants.KnoraBase.ValueHasRefCount).literals.head.toInt
      ))
  }

  /**
    * Converts a [[ValueProps]] into a [[GeonameValueV1]].
    *
    * @param valueProps a [[ValueProps]] representing the SPARQL query results to be converted.
    * @return a [[GeonameValueV1]].
    */
  private def makeGeonameValue(valueProps: ValueProps, responderManager: ActorRef, userProfile: UserADM)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[ApiValueV1] = {
    val predicates = valueProps.literalData

    Future(GeonameValueV1(predicates(OntologyConstants.KnoraBase.ValueHasGeonameCode).literals.head))
  }

  /** Creates an attribute segment for the Salsah GUI from the given resource class.
    * Example: if "http://www.knora.org/ontology/0803/incunabula#book" is given, the function returns "restypeid=http://www.knora.org/ontology/0803/incunabula#book".
    *
    * @param resourceClass the resource class.
    * @return an attribute string to be included in the attributes for the GUI
    */
  def makeAttributeRestype(resourceClass: IRI): String = {
    "restypeid=" + resourceClass
  }

  /**
    * Given a set of attribute segments representing assertions about the values of [[OntologyConstants.SalsahGui.GuiAttribute]] for a property,
    * combines the attributes into a string for use in an API v1 response.
    *
    * @param attributes the values of [[OntologyConstants.SalsahGui.GuiAttribute]] for a property.
    * @return a semicolon-delimited string containing the attributes, or [[None]] if no attributes were found.
    */
  def makeAttributeString(attributes: Set[String]): Option[String] = {
    if (attributes.isEmpty) {
      None
    } else {
      Some(attributes.toVector.sorted.mkString(";"))
    }
  }

}

/**
  * Represents SPARQL results to be converted into [[ApiValueV1]] objects.
  */
object GroupedProps {

  /**
    * Contains the three types of [[GroupedProperties]] returned by a SPARQL query.
    *
    * @param groupedOrdinaryValueProperties properties pointing to ordinary Knora values (i.e. not link values).
    * @param groupedLinkValueProperties     properties pointing to link value objects (reifications of links to resources).
    * @param groupedLinkProperties          properties pointing to resources.
    */
  case class GroupedPropertiesByType(groupedOrdinaryValueProperties: GroupedProperties,
                                     groupedLinkValueProperties: GroupedProperties,
                                     groupedLinkProperties: GroupedProperties)

  /**
    * Represents the grouped properties of one of the three types.
    *
    * @param groupedProperties The grouped properties: The Map's keys (IRI) consist of resource properties (e.g. http://www.knora.org/ontology/knora-base#hasComment).
    */
  case class GroupedProperties(groupedProperties: Map[IRI, ValueObjects])

  /**
    * Represents the value objects belonging to a resource property
    *
    * @param valueObjects The value objects: The Map's keys consist of value object Iris.
    */
  case class ValueObjects(valueObjects: Map[IRI, ValueProps])

  /**
    * Represents the grouped values of a value object.
    *
    * @param valuesLiterals the values (literal or linking).
    * @param standoff       standoff nodes, if any.
    */
  case class GroupedValueObject(valuesLiterals: Map[String, ValueLiterals], standoff: Map[IRI, Map[IRI, String]])

  /**
    * Represents the object properties belonging to a value object
    *
    * @param valueIri    the IRI of the value object.
    * @param literalData the value properties: The Map's keys (IRI) consist of value object properties (e.g. http://www.knora.org/ontology/knora-base#String).
    * @param standoff    the keys of the first Map are the standoff node Iris, the second Map contains all the predicates and objects related to one standoff node.
    */
  case class ValueProps(valueIri: IRI,
                        literalData: Map[IRI, ValueLiterals],
                        standoff: Map[IRI, Map[IRI, String]] = Map.empty[IRI, Map[IRI, String]])

  /**
    * Represents the literal values of a property (e.g. a number or a string)
    *
    * @param literals the literal values of a property.
    */
  case class ValueLiterals(literals: Seq[String])

}
