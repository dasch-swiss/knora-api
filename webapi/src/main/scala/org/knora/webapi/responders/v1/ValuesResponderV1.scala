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

import java.time.Instant

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{
  DefaultObjectAccessPermissionsStringForPropertyGetADM,
  DefaultObjectAccessPermissionsStringResponseADM,
  PermissionADM,
  PermissionType
}
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectADM,
  ProjectGetRequestADM,
  ProjectGetResponseADM,
  ProjectIdentifierADM
}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.messages.util.rdf.{SparqlSelectResult, VariableResultsRow}
import org.knora.webapi.messages.util.{KnoraSystemInstances, PermissionUtilADM, ResponderData, ValueUtilV1}
import org.knora.webapi.messages.v1.responder.ontologymessages.{EntityInfoGetRequestV1, EntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetV1, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileByIRIGetV1, UserProfileTypeV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util._

import scala.annotation.tailrec
import scala.concurrent.Future

/**
  * Updates Knora values.
  */
class ValuesResponderV1(responderData: ResponderData) extends Responder(responderData) {
  // Converts SPARQL query results to ApiValueV1 objects.
  val valueUtilV1 = new ValueUtilV1(settings)

  /**
    * Receives a message of type [[ValuesResponderRequestV1]], and returns an appropriate response message.
    */
  def receive(msg: ValuesResponderRequestV1) = msg match {
    case ValueGetRequestV1(valueIri, featureFactoryConfig, userProfile) =>
      getValueResponseV1(valueIri, featureFactoryConfig, userProfile)
    case LinkValueGetRequestV1(subjectIri, predicateIri, objectIri, featureFactoryConfig, userProfile) =>
      getLinkValue(subjectIri, predicateIri, objectIri, featureFactoryConfig, userProfile)
    case versionHistoryRequest: ValueVersionHistoryGetRequestV1 =>
      getValueVersionHistoryResponseV1(versionHistoryRequest)
    case createValueRequest: CreateValueRequestV1         => createValueV1(createValueRequest)
    case changeValueRequest: ChangeValueRequestV1         => changeValueV1(changeValueRequest)
    case changeFileValueRequest: ChangeFileValueRequestV1 => changeFileValueV1(changeFileValueRequest)
    case changeCommentRequest: ChangeCommentRequestV1     => changeCommentV1(changeCommentRequest)
    case deleteValueRequest: DeleteValueRequestV1         => deleteValueV1(deleteValueRequest)
    case createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV1 =>
      createMultipleValuesV1(createMultipleValuesRequest)
    case verifyMultipleValueCreationRequest: VerifyMultipleValueCreationRequestV1 =>
      verifyMultipleValueCreation(verifyMultipleValueCreationRequest)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for generating complete API responses.

  /**
    * Queries a `knora-base:Value` and returns a [[ValueGetResponseV1]] describing it.
    *
    * @param valueIri             the IRI of the value to be queried.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[ValueGetResponseV1]].
    */
  private def getValueResponseV1(valueIri: IRI,
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 userProfile: UserADM): Future[ValueGetResponseV1] = {
    for {
      maybeValueQueryResult <- findValue(
        valueIri = valueIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      response <- maybeValueQueryResult match {
        case Some(valueQueryResult) =>
          for {
            maybeValueCreatorProfile <- (responderManager ? UserProfileByIRIGetV1(
              userIri = valueQueryResult.creatorIri,
              userProfileType = UserProfileTypeV1.RESTRICTED,
              featureFactoryConfig = featureFactoryConfig
            )).mapTo[Option[UserProfileV1]]

            valueCreatorProfile = maybeValueCreatorProfile match {
              case Some(up) => up
              case None     => throw NotFoundException(s"User ${valueQueryResult.creatorIri} not found")
            }
          } yield
            ValueGetResponseV1(
              valuetype = valueQueryResult.value.valueTypeIri,
              rights = valueQueryResult.permissionCode,
              value = valueQueryResult.value,
              valuecreator = valueCreatorProfile.userData.email.get,
              valuecreatorname = valueCreatorProfile.userData.fullname.get,
              valuecreationdate = valueQueryResult.creationDate,
              comment = valueQueryResult.comment
            )

        case None =>
          throw NotFoundException(s"Value $valueIri not found (it may have been deleted)")
      }
    } yield response
  }

  /**
    * Creates a new value of a resource property (as opposed to a new version of an existing value).
    *
    * @param createValueRequest the request message.
    * @return a [[CreateValueResponseV1]] if the update was successful.
    */
  private def createValueV1(createValueRequest: CreateValueRequestV1): Future[CreateValueResponseV1] = {

    /**
      * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
      * called by [[IriLocker]] once it has acquired an update lock on the resource.
      *
      * @param userIri the IRI of the user making the request.
      * @return a [[Future]] that does pre-update checks and performs the update.
      */
    def makeTaskFuture(userIri: IRI): Future[CreateValueResponseV1] =
      for {
        // Check that the submitted value has the correct type for the property.
        entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
          propertyIris = Set(createValueRequest.propertyIri),
          userProfile = createValueRequest.userProfile
        )).mapTo[EntityInfoGetResponseV1]

        propertyInfo = entityInfoResponse.propertyInfoMap(createValueRequest.propertyIri)
        propertyObjectClassConstraint = propertyInfo
          .getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint)
          .getOrElse {
            throw InconsistentRepositoryDataException(
              s"Property ${createValueRequest.propertyIri} has no knora-base:objectClassConstraint")
          }

        // Check that the object of the property (the value to be created, or the target of the link to be created) will have
        // the correct type for the property's knora-base:objectClassConstraint.
        _ <- checkPropertyObjectClassConstraintForValue(
          propertyIri = createValueRequest.propertyIri,
          propertyObjectClassConstraint = propertyObjectClassConstraint,
          updateValueV1 = createValueRequest.value,
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          userProfile = createValueRequest.userProfile
        )

        // Check that the user has permission to modify the resource. (We do this as late as possible because it's
        // slower than the other checks, and there's no point in doing it if the other checks fail.)

        resourceFullResponse <- (responderManager ? ResourceFullGetRequestV1(
          iri = createValueRequest.resourceIri,
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          userADM = createValueRequest.userProfile,
          getIncoming = false
        )).mapTo[ResourceFullResponseV1]

        resourcePermissionCode: Option[Int] = resourceFullResponse.resdata.flatMap(resdata => resdata.rights)

        _ = if (!PermissionUtilADM.impliesPermissionCodeV1(userHasPermissionCode = resourcePermissionCode,
                                                           userNeedsPermission =
                                                             OntologyConstants.KnoraBase.ModifyPermission)) {
          throw ForbiddenException(
            s"User $userIri does not have permission to modify resource ${createValueRequest.resourceIri}")
        }

        // Ensure that creating the value would not violate the resource's cardinality restrictions or create a duplicate value.
        // This works in API v1 because a ResourceFullResponseV1 contains the resource's current property values (but only the
        // ones that the user is allowed to see, otherwise checking for duplicate values would be a security risk), plus empty
        // properties for which the resource's class has cardinalities. If the resources responder returns no information about
        // the property, this could be because the property isn't allowed for the resource, or because it's allowed, has a
        // cardinality of MustHaveOne or MayHaveOne, and already has a value that the user isn't allowed to see. We'll have to
        // implement this in a different way in API v2.
        cardinalityOK = resourceFullResponse.props.flatMap(_.properties.find(_.pid == createValueRequest.propertyIri)) match {
          case Some(prop: PropertyV1) =>
            if (prop.values.exists(apiValueV1 => createValueRequest.value.isDuplicateOfOtherValue(apiValueV1))) {
              throw DuplicateValueException()
            }

            val propCardinality = Cardinality.lookup(prop.occurrence.get)
            !((propCardinality == Cardinality.MayHaveOne || propCardinality == Cardinality.MustHaveOne) && prop.values.nonEmpty)

          case None =>
            false
        }

        _ = if (!cardinalityOK) {
          throw OntologyConstraintException(
            s"Cardinality restrictions do not allow a value to be added for property ${createValueRequest.propertyIri} of resource ${createValueRequest.resourceIri}")
        }

        // Get the IRI of project of the containing resource.
        projectIri: IRI = resourceFullResponse.resinfo
          .getOrElse(
            throw InconsistentRepositoryDataException(
              s"Did not find resource info for resource ${createValueRequest.resourceIri}"))
          .project_id

        // Get the resource class of the containing resource
        resourceClassIri: IRI = resourceFullResponse.resinfo
          .getOrElse(
            throw InconsistentRepositoryDataException(
              s"Did not find resource info for resource ${createValueRequest.resourceIri}"))
          .restype_id

        defaultObjectAccessPermissions <- {
          responderManager ? DefaultObjectAccessPermissionsStringForPropertyGetADM(
            projectIri = projectIri,
            resourceClassIri = resourceClassIri,
            propertyIri = createValueRequest.propertyIri,
            targetUser = createValueRequest.userProfile,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )
        }.mapTo[DefaultObjectAccessPermissionsStringResponseADM]
        _ = log.debug(s"createValueV1 - defaultObjectAccessPermissions: $defaultObjectAccessPermissions")

        // Get project info
        maybeProjectInfo <- {
          responderManager ? ProjectInfoByIRIGetV1(
            iri = projectIri,
            featureFactoryConfig = createValueRequest.featureFactoryConfig,
            userProfileV1 = Some(createValueRequest.userProfile.asUserProfileV1)
          )
        }.mapTo[Option[ProjectInfoV1]]

        projectInfo = maybeProjectInfo match {
          case Some(pi) => pi
          case None     => throw NotFoundException(s"Project '$projectIri' not found.")
        }

        // Everything seems OK, so create the value.

        unverifiedValue <- createValueV1AfterChecks(
          dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
          projectIri = resourceFullResponse.resinfo.get.project_id,
          resourceIri = createValueRequest.resourceIri,
          propertyIri = createValueRequest.propertyIri,
          value = createValueRequest.value,
          comment = createValueRequest.comment,
          valueCreator = userIri,
          valuePermissions = defaultObjectAccessPermissions.permissionLiteral,
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          userProfile = createValueRequest.userProfile
        )

        // Verify that it was created.
        apiResponse <- verifyValueCreation(
          resourceIri = createValueRequest.resourceIri,
          propertyIri = createValueRequest.propertyIri,
          unverifiedValue = unverifiedValue,
          featureFactoryConfig = createValueRequest.featureFactoryConfig,
          userProfile = createValueRequest.userProfile
        )
      } yield apiResponse

    for {
      // Don't allow anonymous users to create values.
      userIri <- Future {
        if (createValueRequest.userProfile.isAnonymousUser) {
          throw ForbiddenException("Anonymous users aren't allowed to create values")
        } else {
          createValueRequest.userProfile.id
        }
      }

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        createValueRequest.apiRequestID,
        createValueRequest.resourceIri,
        () => makeTaskFuture(userIri)
      )
    } yield taskResult
  }

  /**
    * Generates SPARQL for creating multiple values in a new, empty resource, using an existing transaction.
    * The resource ''must'' be a new, empty resource, i.e. it must have no values. All pre-update checks must already
    * have been performed. Specifically, this method assumes that:
    *
    * - The requesting user has permission to add values to the resource.
    * - Each submitted value is consistent with the `knora-base:objectClassConstraint` of the property that is supposed to point to it.
    * - The resource has a suitable cardinality for each submitted value.
    * - All required values are provided.
    *
    * @param createMultipleValuesRequest the request message.
    * @return a [[GenerateSparqlToCreateMultipleValuesResponseV1]].
    */
  private def createMultipleValuesV1(createMultipleValuesRequest: GenerateSparqlToCreateMultipleValuesRequestV1)
    : Future[GenerateSparqlToCreateMultipleValuesResponseV1] = {

    /**
      * Creates a [[Future]] that performs the update. This function will be called by [[IriLocker]] once it
      * has acquired an update lock on the resource.
      *
      * @param userIri the IRI of the user making the request.
      * @return a [[Future]] that does pre-update checks and performs the update.
      */
    def makeTaskFuture(userIri: IRI): Future[GenerateSparqlToCreateMultipleValuesResponseV1] = {

      /**
        * Assists in the numbering of values to be created.
        *
        * @param createValueV1WithComment the value to be created.
        * @param valueIndex               the index of the value in the sequence of all values to be created. This will be used
        *                                 to generate unique SPARQL variable names.
        * @param valueHasOrder            the index of the value in the sequence of values to be created for a particular property.
        *                                 This will be used to generate `knora-base:valueHasOrder`.
        */
      case class NumberedValueToCreate(createValueV1WithComment: CreateValueV1WithComment,
                                       valueIndex: Int,
                                       valueHasOrder: Int)

      /**
        * Assists in collecting generated SPARQL as well as other information about values to be created for
        * a particular property.
        *
        * @param insertSparql   statements to be included in the SPARQL INSERT clause.
        * @param valuesToVerify information about each value to be created.
        * @param valueIndexes   the value index of each value described by this object (so they can be sorted).
        */
      case class SparqlGenerationResultForProperty(insertSparql: Vector[String] = Vector.empty[String],
                                                   valuesToVerify: Vector[UnverifiedValueV1] =
                                                     Vector.empty[UnverifiedValueV1],
                                                   valueIndexes: Vector[Int] = Vector.empty[Int])

      for {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Generate SPARQL to create links and LinkValues for standoff resource references in text values

        // To create LinkValues for the standoff resource references in the values to be created, we need to compute
        // the initial reference count of each LinkValue. This is equal to the number of TextValues in the resource
        // that have standoff references to a particular target resource.

        // First, make a single list of all the values to be created.
        valuesToCreatePerProperty: Map[IRI, Seq[CreateValueV1WithComment]] <- Future(createMultipleValuesRequest.values)
        valuesToCreateForAllProperties: Iterable[Seq[CreateValueV1WithComment]] = valuesToCreatePerProperty.values
        allValuesToCreate: Iterable[CreateValueV1WithComment] = valuesToCreateForAllProperties.flatten

        // Then, get the standoff resource references from all the text values to be created.
        // The 'collect' method builds a new list by applying a partial function to all elements of the list
        // on which the function is defined.
        resourceReferencesForAllTextValues: Iterable[Set[IRI]] = allValuesToCreate.collect {
          case CreateValueV1WithComment(textValueV1: TextValueWithStandoffV1, _) =>
            // check that resource references are consistent in `resource_reference` and linking standoff tags
            checkTextValueResourceRefs(textValueV1)

            textValueV1.resource_reference
        }

        // Combine those resource references into a single list, so if there are n text values with a reference to
        // some IRI, the list will contain that IRI n times.
        allResourceReferences: Iterable[IRI] = resourceReferencesForAllTextValues.flatten

        // Now we need to count the number of times each IRI occurs in allResourceReferences. To do this, first
        // use groupBy(identity). The groupBy method takes a function that returns a key for each item in the
        // collection, and makes a Map in which items with the same key are grouped together. The identity
        // function just returns its argument. So groupBy(identity) makes a Map[IRI, Iterable[IRI]] in which each
        // IRI points to a sequence of the same IRI repeated as many times as it occurred in allResourceReferences.
        allResourceReferencesGrouped: Map[IRI, Iterable[IRI]] = allResourceReferences.groupBy(identity)

        // Finally, replace each Iterable[IRI] with its size. That's the number of text values containing
        // standoff references to that IRI.
        targetIris: Map[IRI, Int] = allResourceReferencesGrouped.view.mapValues(_.size).toMap

        // If we're creating values as part of a bulk import, some standoff links could point to resources
        // that already exist in the triplestore, and others could point to resources that are being created
        // as part of the import. We need to know here which ones are supposed to exist already and which aren't,
        // because if a target resource is supposed to exist already, we have to query the triplestore now to check
        // that it really exists.
        //
        // Therefore, in the GenerateSparqlToCreateMultipleValuesRequestV1 we received, the standoff link targets
        // that don't yet exist are represented as client resource IDs, while the targets that really exist are
        // represented as ordinary IRIs. StringFormatter.isStandoffLinkReferenceToClientIDForResource() can tell
        // us which are which.
        //
        // So now we can get the set of standoff link targets that are ordinary IRIs, and check that each of
        // them exists in the triplestore and is a knora-base:Resource.
        targetIrisThatAlreadyExist: Set[IRI] = targetIris.keySet.filterNot(iri =>
          stringFormatter.isStandoffLinkReferenceToClientIDForResource(iri))

        targetIriCheckResult <- checkStandoffResourceReferenceTargets(
          targetIris = targetIrisThatAlreadyExist,
          featureFactoryConfig = createMultipleValuesRequest.featureFactoryConfig,
          userProfile = createMultipleValuesRequest.userProfile
        )

        // For each target IRI, construct a SparqlTemplateLinkUpdate to create a hasStandoffLinkTo property and one LinkValue,
        // with the associated count as the LinkValue's initial reference count.
        standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] = targetIris.toSeq.map {
          case (targetIri, initialReferenceCount) =>
            // If the target of a standoff link is a client ID for a resource, convert it to the corresponding real resource IRI.
            val realTargetIri = stringFormatter.toRealStandoffLinkTargetResourceIri(
              iri = targetIri,
              clientResourceIDsToResourceIris = createMultipleValuesRequest.clientResourceIDsToResourceIris)

            SparqlTemplateLinkUpdate(
              linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
              directLinkExists = false,
              insertDirectLink = true,
              deleteDirectLink = false,
              linkValueExists = false,
              linkTargetExists = true, // doesn't matter, the generateInsertStatementsForStandoffLinks template doesn't use it
              newLinkValueIri = stringFormatter.makeValueIri(createMultipleValuesRequest.resourceIri),
              linkTargetIri = realTargetIri,
              currentReferenceCount = 0,
              newReferenceCount = initialReferenceCount,
              newLinkValueCreator = OntologyConstants.KnoraAdmin.SystemUser,
              newLinkValuePermissions = standoffLinkValuePermissions
            )
        }

        // Generate INSERT clause statements based on those SparqlTemplateLinkUpdates.
        standoffLinkInsertSparql: String = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .generateInsertStatementsForStandoffLinks(
            resourceIri = createMultipleValuesRequest.resourceIri,
            linkUpdates = standoffLinkUpdates,
            creationDate = createMultipleValuesRequest.creationDate,
            stringFormatter = stringFormatter
          )
          .toString()

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Number each value to be created, and give it a valueHasOrder

        // Ungroup the values to be created so we can number them as a single sequence (to create unique SPARQL variable names for each value).
        ungroupedValues: Seq[(IRI, CreateValueV1WithComment)] = createMultipleValuesRequest.values.toSeq.flatMap {
          case (propertyIri, values) => values.map(value => (propertyIri, value))
        }

        // Number them all as a single sequence. Give each one a knora-base:valueHasOrder of 0 for now; we'll take care of that in a moment.
        numberedValues: Seq[(IRI, NumberedValueToCreate)] = ungroupedValues.zipWithIndex.map {
          case ((propertyIri: IRI, valueWithComment: CreateValueV1WithComment), valueIndex) =>
            (propertyIri, NumberedValueToCreate(valueWithComment, valueIndex, 0))
        }

        // Group them again by property so we generate knora-base:valueHasOrder for the values of each property.
        groupedNumberedValues: Map[IRI, Seq[NumberedValueToCreate]] = numberedValues.groupBy(_._1).map {
          case (propertyIri, propertyIriAndValueTuples) => (propertyIri, propertyIriAndValueTuples.map(_._2))
        }

        // Generate knora-base:valueHasOrder for the values of each property.
        groupedNumberedValuesWithValueHasOrder: Map[IRI, Seq[NumberedValueToCreate]] = groupedNumberedValues.map {
          case (propertyIri, values) =>
            val valuesWithValueHasOrder = values.zipWithIndex.map {
              case (numberedValueToCreate, valueHasOrder) => numberedValueToCreate.copy(valueHasOrder = valueHasOrder)
            }

            (propertyIri, valuesWithValueHasOrder)
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Generate SPARQL for each value of each property

        // Make a SparqlGenerationResultForProperty for each property.
        sparqlGenerationResults: Map[IRI, SparqlGenerationResultForProperty] = groupedNumberedValuesWithValueHasOrder
          .map {
            case (propertyIri: IRI, valuesToCreate: Seq[NumberedValueToCreate]) =>
              val defaultPropertyAccessPermissions: String =
                createMultipleValuesRequest.defaultPropertyAccessPermissions(propertyIri)

              // log.debug(s"createValueV1 - defaultPropertyAccessPermissions: $defaultPropertyAccessPermissions")

              // For each property, construct a SparqlGenerationResultForProperty containing WHERE clause statements, INSERT clause statements, and UnverifiedValueV1s.
              val sparqlGenerationResultForProperty: SparqlGenerationResultForProperty =
                valuesToCreate.foldLeft(SparqlGenerationResultForProperty()) {
                  case (propertyAcc: SparqlGenerationResultForProperty, valueToCreate: NumberedValueToCreate) =>
                    val updateValueV1 = valueToCreate.createValueV1WithComment.updateValueV1
                    val newValueIri = stringFormatter.makeValueIri(createMultipleValuesRequest.resourceIri)

                    // How we generate the SPARQL depends on whether we're creating a link or an ordinary value.
                    val insertSparql: String = valueToCreate.createValueV1WithComment.updateValueV1 match {
                      case linkUpdateV1: LinkUpdateV1 =>
                        // We're creating a link.

                        // Construct a SparqlTemplateLinkUpdate to tell the SPARQL templates how to create
                        // the link and its LinkValue.
                        val sparqlTemplateLinkUpdate = SparqlTemplateLinkUpdate(
                          linkPropertyIri = propertyIri.toSmartIri,
                          directLinkExists = false,
                          insertDirectLink = true,
                          deleteDirectLink = false,
                          linkValueExists = false,
                          linkTargetExists = linkUpdateV1.targetExists,
                          newLinkValueIri = newValueIri,
                          linkTargetIri = linkUpdateV1.targetResourceIri,
                          currentReferenceCount = 0,
                          newReferenceCount = 1,
                          newLinkValueCreator = userIri,
                          newLinkValuePermissions = defaultPropertyAccessPermissions
                        )

                        // Generate INSERT DATA clause statements for the link.
                        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                          .generateInsertStatementsForCreateLink(
                            resourceIri = createMultipleValuesRequest.resourceIri,
                            linkUpdate = sparqlTemplateLinkUpdate,
                            creationDate = createMultipleValuesRequest.creationDate,
                            maybeComment = valueToCreate.createValueV1WithComment.comment,
                            maybeValueHasOrder = Some(valueToCreate.valueHasOrder),
                            stringFormatter = stringFormatter
                          )
                          .toString()

                      case _ =>
                        // We're creating an ordinary value.

                        // If this is a text value and we're creating values as part of a bulk import, some of the target IRIs of
                        // standoff link tags in the text value might be client IDs for resources rather than real resource IRIs.
                        // Replace those IDs with the real IRIs of the target resources, so the generateInsertStatementsForCreateValue
                        // template can use the real IRIs.
                        val valueWithRealStandoffLinkIris = updateValueV1 match {
                          case textValueWithStandoff: TextValueWithStandoffV1 =>
                            val standoffWithRealStandoffLinkIris = textValueWithStandoff.standoff.map {
                              standoffTag: StandoffTagV2 =>
                                standoffTag.copy(
                                  attributes = standoffTag.attributes.map {
                                    case iriAttribute: StandoffTagIriAttributeV2 =>
                                      iriAttribute.copy(
                                        value = stringFormatter.toRealStandoffLinkTargetResourceIri(
                                          iri = iriAttribute.value,
                                          clientResourceIDsToResourceIris =
                                            createMultipleValuesRequest.clientResourceIDsToResourceIris
                                        )
                                      )

                                    case otherAttribute => otherAttribute
                                  }
                                )
                            }

                            textValueWithStandoff.copy(
                              standoff = standoffWithRealStandoffLinkIris
                            )

                          case otherValue => otherValue
                        }

                        // Generate INSERT DATA clause statements for the value.
                        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                          .generateInsertStatementsForCreateValue(
                            resourceIri = createMultipleValuesRequest.resourceIri,
                            propertyIri = propertyIri,
                            value = valueWithRealStandoffLinkIris,
                            newValueIri = newValueIri,
                            linkUpdates = Seq.empty[SparqlTemplateLinkUpdate], // This is empty because we have to generate SPARQL for standoff links separately.
                            maybeComment = valueToCreate.createValueV1WithComment.comment,
                            valueCreator = userIri,
                            valuePermissions = defaultPropertyAccessPermissions,
                            creationDate = createMultipleValuesRequest.creationDate,
                            maybeValueHasOrder = Some(valueToCreate.valueHasOrder),
                            stringFormatter = stringFormatter
                          )
                          .toString()
                    }

                    // For each value of the property, accumulate the generated SPARQL and an UnverifiedValueV1
                    // in the SparqlGenerationResultForProperty.
                    propertyAcc.copy(
                      insertSparql = propertyAcc.insertSparql :+ insertSparql,
                      valuesToVerify = propertyAcc.valuesToVerify :+ UnverifiedValueV1(newValueIri = newValueIri,
                                                                                       value = updateValueV1),
                      valueIndexes = propertyAcc.valueIndexes :+ valueToCreate.valueIndex
                    )
                }

              (propertyIri, sparqlGenerationResultForProperty)
          }

        // Concatenate all the generated SPARQL into one string for the WHERE clause and one string for the INSERT clause, sorting
        // the values by their indexes.

        resultsForAllProperties: Iterable[SparqlGenerationResultForProperty] = sparqlGenerationResults.values

        // The SPARQL for the INSERT clause also contains the SPARQL that was generated to insert standoff links.
        allInsertSparql: String = resultsForAllProperties
          .flatMap(result => result.insertSparql.zip(result.valueIndexes))
          .toSeq
          .sortBy(_._2)
          .map(_._1)
          .mkString("\n\n") + standoffLinkInsertSparql

        // Collect all the UnverifiedValueV1s for each property.
        allUnverifiedValues: Map[IRI, Seq[UnverifiedValueV1]] = sparqlGenerationResults.map {
          case (propertyIri, results) => propertyIri -> results.valuesToVerify
        }

      } yield
        GenerateSparqlToCreateMultipleValuesResponseV1(
          insertSparql = allInsertSparql,
          unverifiedValues = allUnverifiedValues
        )
    }

    for {
      // Don't allow anonymous users to create resources.
      userIri <- Future {
        if (createMultipleValuesRequest.userProfile.isAnonymousUser) {
          throw ForbiddenException("Anonymous users aren't allowed to create resources")
        } else {
          createMultipleValuesRequest.userProfile.id
        }
      }

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        createMultipleValuesRequest.apiRequestID,
        createMultipleValuesRequest.resourceIri,
        () => makeTaskFuture(userIri)
      )
    } yield taskResult
  }

  /**
    * Verifies the creation of multiple values.
    *
    * @param verifyRequest a [[VerifyMultipleValueCreationRequestV1]].
    * @return a [[VerifyMultipleValueCreationResponseV1]] if all values were created successfully, or a failed
    *         future if any values were not created.
    */
  private def verifyMultipleValueCreation(
      verifyRequest: VerifyMultipleValueCreationRequestV1): Future[VerifyMultipleValueCreationResponseV1] = {
    // We have a Map of property IRIs to sequences of UnverifiedCreateValueResponseV1s. Query each value and
    // build a Map with the same structure, except that instead of UnverifiedCreateValueResponseV1s, it contains Futures
    // providing the results of querying the values.
    val valueVerificationFutures: Map[IRI, Future[Seq[CreateValueResponseV1]]] = verifyRequest.unverifiedValues.map {
      case (propertyIri: IRI, unverifiedValues: Seq[UnverifiedValueV1]) =>
        val valueVerificationResponsesForProperty = unverifiedValues.map { unverifiedValue =>
          verifyValueCreation(
            resourceIri = verifyRequest.resourceIri,
            propertyIri = propertyIri,
            unverifiedValue = unverifiedValue,
            featureFactoryConfig = verifyRequest.featureFactoryConfig,
            userProfile = verifyRequest.userProfile
          )
        }
        propertyIri -> Future.sequence(valueVerificationResponsesForProperty)
    }

    // Convert our Map full of Futures into one Future, which will provide a Map of all the results
    // when they're available.
    for {
      valueVerificationResponses: Map[IRI, Seq[CreateValueResponseV1]] <- ActorUtil.sequenceFutureSeqsInMap(
        valueVerificationFutures)
    } yield VerifyMultipleValueCreationResponseV1(verifiedValues = valueVerificationResponses)
  }

  /**
    * Adds a new version of an existing file value.
    *
    * @param changeFileValueRequest a [[ChangeFileValueRequestV1]] sent by the values route.
    * @return a [[ChangeFileValueResponseV1]] representing all the changed file values.
    */
  private def changeFileValueV1(changeFileValueRequest: ChangeFileValueRequestV1): Future[ChangeFileValueResponseV1] = {

    /**
      * Temporary structure to represent existing file values of a resource.
      *
      * @param property       the property IRI (e.g., hasStillImageFileValueRepresentation)
      * @param valueObjectIri the IRI of the value object.
      * @param quality        the quality of the file value
      */
    case class CurrentFileValue(property: IRI, valueObjectIri: IRI, quality: Option[Int])

    /**
      * Changes a file value in the triplestore.
      *
      * @param changeFileValueRequest a [[ChangeFileValueRequestV1]] sent by the values route.
      * @param projectADM             the project in which the value is being updated.
      * @return a [[ChangeFileValueResponseV1]] representing all the changed file values.
      */
    def makeTaskFuture(changeFileValueRequest: ChangeFileValueRequestV1,
                       projectADM: ProjectADM): Future[ChangeFileValueResponseV1] = {
      val fileValueContent: FileValueContentV2 = changeFileValueRequest.file.toFileValueContentV2

      // get the Iris of the current file value(s)
      val triplestoreUpdateFuture = for {

        resourceIri <- Future(changeFileValueRequest.resourceIri)

        getFileValuesSparql = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .getFileValuesForResource(
            triplestore = settings.triplestoreType,
            resourceIri = resourceIri
          )
          .toString()
        //_ = print(getFileValuesSparql)
        getFileValuesResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(getFileValuesSparql))
          .mapTo[SparqlSelectResult]
        // _ <- Future(println(getFileValuesResponse))

        // check that the resource to be updated exists and it is a subclass of knora-base:Representation
        _ = if (getFileValuesResponse.results.bindings.isEmpty)
          throw NotFoundException(
            s"Value ${changeFileValueRequest.resourceIri} not found (it may have been deleted) or it is not a knora-base:Representation")

        // get the property Iris, file value Iris and qualities attached to the resource
        fileValues: Seq[CurrentFileValue] = getFileValuesResponse.results.bindings.map { row: VariableResultsRow =>
          CurrentFileValue(
            property = row.rowMap("p"),
            valueObjectIri = row.rowMap("fileValueIri"),
            quality = row.rowMap.get("quality") match {
              case Some(quality: String) => Some(quality.toInt)
              case None                  => None
            }
          )
        }

        // TODO: check if the file type returned by Sipi corresponds to the already existing file value type

        response: ChangeValueResponseV1 <- changeValueV1(
          ChangeValueRequestV1(
            valueIri = fileValues.head.valueObjectIri,
            value = changeFileValueRequest.file,
            featureFactoryConfig = changeFileValueRequest.featureFactoryConfig,
            userProfile = changeFileValueRequest.userProfile,
            apiRequestID = changeFileValueRequest.apiRequestID // re-use the same id
          ))

        changedLocation = response.value match {
          case fileValueV1: FileValueV1 => valueUtilV1.fileValueV12LocationV1(fileValueV1)
          case other =>
            throw AssertionException(
              s"Expected Sipi to change a file value, but it changed one of these: ${other.valueTypeIri}")
        }
      } yield
        ChangeFileValueResponseV1(
          locations = Vector(changedLocation),
          projectADM = projectADM
        )

      ResourceUtilV2.doSipiPostUpdate(
        updateFuture = triplestoreUpdateFuture,
        valueContent = fileValueContent,
        requestingUser = changeFileValueRequest.userProfile,
        responderManager = responderManager,
        storeManager = storeManager,
        log = log
      )
    }

    for {
      resourceInfoResponse <- (responderManager ? ResourceInfoGetRequestV1(
        iri = changeFileValueRequest.resourceIri,
        featureFactoryConfig = changeFileValueRequest.featureFactoryConfig,
        userProfile = changeFileValueRequest.userProfile
      )).mapTo[ResourceInfoResponseV1]

      // Get project info
      projectResponse <- {
        responderManager ? ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(resourceInfoResponse.resource_info.get.project_id)),
          featureFactoryConfig = changeFileValueRequest.featureFactoryConfig,
          requestingUser = changeFileValueRequest.userProfile
        )
      }.mapTo[ProjectGetResponseADM]

      // Do the preparations of a file value change while already holding an update lock on the resource.
      // This is necessary because in `makeTaskFuture` the current file value Iris for the given resource IRI have to been retrieved.
      // Using the lock, we make sure that these are still up to date when `changeValueV1` is being called.
      //
      // The method `changeValueV1` will be called using the same lock.
      taskResult <- IriLocker.runWithIriLock(
        changeFileValueRequest.apiRequestID,
        changeFileValueRequest.resourceIri,
        () => makeTaskFuture(changeFileValueRequest, projectResponse.project)
      )
    } yield taskResult

  }

  /**
    * Adds a new version of an existing value.
    *
    * @param changeValueRequest the request message.
    * @return an [[ChangeValueResponseV1]] if the update was successful.
    */
  private def changeValueV1(changeValueRequest: ChangeValueRequestV1): Future[ChangeValueResponseV1] = {

    /**
      * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
      * called by [[IriLocker]] once it has acquired an update lock on the resource.
      *
      * @param userIri                     the IRI of the user making the request.
      * @param findResourceWithValueResult a [[FindResourceWithValueResult]] indicating which resource contains the value
      *                                    to be updated.
      * @return a [[Future]] that does pre-update checks and performs the update.
      */
    def makeTaskFuture(userIri: IRI,
                       findResourceWithValueResult: FindResourceWithValueResult): Future[ChangeValueResponseV1] = {
      // If we're updating a link, findResourceWithValueResult will contain the IRI of the property that points to the
      // knora-base:LinkValue, but we'll need the IRI of the corresponding link property.
      val propertyIri = changeValueRequest.value match {
        case linkUpdateV1: LinkUpdateV1 =>
          stringFormatter.linkValuePropertyIriToLinkPropertyIri(findResourceWithValueResult.propertyIri)
        case _ => findResourceWithValueResult.propertyIri
      }

      if (propertyIri == OntologyConstants.KnoraBase.HasStandoffLinkTo) {
        throw BadRequestException("Standoff links can be changed only by submitting a new text value")
      }

      for {
        // Ensure that the user has permission to modify the value.
        maybeCurrentValueQueryResult: Option[ValueQueryResult] <- changeValueRequest.value match {
          case linkUpdateV1: LinkUpdateV1 =>
            // We're being asked to update a link. We expect the current value version IRI to point to a
            // knora-base:LinkValue. Get all necessary information about the LinkValue and the corresponding
            // direct link.
            findLinkValueByIri(
              subjectIri = findResourceWithValueResult.resourceIri,
              predicateIri = propertyIri,
              objectIri = None,
              linkValueIri = changeValueRequest.valueIri,
              featureFactoryConfig = changeValueRequest.featureFactoryConfig,
              userProfile = changeValueRequest.userProfile
            )

          case otherValueV1 =>
            // We're being asked to update an ordinary value.
            findValue(
              valueIri = changeValueRequest.valueIri,
              featureFactoryConfig = changeValueRequest.featureFactoryConfig,
              userProfile = changeValueRequest.userProfile
            )
        }

        currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(
          throw NotFoundException(s"Value ${changeValueRequest.valueIri} not found (it may have been deleted)"))

        _ = if (!PermissionUtilADM.impliesPermissionCodeV1(
                  userHasPermissionCode = Some(currentValueQueryResult.permissionCode),
                  userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission)) {
          throw ForbiddenException(
            s"User $userIri does not have permission to add a new version to value ${changeValueRequest.valueIri}")
        }

        // Check that the submitted value has the correct type for the property.

        entityInfoResponse <- (responderManager ? EntityInfoGetRequestV1(
          propertyIris = Set(propertyIri),
          userProfile = changeValueRequest.userProfile
        )).mapTo[EntityInfoGetResponseV1]

        propertyInfo = entityInfoResponse.propertyInfoMap(propertyIri)
        propertyObjectClassConstraint = propertyInfo
          .getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint)
          .getOrElse {
            throw InconsistentRepositoryDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
          }

        // Check that the object of the property (the value to be updated, or the target of the link to be updated) will have
        // the correct type for the property's knora-base:objectClassConstraint.
        _ <- checkPropertyObjectClassConstraintForValue(
          propertyIri = propertyIri,
          propertyObjectClassConstraint = propertyObjectClassConstraint,
          updateValueV1 = changeValueRequest.value,
          featureFactoryConfig = changeValueRequest.featureFactoryConfig,
          userProfile = changeValueRequest.userProfile
        )

        // Check that the current value and the submitted value have the same type.
        _ = if (currentValueQueryResult.value.valueTypeIri != changeValueRequest.value.valueTypeIri) {
          throw BadRequestException(
            s"Value ${changeValueRequest.valueIri} has type ${currentValueQueryResult.value.valueTypeIri}, but the submitted new version has type ${changeValueRequest.value.valueTypeIri}")
        }

        // Make sure the new version would not be redundant, given the current version.
        _ = if (changeValueRequest.value.isRedundant(currentValueQueryResult.value)) {
          throw DuplicateValueException("The submitted value is the same as the current version")
        }

        // Get details of the resource.  (We do this as late as possible because it's slower than the other checks,
        // and there's no point in doing it if the other checks fail.)
        resourceFullResponse <- (responderManager ? ResourceFullGetRequestV1(
          iri = findResourceWithValueResult.resourceIri,
          featureFactoryConfig = changeValueRequest.featureFactoryConfig,
          userADM = changeValueRequest.userProfile,
          getIncoming = false
        )).mapTo[ResourceFullResponseV1]

        _ = changeValueRequest.value match {
          case _: FileValueV1 => () // It is a file value, do not check for duplicates.
          case _              => // It is not a file value.
            // Ensure that adding the new value version would not create a duplicate value. This works in API v1 because a
            // ResourceFullResponseV1 contains only the values that the user is allowed to see, otherwise checking for
            // duplicate values would be a security risk. We'll have to implement this in a different way in API v2.
            resourceFullResponse.props.flatMap(_.properties.find(_.pid == propertyIri)) match {
              case Some(prop: PropertyV1) =>
                // Don't consider the current value version when looking for duplicates.
                val filteredValues =
                  prop.value_ids.zip(prop.values).filter(_._1 != changeValueRequest.valueIri).map(_._2)

                if (filteredValues.exists(apiValueV1 => changeValueRequest.value.isDuplicateOfOtherValue(apiValueV1))) {
                  throw DuplicateValueException()
                }

              case None =>
                // This shouldn't happen unless someone just changed the ontology.
                throw NotFoundException(
                  s"No information found about property $propertyIri for resource ${findResourceWithValueResult.resourceIri}")
            }

        }

        // Get the resource class of the containing resource
        resourceClassIri: IRI = resourceFullResponse.resinfo
          .getOrElse(
            throw InconsistentRepositoryDataException(
              s"Did not find resource info for resource ${findResourceWithValueResult.resourceIri}"))
          .restype_id

        _ = log.debug(
          s"changeValueV1 - DefaultObjectAccessPermissionsStringForPropertyGetV1 - projectIri ${findResourceWithValueResult.projectIri}, propertyIri: ${findResourceWithValueResult.propertyIri}, permissions: ${changeValueRequest.userProfile.permissions} ")
        defaultObjectAccessPermissions <- {
          responderManager ? DefaultObjectAccessPermissionsStringForPropertyGetADM(
            projectIri = findResourceWithValueResult.projectIri,
            resourceClassIri = resourceClassIri,
            propertyIri = findResourceWithValueResult.propertyIri,
            targetUser = changeValueRequest.userProfile,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )
        }.mapTo[DefaultObjectAccessPermissionsStringResponseADM]
        _ = log.debug(s"changeValueV1 - defaultObjectAccessPermissions: $defaultObjectAccessPermissions")

        // Get project info
        maybeProjectInfo <- {
          responderManager ? ProjectInfoByIRIGetV1(
            iri = resourceFullResponse.resinfo.get.project_id,
            featureFactoryConfig = changeValueRequest.featureFactoryConfig,
            userProfileV1 = Some(changeValueRequest.userProfile.asUserProfileV1)
          )
        }.mapTo[Option[ProjectInfoV1]]

        projectInfo = maybeProjectInfo match {
          case Some(pi) => pi
          case None     => throw NotFoundException(s"Project '${resourceFullResponse.resinfo.get.project_id}' not found.")
        }

        // The rest of the preparation for the update depends on whether we're changing a link or an ordinary value.
        apiResponse <- (changeValueRequest.value, currentValueQueryResult) match {
          case (linkUpdateV1: LinkUpdateV1, currentLinkValueQueryResult: LinkValueQueryResult) =>
            // We're updating a link. This means deleting an existing link and creating a new one, so
            // check that the user has permission to modify the resource.
            val resourcePermissionCode = resourceFullResponse.resdata.flatMap(resdata => resdata.rights)
            if (!PermissionUtilADM.impliesPermissionCodeV1(userHasPermissionCode = resourcePermissionCode,
                                                           userNeedsPermission =
                                                             OntologyConstants.KnoraBase.ModifyPermission)) {
              throw ForbiddenException(
                s"User $userIri does not have permission to modify resource ${findResourceWithValueResult.resourceIri}")
            }

            // We'll need to create a new LinkValue.

            changeLinkValueV1AfterChecks(
              projectIri = currentValueQueryResult.projectIri,
              dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
              resourceIri = findResourceWithValueResult.resourceIri,
              propertyIri = propertyIri,
              currentLinkValueV1 = currentLinkValueQueryResult.value,
              linkUpdateV1 = linkUpdateV1,
              comment = changeValueRequest.comment,
              valueCreator = userIri,
              valuePermissions = defaultObjectAccessPermissions.permissionLiteral,
              featureFactoryConfig = changeValueRequest.featureFactoryConfig,
              userProfile = changeValueRequest.userProfile
            )

          case _ =>
            // We're updating an ordinary value. Generate an IRI for the new version of the value.
            val newValueIri = stringFormatter.makeValueIri(findResourceWithValueResult.resourceIri)

            // Give the new version the same permissions as the previous version.

            val valuePermissions = currentValueQueryResult.permissionRelevantAssertions
              .find {
                case (p, o) => p == OntologyConstants.KnoraBase.HasPermissions
              }
              .map(_._2)
              .getOrElse(
                throw InconsistentRepositoryDataException(s"Value ${changeValueRequest.valueIri} has no permissions"))

            changeOrdinaryValueV1AfterChecks(
              projectIri = currentValueQueryResult.projectIri,
              resourceIri = findResourceWithValueResult.resourceIri,
              propertyIri = propertyIri,
              currentValueIri = changeValueRequest.valueIri,
              currentValueV1 = currentValueQueryResult.value,
              newValueIri = newValueIri,
              updateValueV1 = changeValueRequest.value,
              comment = changeValueRequest.comment,
              valueCreator = userIri,
              valuePermissions = valuePermissions,
              featureFactoryConfig = changeValueRequest.featureFactoryConfig,
              userProfile = changeValueRequest.userProfile
            )
        }
      } yield apiResponse
    }

    for {
      // Don't allow anonymous users to update values.
      userIri <- Future {
        if (changeValueRequest.userProfile.isAnonymousUser) {
          throw ForbiddenException("Anonymous users aren't allowed to update values")
        } else {
          changeValueRequest.userProfile.id
        }
      }

      // Find the resource containing the value.
      findResourceWithValueResult <- findResourceWithValue(changeValueRequest.valueIri)

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        changeValueRequest.apiRequestID,
        findResourceWithValueResult.resourceIri,
        () => makeTaskFuture(userIri, findResourceWithValueResult)
      )
    } yield taskResult
  }

  private def changeCommentV1(changeCommentRequest: ChangeCommentRequestV1): Future[ChangeValueResponseV1] = {

    /**
      * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
      * called by [[IriLocker]] once it has acquired an update lock on the resource.
      *
      * @param userIri the IRI of the user making the request.
      * @return a [[Future]] that does pre-update checks and performs the update.
      */
    def makeTaskFuture(userIri: IRI,
                       findResourceWithValueResult: FindResourceWithValueResult): Future[ChangeValueResponseV1] = {
      for {
        // Ensure that the user has permission to modify the value.
        maybeCurrentValueQueryResult: Option[ValueQueryResult] <- findValue(
          valueIri = changeCommentRequest.valueIri,
          featureFactoryConfig = changeCommentRequest.featureFactoryConfig,
          userProfile = changeCommentRequest.userProfile
        )

        currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(
          throw NotFoundException(s"Value ${changeCommentRequest.valueIri} not found (it may have been deleted)"))

        _ = if (!PermissionUtilADM.impliesPermissionCodeV1(
                  userHasPermissionCode = Some(currentValueQueryResult.permissionCode),
                  userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission)) {
          throw ForbiddenException(
            s"User $userIri does not have permission to add a new version to value ${changeCommentRequest.valueIri}")
        }

        // currentValueQueryResult.comment is an Option[String]
        _ = if (currentValueQueryResult.comment == changeCommentRequest.comment)
          throw DuplicateValueException("The submitted comment is the same as the current comment")

        // Everything looks OK, so update the comment.

        // Generate an IRI for the new value.
        newValueIri = stringFormatter.makeValueIri(findResourceWithValueResult.resourceIri)

        // Get project info
        maybeProjectInfo <- {
          responderManager ? ProjectInfoByIRIGetV1(
            findResourceWithValueResult.projectIri,
            featureFactoryConfig = changeCommentRequest.featureFactoryConfig,
            None
          )
        }.mapTo[Option[ProjectInfoV1]]

        projectInfo = maybeProjectInfo match {
          case Some(pi) => pi
          case None     => throw NotFoundException(s"Project '${findResourceWithValueResult.projectIri}' not found.")
        }

        // Make a timestamp to indicate when the value was updated.
        currentTime: String = Instant.now.toString

        // Generate a SPARQL update.
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .changeComment(
            dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
            triplestore = settings.triplestoreType,
            resourceIri = findResourceWithValueResult.resourceIri,
            propertyIri = findResourceWithValueResult.propertyIri,
            currentValueIri = changeCommentRequest.valueIri,
            newValueIri = newValueIri,
            maybeComment = changeCommentRequest.comment,
            currentTime = currentTime
          )
          .toString()

        // Do the update.
        sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // To find out whether the update succeeded, look for the new value in the triplestore.
        verifyUpdateResult <- verifyOrdinaryValueUpdate(
          resourceIri = findResourceWithValueResult.resourceIri,
          propertyIri = findResourceWithValueResult.propertyIri,
          searchValueIri = newValueIri,
          featureFactoryConfig = changeCommentRequest.featureFactoryConfig,
          userProfile = changeCommentRequest.userProfile
        )
      } yield
        ChangeValueResponseV1(
          value = verifyUpdateResult.value,
          comment = verifyUpdateResult.comment,
          id = newValueIri,
          rights = verifyUpdateResult.permissionCode
        )
    }

    for {
      // Don't allow anonymous users to update values.
      userIri <- Future {
        if (changeCommentRequest.userProfile.isAnonymousUser) {
          throw ForbiddenException("Anonymous users aren't allowed to update values")
        } else {
          changeCommentRequest.userProfile.id
        }
      }

      // Find the resource containing the value.
      findResourceWithValueResult <- findResourceWithValue(changeCommentRequest.valueIri)

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        changeCommentRequest.apiRequestID,
        findResourceWithValueResult.resourceIri,
        () => makeTaskFuture(userIri, findResourceWithValueResult)
      )
    } yield taskResult

  }

  /**
    * Marks a value as deleted.
    *
    * @param deleteValueRequest the request message.
    * @return a [[DeleteValueResponseV1]].
    */
  private def deleteValueV1(deleteValueRequest: DeleteValueRequestV1): Future[DeleteValueResponseV1] = {

    /**
      * Creates a [[Future]] that does pre-update checks and performs the update. This function will be
      * called by [[IriLocker]] once it has acquired an update lock on the resource.
      *
      * @param userIri                     the IRI of the user making the request.
      * @param findResourceWithValueResult a [[FindResourceWithValueResult]] indicating which resource contains the value
      *                                    to be updated.
      * @return a [[Future]] that does pre-update checks and performs the update.
      */
    def makeTaskFuture(userIri: IRI,
                       findResourceWithValueResult: FindResourceWithValueResult): Future[DeleteValueResponseV1] =
      for {
        // Ensure that the user has permission to mark the value as deleted.
        maybeCurrentValueQueryResult <- findValue(
          valueIri = deleteValueRequest.valueIri,
          featureFactoryConfig = deleteValueRequest.featureFactoryConfig,
          userProfile = deleteValueRequest.userProfile
        )

        currentValueQueryResult = maybeCurrentValueQueryResult.getOrElse(
          throw NotFoundException(s"Value ${deleteValueRequest.valueIri} not found (it may have been deleted)"))

        _ = if (!PermissionUtilADM.impliesPermissionCodeV1(
                  userHasPermissionCode = Some(currentValueQueryResult.permissionCode),
                  userNeedsPermission = OntologyConstants.KnoraBase.DeletePermission)) {
          throw ForbiddenException(
            s"User $userIri does not have permission to delete value ${deleteValueRequest.valueIri}")
        }

        // Make a timestamp to indicate when the value was marked as deleted.
        currentTime: String = Instant.now.toString

        // The way we delete the value depends on whether it's a link value or an ordinary value.

        (sparqlUpdate, deletedValueIri) <- currentValueQueryResult.value match {
          case linkValue: LinkValueV1 =>
            // It's a LinkValue. Make a new version of it with a reference count of 0, and mark the new
            // version as deleted.

            // Give the new version the same permissions as the previous version.

            val valuePermissions: String = currentValueQueryResult.permissionRelevantAssertions
              .find {
                case (p, o) => p == OntologyConstants.KnoraBase.HasPermissions
              }
              .map(_._2)
              .getOrElse(
                throw InconsistentRepositoryDataException(s"Value ${deleteValueRequest.valueIri} has no permissions"))

            val linkPropertyIri =
              stringFormatter.linkValuePropertyIriToLinkPropertyIri(findResourceWithValueResult.propertyIri)

            for {
              // Get project info
              maybeProjectInfo <- {
                responderManager ? ProjectInfoByIRIGetV1(
                  iri = findResourceWithValueResult.projectIri,
                  featureFactoryConfig = deleteValueRequest.featureFactoryConfig,
                  userProfileV1 = None
                )
              }.mapTo[Option[ProjectInfoV1]]

              projectInfo = maybeProjectInfo match {
                case Some(pi) => pi
                case None     => throw NotFoundException(s"Project '${findResourceWithValueResult.projectIri}' not found.")
              }

              sparqlTemplateLinkUpdate <- decrementLinkValue(
                sourceResourceIri = findResourceWithValueResult.resourceIri,
                linkPropertyIri = linkPropertyIri,
                targetResourceIri = linkValue.objectIri,
                valueCreator = userIri,
                valuePermissions = valuePermissions,
                featureFactoryConfig = deleteValueRequest.featureFactoryConfig,
                userProfile = deleteValueRequest.userProfile
              )

              sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                .deleteLink(
                  dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
                  triplestore = settings.triplestoreType,
                  linkSourceIri = findResourceWithValueResult.resourceIri,
                  linkUpdate = sparqlTemplateLinkUpdate,
                  maybeComment = deleteValueRequest.deleteComment,
                  currentTime = currentTime,
                  requestingUser = userIri
                )
                .toString()
            } yield (sparqlUpdate, sparqlTemplateLinkUpdate.newLinkValueIri)

          case other =>
            // It's not a LinkValue. Mark the existing version as deleted.

            // If it's a TextValue, make SparqlTemplateLinkUpdates for updating LinkValues representing
            // links in standoff markup.
            val linkUpdatesFuture: Future[Seq[SparqlTemplateLinkUpdate]] = other match {
              case textValue: TextValueWithStandoffV1 =>
                val linkUpdateFutures = textValue.resource_reference.map { targetResourceIri =>
                  decrementLinkValue(
                    sourceResourceIri = findResourceWithValueResult.resourceIri,
                    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                    targetResourceIri = targetResourceIri,
                    valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                    valuePermissions = standoffLinkValuePermissions,
                    featureFactoryConfig = deleteValueRequest.featureFactoryConfig,
                    userProfile = deleteValueRequest.userProfile
                  )
                }.toVector

                Future.sequence(linkUpdateFutures)

              case _ => Future(Seq.empty[SparqlTemplateLinkUpdate])
            }

            for {
              linkUpdates <- linkUpdatesFuture

              // Get project info
              maybeProjectInfo <- {
                responderManager ? ProjectInfoByIRIGetV1(
                  iri = findResourceWithValueResult.projectIri,
                  featureFactoryConfig = deleteValueRequest.featureFactoryConfig,
                  userProfileV1 = None
                )
              }.mapTo[Option[ProjectInfoV1]]

              projectInfo = maybeProjectInfo match {
                case Some(pi) => pi
                case None     => throw NotFoundException(s"Project '${findResourceWithValueResult.projectIri}' not found.")
              }

              sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                .deleteValue(
                  dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
                  triplestore = settings.triplestoreType,
                  resourceIri = findResourceWithValueResult.resourceIri,
                  propertyIri = findResourceWithValueResult.propertyIri,
                  valueIri = deleteValueRequest.valueIri,
                  maybeDeleteComment = deleteValueRequest.deleteComment,
                  linkUpdates = linkUpdates,
                  currentTime = currentTime,
                  requestingUser = userIri,
                  stringFormatter = stringFormatter
                )
                .toString()
            } yield (sparqlUpdate, deleteValueRequest.valueIri)
        }

        // Do the update.
        sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

        // Check whether the update succeeded.
        sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .checkValueDeletion(
            triplestore = settings.triplestoreType,
            valueIri = deletedValueIri
          )
          .toString()
        sparqlSelectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
        rows = sparqlSelectResponse.results.bindings

        _ = if (rows.isEmpty || !stringFormatter.optionStringToBoolean(
                  rows.head.rowMap.get("isDeleted"),
                  throw InconsistentRepositoryDataException(
                    s"Invalid boolean for isDeleted: ${rows.head.rowMap.get("isDeleted")}"))) {
          throw UpdateNotPerformedException(
            s"The request to mark value ${deleteValueRequest.valueIri} (or a new version of that value) as deleted did not succeed. Please report this as a possible bug.")
        }
      } yield DeleteValueResponseV1(id = deletedValueIri)

    for {
      // Don't allow anonymous users to update values.
      userIri <- Future {
        if (deleteValueRequest.userProfile.isAnonymousUser) {
          throw ForbiddenException("Anonymous users aren't allowed to mark values as deleted")
        } else {
          deleteValueRequest.userProfile.id
        }
      }

      // Find the resource containing the value.
      findResourceWithValueResult <- findResourceWithValue(deleteValueRequest.valueIri)

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
        deleteValueRequest.apiRequestID,
        findResourceWithValueResult.resourceIri,
        () => makeTaskFuture(userIri, findResourceWithValueResult)
      )
    } yield taskResult
  }

  /**
    * Gets the version history of a value.
    *
    * @param versionHistoryRequest a [[ValueVersionHistoryGetRequestV1]].
    * @return a [[ValueVersionHistoryGetResponseV1]].
    */
  private def getValueVersionHistoryResponseV1(
      versionHistoryRequest: ValueVersionHistoryGetRequestV1): Future[ValueVersionHistoryGetResponseV1] = {
    val userProfileV1 = versionHistoryRequest.userProfile.asUserProfileV1

    /**
      * Recursively converts a [[Map]] of value version SPARQL query result rows into a [[Vector]] representing the value's version history,
      * ordered from most recent to oldest.
      *
      * @param versionMap        a [[Map]] of value version IRIs to the contents of SPARQL query result rows.
      * @param startAtVersion    the IRI of the version to start at.
      * @param versionRowsVector a [[Vector]] containing the results of the previous recursive call, or an empty vector if this is the first call.
      * @return a [[Vector]] in which the elements are SPARQL query result rows representing versions, ordered from most recent to oldest.
      */
    @tailrec
    def versionMap2Vector(versionMap: Map[IRI, Map[String, String]],
                          startAtVersion: IRI,
                          versionRowsVector: Vector[Map[String, String]]): Vector[Map[String, String]] = {
      val startValue = versionMap(startAtVersion)
      val newVersionVector = versionRowsVector :+ startValue

      startValue.get("previousValue") match {
        case Some(previousValue) => versionMap2Vector(versionMap, previousValue, newVersionVector)
        case None                => newVersionVector
      }
    }

    for {
      // Do a SPARQL query to get the versions of the value.
      sparqlQuery <- Future {
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .getVersionHistory(
            triplestore = settings.triplestoreType,
            resourceIri = versionHistoryRequest.resourceIri,
            propertyIri = versionHistoryRequest.propertyIri,
            currentValueIri = versionHistoryRequest.currentValueIri
          )
          .toString()
      }
      selectResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      rows = selectResponse.results.bindings

      _ = if (rows.isEmpty) {
        throw NotFoundException(
          s"Value ${versionHistoryRequest.currentValueIri} is not the most recent version of an object of property ${versionHistoryRequest.propertyIri} for resource ${versionHistoryRequest.resourceIri}")
      }

      // Convert the result rows to a map of value IRIs to result rows.
      versionMap: Map[IRI, Map[String, String]] = rows.map { row =>
        val valueIri = row.rowMap("value")
        valueIri -> row.rowMap
      }.toMap

      // Order the result rows from most recent to oldest.
      versionRowsVector = versionMap2Vector(versionMap,
                                            versionHistoryRequest.currentValueIri,
                                            Vector.empty[Map[String, String]])

      // Filter out the versions that the user doesn't have permission to see.
      filteredVersionRowsVector = versionRowsVector.filter { rowMap =>
        val valueIri = rowMap("value")
        val valueCreator = rowMap("valueCreator")
        val project = rowMap("project")
        val valuePermissions = rowMap("valuePermissions")

        // Permission-checking on LinkValues is special, because they can be system-created rather than user-created.
        val valuePermissionCode =
          if (stringFormatter.optionStringToBoolean(
                rowMap.get("isLinkValue"),
                throw InconsistentRepositoryDataException(
                  s"Invalid boolean for isLinkValue: ${rowMap.get("isLinkValue")}"))) {
            // It's a LinkValue.
            PermissionUtilADM.getUserPermissionV1(
              entityIri = valueIri,
              entityCreator = valueCreator,
              entityProject = project,
              entityPermissionLiteral = valuePermissions,
              userProfile = userProfileV1
            )
          } else {
            // It's not a LinkValue.
            PermissionUtilADM.getUserPermissionV1(
              entityIri = valueIri,
              entityCreator = valueCreator,
              entityProject = project,
              entityPermissionLiteral = valuePermissions,
              userProfile = userProfileV1
            )
          }

        valuePermissionCode.nonEmpty
      }

      // Make a set of the IRIs of the versions that the user has permission to see.
      visibleVersionIris = filteredVersionRowsVector.map(_("value")).toSet

      versionV1Vector = filteredVersionRowsVector.map { rowMap =>
        ValueVersionV1(
          valueObjectIri = rowMap("value"),
          valueCreationDate = rowMap.get("valueCreationDate"),
          previousValue = rowMap.get("previousValue") match {
            // Don't refer to a previous value that the user doesn't have permission to see.
            case Some(previousValueIri) if visibleVersionIris.contains(previousValueIri) => Some(previousValueIri)
            case _                                                                       => None
          }
        )
      }
    } yield ValueVersionHistoryGetResponseV1(valueVersions = versionV1Vector)
  }

  /**
    * Looks for a direct link connecting two resources, finds the corresponding `knora-base:LinkValue`, and returns
    * a [[ValueGetResponseV1]] containing a [[LinkValueV1]] describing the `LinkValue`. Throws [[NotFoundException]]
    * if no such `LinkValue` is found.
    *
    * @param subjectIri           the IRI of the resource that is the source of the link.
    * @param predicateIri         the IRI of the property that links the two resources.
    * @param objectIri            the IRI of the resource that is the target of the link.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[ValueGetResponseV1]] containing a [[LinkValueV1]].
    */
  @throws(classOf[NotFoundException])
  private def getLinkValue(subjectIri: IRI,
                           predicateIri: IRI,
                           objectIri: IRI,
                           featureFactoryConfig: FeatureFactoryConfig,
                           userProfile: UserADM): Future[ValueGetResponseV1] = {
    for {
      maybeValueQueryResult <- findLinkValueByLinkTriple(
        subjectIri = subjectIri,
        predicateIri = predicateIri,
        objectIri = objectIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      linkValueResponse <- maybeValueQueryResult match {
        case Some(valueQueryResult) =>
          for {
            maybeValueCreatorProfile <- (responderManager ? UserProfileByIRIGetV1(
              userIri = valueQueryResult.creatorIri,
              userProfileType = UserProfileTypeV1.RESTRICTED,
              featureFactoryConfig = featureFactoryConfig
            )).mapTo[Option[UserProfileV1]]

            valueCreatorProfile = maybeValueCreatorProfile match {
              case Some(up) => up
              case None     => throw NotFoundException(s"User ${valueQueryResult.creatorIri} not found")
            }
          } yield
            ValueGetResponseV1(
              valuetype = valueQueryResult.value.valueTypeIri,
              rights = valueQueryResult.permissionCode,
              value = valueQueryResult.value,
              valuecreator = valueCreatorProfile.userData.email.get,
              valuecreatorname = valueCreatorProfile.userData.fullname.get,
              valuecreationdate = valueQueryResult.creationDate,
              comment = valueQueryResult.comment
            )

        case None =>
          throw NotFoundException(
            s"No knora-base:LinkValue found describing a link from resource $subjectIri with predicate $predicateIri to resource $objectIri (it may have been deleted)")
      }
    } yield linkValueResponse
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper methods and types.

  /**
    * Represents the result of querying a value.
    */
  trait ValueQueryResult {

    /**
      * The value that was found.
      */
    def value: ApiValueV1

    /**
      * The IRI Of the user that created the value.
      */
    def creatorIri: IRI

    /**
      * The date when the value was created, represented as a string.
      */
    def creationDate: String

    /**
      * The IRI of the project that the value belongs to.
      */
    def projectIri: IRI

    /**
      * An optional comment describing the value.
      */
    def comment: Option[String]

    /**
      * A list of the permission-relevant assertions declared on the value.
      */
    def permissionRelevantAssertions: Seq[(IRI, IRI)]

    /**
      * An integer permission code representing the user's permissions on the value.
      */
    def permissionCode: Int
  }

  /**
    * Represents basic information resulting from querying a value. This is sufficient if the value is an ordinary
    * value (not a link).
    */
  case class BasicValueQueryResult(value: ApiValueV1,
                                   creatorIri: IRI,
                                   creationDate: String,
                                   projectIri: IRI,
                                   comment: Option[String],
                                   permissionRelevantAssertions: Seq[(IRI, IRI)],
                                   permissionCode: Int)
      extends ValueQueryResult

  /**
    * Represents the result of querying a link.
    *
    * @param directLinkExists    `true` if a direct link exists between the two resources.
    * @param targetResourceClass if a direct link exists, contains the OWL class of the target resource.
    */
  case class LinkValueQueryResult(value: LinkValueV1,
                                  linkValueIri: IRI,
                                  creatorIri: IRI,
                                  creationDate: String,
                                  projectIri: IRI,
                                  comment: Option[String],
                                  directLinkExists: Boolean,
                                  targetResourceClass: Option[IRI],
                                  permissionRelevantAssertions: Seq[(IRI, IRI)],
                                  permissionCode: Int)
      extends ValueQueryResult

  /**
    * Queries a `knora-base:Value` and returns a [[ValueQueryResult]] describing it.
    *
    * @param valueIri             the IRI of the value to be queried.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[ValueQueryResult]], or `None` if the value is not found.
    */
  private def findValue(valueIri: IRI,
                        featureFactoryConfig: FeatureFactoryConfig,
                        userProfile: UserADM): Future[Option[ValueQueryResult]] = {
    for {
      sparqlQuery <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .getValue(
            triplestore = settings.triplestoreType,
            valueIri = valueIri
          )
          .toString())

      response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      rows: Seq[VariableResultsRow] = response.results.bindings

      maybeValueQueryResult <- sparqlQueryResults2ValueQueryResult(
        valueIri = valueIri,
        rows = rows,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // If it's a link value, check that the user has permission to see the source and target resources.
      _ = maybeValueQueryResult match {
        case Some(valueQueryResult) =>
          valueQueryResult.value match {
            case _: LinkValueV1 => checkLinkValueSubjectAndObjectPermissions(valueIri, userProfile)
            case _              => ()
          }

        case None => ()
      }
    } yield maybeValueQueryResult
  }

  /**
    * Checks that the user has permission to see the source and target resources of a link value.
    *
    * @param linkValueIri the IRI of the link value.
    * @param userProfile  the profile of the user making the request.
    * @return () if the user has the required permission, or an exception otherwise.
    */
  private def checkLinkValueSubjectAndObjectPermissions(linkValueIri: IRI, userProfile: UserADM): Future[Unit] = {
    val userProfileV1 = userProfile.asUserProfileV1

    for {
      sparqlQuery <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .getLinkSourceAndTargetPermissions(
            triplestore = settings.triplestoreType,
            linkValueIri = linkValueIri
          )
          .toString())

      response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      rows = response.results.bindings

      _ = if (rows.isEmpty) {
        throw NotFoundException(s"Link value $linkValueIri, or its source or target resource, was not found.")
      }

      rowMap = rows.head.rowMap

      maybeSourcePermissionCode = PermissionUtilADM.getUserPermissionV1(
        entityIri = rowMap("source"),
        entityCreator = rowMap("sourceCreator"),
        entityProject = rowMap("sourceProject"),
        entityPermissionLiteral = rowMap("sourcePermissions"),
        userProfile = userProfileV1
      )

      maybeTargetPermissionCode = PermissionUtilADM.getUserPermissionV1(
        entityIri = rowMap("target"),
        entityCreator = rowMap("targetCreator"),
        entityProject = rowMap("targetProject"),
        entityPermissionLiteral = rowMap("targetPermissions"),
        userProfile = userProfileV1
      )

      _ = if (maybeSourcePermissionCode.isEmpty || maybeTargetPermissionCode.isEmpty) {
        throw ForbiddenException(s"User ${userProfile.id} does not have permission to view link value $linkValueIri")
      }
    } yield ()
  }

  /**
    * Looks for `knora-base:LinkValue` given its IRI, and returns a [[ValueGetResponseV1]] containing a
    * [[LinkValueV1]] describing the `LinkValue`, or `None` if no such `LinkValue` is found.
    *
    * @param subjectIri           the IRI of the resource that is the source of the link.
    * @param predicateIri         the IRI of the property that links the two resources.
    * @param objectIri            if provided, the IRI of the target resource.
    * @param linkValueIri         the IRI of the `LinkValue`.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return an optional [[ValueGetResponseV1]] containing a [[LinkValueV1]].
    */
  private def findLinkValueByIri(subjectIri: IRI,
                                 predicateIri: IRI,
                                 objectIri: Option[IRI],
                                 linkValueIri: IRI,
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 userProfile: UserADM): Future[Option[LinkValueQueryResult]] = {
    for {
      sparqlQuery <- Future {
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .findLinkValueByIri(
            triplestore = settings.triplestoreType,
            subjectIri = subjectIri,
            predicateIri = predicateIri,
            maybeObjectIri = objectIri,
            linkValueIri = linkValueIri
          )
          .toString()
      }

      response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      rows: Seq[VariableResultsRow] = response.results.bindings

      maybeLinkValueQueryResult <- sparqlQueryResults2LinkValueQueryResult(
        rows = rows,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Check that the user has permission to see the source and target resources.
      _ = if (maybeLinkValueQueryResult.nonEmpty) {
        checkLinkValueSubjectAndObjectPermissions(linkValueIri, userProfile)
      }
    } yield maybeLinkValueQueryResult
  }

  /**
    * Looks for `knora-base:LinkValue` describing a link between two resources, and returns
    * a [[ValueGetResponseV1]] containing a [[LinkValueV1]] describing the `LinkValue`, or `None` if no such
    * `LinkValue` is found.
    *
    * @param subjectIri           the IRI of the resource that is the source of the link.
    * @param predicateIri         the IRI of the property that links the two resources.
    * @param objectIri            the IRI of the target resource.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return an optional [[ValueGetResponseV1]] containing a [[LinkValueV1]].
    */
  private def findLinkValueByLinkTriple(subjectIri: IRI,
                                        predicateIri: IRI,
                                        objectIri: IRI,
                                        featureFactoryConfig: FeatureFactoryConfig,
                                        userProfile: UserADM): Future[Option[LinkValueQueryResult]] = {
    for {
      sparqlQuery <- Future {
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .findLinkValueByObject(
            triplestore = settings.triplestoreType,
            subjectIri = subjectIri,
            predicateIri = predicateIri,
            objectIri = objectIri
          )
          .toString()
      }

      response <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]
      rows: Seq[VariableResultsRow] = response.results.bindings

      maybeLinkValueQueryResult <- sparqlQueryResults2LinkValueQueryResult(
        rows = rows,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Check that the user has permission to see the source and target resources.
      _ = maybeLinkValueQueryResult match {
        case Some(linkValueQueryResult) =>
          checkLinkValueSubjectAndObjectPermissions(linkValueQueryResult.linkValueIri, userProfile)
        case _ => ()
      }
    } yield maybeLinkValueQueryResult
  }

  /**
    * Converts SPARQL query results into a [[ValueQueryResult]]. Checks that the user has permission to view the value object.
    * If the value is a link value, the caller of this method is responsible for ensuring that the user has permission to
    * view the source and target resources.
    *
    * @param valueIri             the IRI of the value that was queried.
    * @param rows                 the query result rows.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[ValueQueryResult]].
    */
  @throws(classOf[ForbiddenException])
  private def sparqlQueryResults2ValueQueryResult(valueIri: IRI,
                                                  rows: Seq[VariableResultsRow],
                                                  featureFactoryConfig: FeatureFactoryConfig,
                                                  userProfile: UserADM): Future[Option[BasicValueQueryResult]] = {
    val userProfileV1 = userProfile.asUserProfileV1

    if (rows.nonEmpty) {
      // Convert the query results to a ApiValueV1.
      val valueProps = valueUtilV1.createValueProps(valueIri, rows)

      for {
        resourceIri <- Future(
          valueIri.substring(0, valueIri.indexOf("/values/"))
        )
        resourceInfoResponse <- (responderManager ? ResourceInfoGetRequestV1(
          iri = resourceIri,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )).mapTo[ResourceInfoResponseV1]

        //TODO: change this to project UUID
        projectShortcode: String = resourceInfoResponse.resource_info
          .getOrElse(
            throw NotFoundException(
              s"Invalid value IRI, $valueIri. It contains IRI of a resource that does not exist."))
          .project_shortcode

        value <- valueUtilV1.makeValueV1(
          valueProps = valueProps,
          projectShortcode = projectShortcode,
          responderManager = responderManager,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )

        // Get the value's class IRI.
        valueClassIri = getValuePredicateObject(predicateIri = OntologyConstants.Rdf.Type, rows = rows)
          .getOrElse(throw InconsistentRepositoryDataException(s"Value $valueIri has no rdf:type"))

        // Get the IRI of the value's creator.
        creatorIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToUser, rows = rows)
          .getOrElse(throw InconsistentRepositoryDataException(s"Value $valueIri has no knora-base:attachedToUser"))

        // Get the value's project IRI.
        projectIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToProject, rows = rows)
          .getOrElse(
            throw InconsistentRepositoryDataException(
              s"The resource containing value $valueIri has no knora-base:attachedToProject"))

        // Get the value's creation date.
        creationDate = getValuePredicateObject(
          predicateIri = OntologyConstants.KnoraBase.ValueCreationDate,
          rows = rows).getOrElse(throw InconsistentRepositoryDataException(s"Value $valueIri has no valueCreationDate"))

        // Get the optional comment on the value.
        comment = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueHasComment, rows = rows)

        // Get the value's permission-relevant assertions.
        assertions = PermissionUtilADM.filterPermissionRelevantAssertionsFromValueProps(valueProps)

        // Get the permission code representing the user's permissions on the value.
        //
        // Link values created automatically for resource references in standoff
        // are automatically visible to all users, as long as they have permission
        // to see the source and target resources. The caller of this method is responsible
        // for checking the permissions on the source and target resources.

        maybePermissionCode = valueClassIri match {
          case OntologyConstants.KnoraBase.LinkValue =>
            val linkPredicateIri = getValuePredicateObject(predicateIri = OntologyConstants.Rdf.Predicate, rows = rows)
              .getOrElse(throw InconsistentRepositoryDataException(s"Link value $valueIri has no rdf:predicate"))

            PermissionUtilADM.getUserPermissionWithValuePropsV1(
              valueIri = valueIri,
              valueProps = valueProps,
              entityProject = None, // no need to specify this here, because it's in valueProps
              userProfile = userProfileV1
            )

          case _ =>
            PermissionUtilADM.getUserPermissionFromAssertionsV1(
              entityIri = valueIri,
              assertions = assertions,
              userProfile = userProfileV1
            )
        }

        permissionCode = maybePermissionCode.getOrElse {
          throw ForbiddenException(s"User ${userProfile.id} does not have permission to see value $valueIri")
        }

      } yield
        Some(
          BasicValueQueryResult(
            value = value,
            creatorIri = creatorIri,
            creationDate = creationDate,
            comment = comment,
            projectIri = projectIri,
            permissionRelevantAssertions = assertions,
            permissionCode = permissionCode
          )
        )
    } else {
      Future(None)
    }
  }

  /**
    * Converts SPARQL query results about a `knora-base:LinkValue` into a [[LinkValueQueryResult]].
    *
    * @param rows                 SPARQL query results about a `knora-base:LinkValue`.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[LinkValueQueryResult]].
    */
  private def sparqlQueryResults2LinkValueQueryResult(rows: Seq[VariableResultsRow],
                                                      featureFactoryConfig: FeatureFactoryConfig,
                                                      userProfile: UserADM): Future[Option[LinkValueQueryResult]] = {
    val userProfileV1 = userProfile.asUserProfileV1

    if (rows.nonEmpty) {
      val firstRowMap = rows.head.rowMap
      val linkValueIri = firstRowMap("linkValue")

      // Convert the query results into a LinkValueV1.
      val valueProps = valueUtilV1.createValueProps(linkValueIri, rows)
      val resourceIri = linkValueIri.substring(0, linkValueIri.indexOf("/values/"))

      for {
        resourceInfoResponse <- (responderManager ? ResourceInfoGetRequestV1(
          iri = resourceIri,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )).mapTo[ResourceInfoResponseV1]

        //TODO: change this to project UUID
        projectShortcode: String = resourceInfoResponse.resource_info
          .getOrElse(
            throw NotFoundException(
              s"Invalid value IRI, $linkValueIri. It contains IRI of a resource that does not exist."))
          .project_shortcode

        linkValueMaybe <- valueUtilV1.makeValueV1(
          valueProps = valueProps,
          projectShortcode = projectShortcode,
          responderManager = responderManager,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )

        linkValueV1: LinkValueV1 = linkValueMaybe match {
          case linkValue: LinkValueV1 => linkValue
          case other =>
            throw InconsistentRepositoryDataException(
              s"Expected value $linkValueIri to be of type ${OntologyConstants.KnoraBase.LinkValue}, but it was read with type ${other.valueTypeIri}")
        }

        // Get the IRI of the value's owner.
        creatorIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToUser, rows = rows)
          .getOrElse(throw InconsistentRepositoryDataException(s"Value $linkValueIri has no knora-base:attachedToUser"))

        // Get the value's project IRI.
        projectIri = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.AttachedToProject, rows = rows)
          .getOrElse(
            throw InconsistentRepositoryDataException(
              s"The resource containing value $linkValueIri has no knora-base:attachedToProject"))

        // Get the value's creation date.
        creationDate = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueCreationDate,
                                               rows = rows)
          .getOrElse(throw InconsistentRepositoryDataException(s"Value $linkValueIri has no valueCreationDate"))

        // Get the optional comment on the value.
        comment = getValuePredicateObject(predicateIri = OntologyConstants.KnoraBase.ValueHasComment, rows = rows)

        // Get the value's permission-relevant assertions.
        permissionRelevantAssertions = PermissionUtilADM.filterPermissionRelevantAssertionsFromValueProps(valueProps)

        // Get the permission code representing the user's permissions on the value.
        permissionCode = PermissionUtilADM
          .getUserPermissionWithValuePropsV1(
            valueIri = linkValueIri,
            valueProps = valueProps,
            entityProject = None, // no need to specify this here, because it's in valueProps
            userProfile = userProfileV1
          )
          .getOrElse {
            throw ForbiddenException(s"User ${userProfile.id} does not have permission to see value $linkValueIri")
          }

        directLinkExists = firstRowMap.get("directLinkExists").exists(_.toBoolean)
        targetResourceClass = firstRowMap.get("targetResourceClass")

      } yield
        Some(
          LinkValueQueryResult(
            value = linkValueV1,
            linkValueIri = linkValueIri,
            creatorIri = creatorIri,
            creationDate = creationDate,
            comment = comment,
            projectIri = projectIri,
            directLinkExists = directLinkExists,
            targetResourceClass = targetResourceClass,
            permissionRelevantAssertions = permissionRelevantAssertions,
            permissionCode = permissionCode
          )
        )
    } else {
      Future(None)
    }
  }

  /**
    * Verifies that a value was created.
    *
    * @param resourceIri          the IRI of the resource in which the value should have been created.
    * @param propertyIri          the IRI of the property that should point from the resource to the value.
    * @param unverifiedValue      the value that should have been created.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[CreateValueResponseV1]], or a failed [[Future]] if the value could not be found in
    *         the resource's version history.
    */
  private def verifyValueCreation(resourceIri: IRI,
                                  propertyIri: IRI,
                                  unverifiedValue: UnverifiedValueV1,
                                  featureFactoryConfig: FeatureFactoryConfig,
                                  userProfile: UserADM): Future[CreateValueResponseV1] = {
    unverifiedValue.value match {
      case linkUpdateV1: LinkUpdateV1 =>
        for {
          linkValueQueryResult <- verifyLinkUpdate(
            linkSourceIri = resourceIri,
            linkPropertyIri = propertyIri,
            linkTargetIri = linkUpdateV1.targetResourceIri,
            linkValueIri = unverifiedValue.newValueIri,
            featureFactoryConfig = featureFactoryConfig,
            userProfile = userProfile
          )

          apiResponseValue = LinkV1(
            targetResourceIri = linkUpdateV1.targetResourceIri,
            valueResourceClass = linkValueQueryResult.targetResourceClass
          )
        } yield
          CreateValueResponseV1(
            value = apiResponseValue,
            comment = linkValueQueryResult.comment,
            id = unverifiedValue.newValueIri,
            rights = linkValueQueryResult.permissionCode
          )

      case ordinaryUpdateValueV1 =>
        for {
          verifyUpdateResult <- verifyOrdinaryValueUpdate(
            resourceIri = resourceIri,
            propertyIri = propertyIri,
            searchValueIri = unverifiedValue.newValueIri,
            featureFactoryConfig = featureFactoryConfig,
            userProfile = userProfile
          )

        } yield
          CreateValueResponseV1(
            value = verifyUpdateResult.value,
            comment = verifyUpdateResult.comment,
            id = unverifiedValue.newValueIri,
            rights = verifyUpdateResult.permissionCode
          )
    }
  }

  /**
    * Given the IRI of a value that should have been created, looks for the value in the resource's version history,
    * and returns details about it. If the value is not found, throws [[UpdateNotPerformedException]].
    *
    * @param resourceIri          the IRI of the resource that may have the value.
    * @param propertyIri          the IRI of the property that may have have the value.
    * @param searchValueIri       the IRI of the value.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[ValueQueryResult]].
    */
  @throws(classOf[UpdateNotPerformedException])
  @throws(classOf[ForbiddenException])
  private def verifyOrdinaryValueUpdate(resourceIri: IRI,
                                        propertyIri: IRI,
                                        searchValueIri: IRI,
                                        featureFactoryConfig: FeatureFactoryConfig,
                                        userProfile: UserADM): Future[ValueQueryResult] = {
    for {
      // Do a SPARQL query to look for the value in the resource's version history.
      sparqlQuery <- Future {
        // Run the template function in a Future to handle exceptions (see http://git.iml.unibas.ch/salsah-suite/knora/wikis/futures-with-akka#handling-errors-with-futures)
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .findValueInVersions(
            triplestore = settings.triplestoreType,
            resourceIri = resourceIri,
            propertyIri = propertyIri,
            searchValueIri = searchValueIri
          )
          .toString()
      }

      updateVerificationResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(sparqlQuery))
        .mapTo[SparqlSelectResult]
      rows = updateVerificationResponse.results.bindings

      resultOption <- sparqlQueryResults2ValueQueryResult(
        valueIri = searchValueIri,
        rows = rows,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

    } yield
      resultOption.getOrElse(throw UpdateNotPerformedException(
        s"The update to value $searchValueIri for property $propertyIri in resource $resourceIri was not performed. Please report this as a possible bug."))
  }

  /**
    * Given information about a link that should have been created, verifies that the link exists, and returns
    * details about it. If the link has not been created, throws [[UpdateNotPerformedException]].
    *
    * @param linkSourceIri        the IRI of the resource that should be the source of the link.
    * @param linkPropertyIri      the IRI of the link property.
    * @param linkTargetIri        the IRI of the resource that should be the target of the link.
    * @param linkValueIri         the IRI of the `knora-base:LinkValue` that should have been created.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[LinkValueQueryResult]].
    */
  @throws(classOf[UpdateNotPerformedException])
  @throws(classOf[ForbiddenException])
  private def verifyLinkUpdate(linkSourceIri: IRI,
                               linkPropertyIri: IRI,
                               linkTargetIri: IRI,
                               linkValueIri: IRI,
                               featureFactoryConfig: FeatureFactoryConfig,
                               userProfile: UserADM): Future[LinkValueQueryResult] = {
    for {
      maybeLinkValueQueryResult <- findLinkValueByIri(
        subjectIri = linkSourceIri,
        predicateIri = linkPropertyIri,
        objectIri = Some(linkTargetIri),
        linkValueIri = linkValueIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      result = maybeLinkValueQueryResult match {
        case Some(linkValueQueryResult) =>
          if (!linkValueQueryResult.directLinkExists || linkValueQueryResult.targetResourceClass.isEmpty) {
            throw UpdateNotPerformedException()
          } else {
            linkValueQueryResult
          }

        case None =>
          throw UpdateNotPerformedException(
            s"The update to link value $linkValueIri with source IRI $linkSourceIri, link property $linkPropertyIri, and target $linkTargetIri was not performed. Please report this as a possible bug.")
      }
    } yield result
  }

  /**
    * Finds the object of the specified predicate in SPARQL query results describing a value.
    *
    * @param predicateIri the IRI of the predicate.
    * @param rows         the SPARQL query results that describe the value.
    * @return the predicate's object.
    */
  private def getValuePredicateObject(predicateIri: IRI, rows: Seq[VariableResultsRow]): Option[IRI] = {
    rows.find(_.rowMap("objPred") == predicateIri).map(_.rowMap("objObj"))
  }

  /**
    * The result of calling the `findResourceWithValue` method.
    *
    * @param resourceIri the IRI of the resource containing the value.
    * @param projectIri  the IRI of the resource's project.
    * @param propertyIri the IRI of the property pointing to the value.
    */
  case class FindResourceWithValueResult(resourceIri: IRI, projectIri: IRI, propertyIri: IRI)

  /**
    * Given a value IRI, finds the value's resource and property.
    *
    * @param valueIri the IRI of the value.
    * @return a [[FindResourceWithValueResult]].
    */
  private def findResourceWithValue(valueIri: IRI): Future[FindResourceWithValueResult] = {
    for {
      findResourceSparqlQuery <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.v1.txt
          .findResourceWithValue(
            triplestore = settings.triplestoreType,
            searchValueIri = valueIri
          )
          .toString())
      findResourceResponse <- (storeManager ? SparqlSelectRequest(findResourceSparqlQuery)).mapTo[SparqlSelectResult]

      _ = if (findResourceResponse.results.bindings.isEmpty) {
        throw NotFoundException(s"No resource found containing value $valueIri")
      }

      resultRowMap = findResourceResponse.getFirstRow.rowMap

      resourceIri = resultRowMap("resource")
      projectIri = resultRowMap("project")
      propertyIri = resultRowMap("property")
    } yield
      FindResourceWithValueResult(
        resourceIri = resourceIri,
        projectIri = projectIri,
        propertyIri = propertyIri
      )
  }

  /**
    * Creates a new value (either an ordinary value or a link), using an existing transaction, assuming that
    * pre-update checks have already been done.
    *
    * @param dataNamedGraph       the named graph in which the value is to be created.
    * @param projectIri           the IRI of the project in which to create the value.
    * @param resourceIri          the IRI of the resource in which to create the value.
    * @param propertyIri          the IRI of the property that will point from the resource to the value.
    * @param value                the value to create.
    * @param valueCreator         the IRI of the new value's owner.
    * @param valuePermissions     the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return an [[UnverifiedValueV1]].
    */
  private def createValueV1AfterChecks(dataNamedGraph: IRI,
                                       projectIri: IRI,
                                       resourceIri: IRI,
                                       propertyIri: IRI,
                                       value: UpdateValueV1,
                                       comment: Option[String],
                                       valueCreator: IRI,
                                       valuePermissions: String,
                                       featureFactoryConfig: FeatureFactoryConfig,
                                       userProfile: UserADM): Future[UnverifiedValueV1] = {
    value match {
      case linkUpdateV1: LinkUpdateV1 =>
        createLinkValueV1AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceIri = resourceIri,
          propertyIri = propertyIri,
          linkUpdateV1 = linkUpdateV1,
          comment = comment,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )

      case ordinaryUpdateValueV1 =>
        createOrdinaryValueV1AfterChecks(
          dataNamedGraph = dataNamedGraph,
          resourceIri = resourceIri,
          propertyIri = propertyIri,
          value = ordinaryUpdateValueV1,
          comment = comment,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )
    }
  }

  /**
    * Creates a link, using an existing transaction, assuming that pre-update checks have already been done.
    *
    * @param dataNamedGraph       the named graph in which the link is to be created.
    * @param resourceIri          the resource in which the link is to be created.
    * @param propertyIri          the link property.
    * @param linkUpdateV1         a [[LinkUpdateV1]] specifying the target resource.
    * @param valueCreator         the IRI of the new link value's owner.
    * @param valuePermissions     the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return an [[UnverifiedValueV1]].
    */
  private def createLinkValueV1AfterChecks(dataNamedGraph: IRI,
                                           resourceIri: IRI,
                                           propertyIri: IRI,
                                           linkUpdateV1: LinkUpdateV1,
                                           comment: Option[String],
                                           valueCreator: IRI,
                                           valuePermissions: String,
                                           featureFactoryConfig: FeatureFactoryConfig,
                                           userProfile: UserADM): Future[UnverifiedValueV1] = {
    for {
      sparqlTemplateLinkUpdate <- incrementLinkValue(
        sourceResourceIri = resourceIri,
        linkPropertyIri = propertyIri,
        targetResourceIri = linkUpdateV1.targetResourceIri,
        valueCreator = valueCreator,
        valuePermissions = valuePermissions,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      currentTime: Instant = Instant.now

      // Generate a SPARQL update string.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
        .createLink(
          dataNamedGraph = dataNamedGraph,
          triplestore = settings.triplestoreType,
          resourceIri = resourceIri,
          linkUpdate = sparqlTemplateLinkUpdate,
          creationDate = currentTime,
          maybeComment = comment,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Create link ===============")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
       */

      // Do the update.
      sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
    } yield
      UnverifiedValueV1(
        newValueIri = sparqlTemplateLinkUpdate.newLinkValueIri,
        value = linkUpdateV1
      )
  }

  /**
    * Creates an ordinary value (i.e. not a link), using an existing transaction, assuming that pre-update checks have already been done.
    *
    * @param resourceIri          the resource in which the value is to be created.
    * @param propertyIri          the property that should point to the value.
    * @param value                an [[UpdateValueV1]] describing the value.
    * @param valueCreator         the IRI of the new value's owner.
    * @param valuePermissions     the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return an [[UnverifiedValueV1]].
    */
  private def createOrdinaryValueV1AfterChecks(dataNamedGraph: IRI,
                                               resourceIri: IRI,
                                               propertyIri: IRI,
                                               value: UpdateValueV1,
                                               comment: Option[String],
                                               valueCreator: IRI,
                                               valuePermissions: String,
                                               featureFactoryConfig: FeatureFactoryConfig,
                                               userProfile: UserADM): Future[UnverifiedValueV1] = {
    // Generate an IRI for the new value.
    val newValueIri = stringFormatter.makeValueIri(resourceIri)
    val creationDate: Instant = Instant.now

    for {
      // If we're creating a text value, update direct links and LinkValues for any resource references in standoff.
      standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- value match {
        case textValueV1: TextValueWithStandoffV1 =>
          // Make sure the text value's list of resource references is correct.
          checkTextValueResourceRefs(textValueV1)

          // Construct a SparqlTemplateLinkUpdate for each reference that was added.
          val standoffLinkUpdatesForAddedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
            textValueV1.resource_reference.map { targetResourceIri =>
              incrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                targetResourceIri = targetResourceIri,
                valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                valuePermissions = standoffLinkValuePermissions,
                featureFactoryConfig = featureFactoryConfig,
                userProfile = userProfile
              )
            }.toVector

          Future.sequence(standoffLinkUpdatesForAddedResourceRefs)

        case _ => Future(Vector.empty[SparqlTemplateLinkUpdate])
      }

      // Generate a SPARQL update string.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
        .createValue(
          dataNamedGraph = dataNamedGraph,
          triplestore = settings.triplestoreType,
          resourceIri = resourceIri,
          propertyIri = propertyIri,
          newValueIri = newValueIri,
          valueTypeIri = value.valueTypeIri,
          value = value,
          linkUpdates = standoffLinkUpdates,
          maybeComment = comment,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          creationDate = creationDate,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Create value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
       */

      // Do the update.
      sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]
    } yield
      UnverifiedValueV1(
        newValueIri = newValueIri,
        value = value
      )
  }

  /**
    * Changes a link, assuming that pre-update checks have already been done.
    *
    * @param dataNamedGraph       the IRI of the named graph containing the link.
    * @param projectIri           the IRI of the project containing the link.
    * @param resourceIri          the IRI of the resource containing the link.
    * @param propertyIri          the IRI of the link property.
    * @param currentLinkValueV1   a [[LinkValueV1]] representing the `knora-base:LinkValue` for the existing link.
    * @param linkUpdateV1         a [[LinkUpdateV1]] indicating the new target resource.
    * @param comment              an optional comment on the new link value.
    * @param valueCreator         the IRI of the new link value's owner.
    * @param valuePermissions     the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[ChangeValueResponseV1]].
    */
  private def changeLinkValueV1AfterChecks(dataNamedGraph: IRI,
                                           projectIri: IRI,
                                           resourceIri: IRI,
                                           propertyIri: IRI,
                                           currentLinkValueV1: LinkValueV1,
                                           linkUpdateV1: LinkUpdateV1,
                                           comment: Option[String],
                                           valueCreator: IRI,
                                           valuePermissions: String,
                                           featureFactoryConfig: FeatureFactoryConfig,
                                           userProfile: UserADM): Future[ChangeValueResponseV1] = {
    for {
      // Delete the existing link and decrement its LinkValue's reference count.
      sparqlTemplateLinkUpdateForCurrentLink <- decrementLinkValue(
        sourceResourceIri = resourceIri,
        linkPropertyIri = propertyIri,
        targetResourceIri = currentLinkValueV1.objectIri,
        valueCreator = valueCreator,
        valuePermissions = valuePermissions,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Create a new link, and create a new LinkValue for it.
      sparqlTemplateLinkUpdateForNewLink <- incrementLinkValue(
        sourceResourceIri = resourceIri,
        linkPropertyIri = propertyIri,
        targetResourceIri = linkUpdateV1.targetResourceIri,
        valueCreator = valueCreator,
        valuePermissions = valuePermissions,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Get project info
      maybeProjectInfo <- {
        responderManager ? ProjectInfoByIRIGetV1(
          iri = projectIri,
          featureFactoryConfig = featureFactoryConfig,
          userProfileV1 = None
        )
      }.mapTo[Option[ProjectInfoV1]]

      projectInfo = maybeProjectInfo match {
        case Some(pi) => pi
        case None     => throw NotFoundException(s"Project '$projectIri' not found.")
      }

      // Make a timestamp to indicate when the link value was updated.
      currentTime: String = Instant.now.toString

      // Generate a SPARQL update string.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
        .changeLink(
          dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
          triplestore = settings.triplestoreType,
          linkSourceIri = resourceIri,
          linkUpdateForCurrentLink = sparqlTemplateLinkUpdateForCurrentLink,
          linkUpdateForNewLink = sparqlTemplateLinkUpdateForNewLink,
          maybeComment = comment,
          currentTime = currentTime,
          requestingUser = userProfile.id,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Update link ================")
            _ = println(sparqlUpdate)
            _ = println("=============================================")
       */

      // Do the update.
      sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

      // To find out whether the update succeeded, check that the new link is in the triplestore.
      linkValueQueryResult <- verifyLinkUpdate(
        linkSourceIri = resourceIri,
        linkPropertyIri = propertyIri,
        linkTargetIri = linkUpdateV1.targetResourceIri,
        linkValueIri = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      apiResponseValue = LinkV1(
        targetResourceIri = linkUpdateV1.targetResourceIri,
        valueResourceClass = linkValueQueryResult.targetResourceClass
      )

    } yield
      ChangeValueResponseV1(
        value = apiResponseValue,
        comment = linkValueQueryResult.comment,
        id = sparqlTemplateLinkUpdateForNewLink.newLinkValueIri,
        rights = linkValueQueryResult.permissionCode
      )
  }

  /**
    * Changes an ordinary value (i.e. not a link), assuming that pre-update checks have already been done.
    *
    * @param projectIri       the IRI of the project containing the value.
    * @param resourceIri      the IRI of the resource containing the value.
    * @param propertyIri      the IRI of the property that points to the value.
    * @param currentValueIri  the IRI of the existing value.
    * @param currentValueV1   an [[ApiValueV1]] representing the existing value.
    * @param newValueIri      the IRI of the new value.
    * @param updateValueV1    an [[UpdateValueV1]] representing the new value.
    * @param comment          an optional comment on the new value.
    * @param valueCreator     the IRI of the new value's owner.
    * @param valuePermissions the literal that should be used as the object of the new value's `knora-base:hasPermissions` predicate.
    * @param userProfile      the profile of the user making the request.
    * @return a [[ChangeValueResponseV1]].
    */
  private def changeOrdinaryValueV1AfterChecks(projectIri: IRI,
                                               resourceIri: IRI,
                                               propertyIri: IRI,
                                               currentValueIri: IRI,
                                               currentValueV1: ApiValueV1,
                                               newValueIri: IRI,
                                               updateValueV1: UpdateValueV1,
                                               comment: Option[String],
                                               valueCreator: IRI,
                                               valuePermissions: String,
                                               featureFactoryConfig: FeatureFactoryConfig,
                                               userProfile: UserADM): Future[ChangeValueResponseV1] = {
    for {
      // If we're adding a text value, update direct links and LinkValues for any resource references in Standoff.
      standoffLinkUpdates: Seq[SparqlTemplateLinkUpdate] <- (currentValueV1, updateValueV1) match {
        case (currentTextValue: TextValueV1, newTextValue: TextValueV1) =>
          // Make sure the new text value's list of resource references is correct.

          newTextValue match {
            case newTextWithStandoff: TextValueWithStandoffV1 =>
              checkTextValueResourceRefs(newTextWithStandoff)
            case textValueSimple: TextValueSimpleV1 => ()
          }

          // Identify the resource references that have been added or removed in the new version of
          // the value.
          val currentResourceRefs = currentTextValue match {
            case textValueWithStandoff: TextValueWithStandoffV1 =>
              textValueWithStandoff.resource_reference
            case textValueSimple: TextValueSimpleV1 => Set.empty[IRI]
          }

          val newResourceRefs = newTextValue match {
            case textValueWithStandoff: TextValueWithStandoffV1 =>
              textValueWithStandoff.resource_reference
            case textValueSimple: TextValueSimpleV1 => Set.empty[IRI]
          }
          val addedResourceRefs = newResourceRefs -- currentResourceRefs
          val removedResourceRefs = currentResourceRefs -- newResourceRefs

          // Construct a SparqlTemplateLinkUpdate for each reference that was added.
          val standoffLinkUpdatesForAddedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
            addedResourceRefs.toVector.map { targetResourceIri =>
              incrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                targetResourceIri = targetResourceIri,
                valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                valuePermissions = standoffLinkValuePermissions,
                featureFactoryConfig = featureFactoryConfig,
                userProfile = userProfile
              )
            }

          // Construct a SparqlTemplateLinkUpdate for each reference that was removed.
          val standoffLinkUpdatesForRemovedResourceRefs: Seq[Future[SparqlTemplateLinkUpdate]] =
            removedResourceRefs.toVector.map { removedTargetResource =>
              decrementLinkValue(
                sourceResourceIri = resourceIri,
                linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                targetResourceIri = removedTargetResource,
                valueCreator = OntologyConstants.KnoraAdmin.SystemUser,
                valuePermissions = standoffLinkValuePermissions,
                featureFactoryConfig = featureFactoryConfig,
                userProfile = userProfile
              )
            }

          Future.sequence(standoffLinkUpdatesForAddedResourceRefs ++ standoffLinkUpdatesForRemovedResourceRefs)

        case _ => Future(Vector.empty[SparqlTemplateLinkUpdate])
      }

      // Get project info
      maybeProjectInfo <- {
        responderManager ? ProjectInfoByIRIGetV1(
          iri = projectIri,
          featureFactoryConfig = featureFactoryConfig,
          userProfileV1 = None
        )
      }.mapTo[Option[ProjectInfoV1]]

      projectInfo = maybeProjectInfo match {
        case Some(pi) => pi
        case None     => throw NotFoundException(s"Project '$projectIri' not found.")
      }

      // Make a timestamp to indicate when the value was updated.
      currentTime: String = Instant.now.toString

      // Generate a SPARQL update.
      sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
        .addValueVersion(
          dataNamedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfo),
          triplestore = settings.triplestoreType,
          resourceIri = resourceIri,
          propertyIri = propertyIri,
          currentValueIri = currentValueIri,
          newValueIri = newValueIri,
          valueTypeIri = updateValueV1.valueTypeIri,
          value = updateValueV1,
          valueCreator = valueCreator,
          valuePermissions = valuePermissions,
          maybeComment = comment,
          linkUpdates = standoffLinkUpdates,
          currentTime = currentTime,
          requestingUser = userProfile.id,
          stringFormatter = stringFormatter
        )
        .toString()

      /*
            _ = println("================ Update value ================")
            _ = println(sparqlUpdate)
            _ = println("==============================================")
       */

      // Do the update.
      sparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(sparqlUpdate)).mapTo[SparqlUpdateResponse]

      // To find out whether the update succeeded, look for the new value in the triplestore.
      verifyUpdateResult <- verifyOrdinaryValueUpdate(
        resourceIri = resourceIri,
        propertyIri = propertyIri,
        searchValueIri = newValueIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )
    } yield
      ChangeValueResponseV1(
        value = verifyUpdateResult.value,
        comment = verifyUpdateResult.comment,
        id = newValueIri,
        rights = verifyUpdateResult.permissionCode
      )
  }

  /**
    * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to create a `LinkValue` or to
    * increment the reference count of an existing `LinkValue`. This happens in two cases:
    *
    *  - When the user creates a link. In this case, neither the link nor the `LinkValue` exist yet. The
    * [[SparqlTemplateLinkUpdate]] will specify that the link should be created, and that the `LinkValue` should be
    * created with a reference count of 1.
    *  - When a text value is updated so that its standoff markup refers to a resource that it did not previously
    * refer to. Here there are two possibilities:
    *    - If there is currently a `knora-base:hasStandoffLinkTo` link between the source and target resources, with a
    * corresponding `LinkValue`, a new version of the `LinkValue` will be made, with an incremented reference count.
    *    - If that link and `LinkValue` don't yet exist, they will be created, and the `LinkValue` will be given
    * a reference count of 1.
    *
    * @param sourceResourceIri    the IRI of the source resource.
    * @param linkPropertyIri      the IRI of the property that links the source resource to the target resource.
    * @param targetResourceIri    the IRI of the target resource.
    * @param valueCreator         the IRI of the new link value's owner.
    * @param valuePermissions     the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
    */
  private def incrementLinkValue(sourceResourceIri: IRI,
                                 linkPropertyIri: IRI,
                                 targetResourceIri: IRI,
                                 valueCreator: IRI,
                                 valuePermissions: String,
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 userProfile: UserADM): Future[SparqlTemplateLinkUpdate] = {
    for {
      // Check whether a LinkValue already exists for this link.
      maybeLinkValueQueryResult <- findLinkValueByLinkTriple(
        subjectIri = sourceResourceIri,
        predicateIri = linkPropertyIri,
        objectIri = targetResourceIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Check that the target resource actually exists and is a knora-base:Resource.
      targetIriCheckResult <- checkStandoffResourceReferenceTargets(
        targetIris = Set(targetResourceIri),
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Generate an IRI for the new LinkValue.
      newLinkValueIri = stringFormatter.makeValueIri(sourceResourceIri)

      linkUpdate = maybeLinkValueQueryResult match {
        case Some(linkValueQueryResult) =>
          // There's already a LinkValue for links between these two resources. Increment
          // its reference count.
          val currentReferenceCount = linkValueQueryResult.value.referenceCount
          val newReferenceCount = currentReferenceCount + 1
          val insertDirectLink = !linkValueQueryResult.directLinkExists

          SparqlTemplateLinkUpdate(
            linkPropertyIri = linkPropertyIri.toSmartIri,
            directLinkExists = linkValueQueryResult.directLinkExists,
            insertDirectLink = insertDirectLink,
            deleteDirectLink = false,
            linkValueExists = true,
            linkTargetExists = true,
            newLinkValueIri = newLinkValueIri,
            linkTargetIri = targetResourceIri,
            currentReferenceCount = currentReferenceCount,
            newReferenceCount = newReferenceCount,
            newLinkValueCreator = valueCreator,
            newLinkValuePermissions = valuePermissions
          )

        case None =>
          // There's no LinkValue for links between these two resources, so create one, and give it
          // a reference count of 1.
          SparqlTemplateLinkUpdate(
            linkPropertyIri = linkPropertyIri.toSmartIri,
            directLinkExists = false,
            insertDirectLink = true,
            deleteDirectLink = false,
            linkValueExists = false,
            linkTargetExists = true,
            newLinkValueIri = newLinkValueIri,
            linkTargetIri = targetResourceIri,
            currentReferenceCount = 0,
            newReferenceCount = 1,
            newLinkValueCreator = valueCreator,
            newLinkValuePermissions = valuePermissions
          )
      }
    } yield linkUpdate
  }

  /**
    * Generates a [[SparqlTemplateLinkUpdate]] to tell a SPARQL update template how to decrement the reference count
    * of a `LinkValue`. This happens in two cases:
    *
    *  - When the user deletes (or changes) a user-created link. In this case, the current reference count will be 1.
    * The existing link will be removed. A new version of the `LinkValue` be made with a reference count of 0, and
    * will be marked as deleted.
    *  - When a resource reference is removed from standoff markup on a text value, so that the text value no longer
    * contains any references to that target resource. In this case, a new version of the `LinkValue` will be
    * made, with a decremented reference count. If the new reference count is 0, the link will be removed and the
    * `LinkValue` will be marked as deleted.
    *
    * @param sourceResourceIri    the IRI of the source resource.
    * @param linkPropertyIri      the IRI of the property that links the source resource to the target resource.
    * @param targetResourceIri    the IRI of the target resource.
    * @param valueCreator         the IRI of the new link value's owner.
    * @param valuePermissions     the literal that should be used as the object of the new link value's `knora-base:hasPermissions` predicate.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a [[SparqlTemplateLinkUpdate]] that can be passed to a SPARQL update template.
    */
  private def decrementLinkValue(sourceResourceIri: IRI,
                                 linkPropertyIri: IRI,
                                 targetResourceIri: IRI,
                                 valueCreator: IRI,
                                 valuePermissions: String,
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 userProfile: UserADM): Future[SparqlTemplateLinkUpdate] = {
    for {
      // Query the LinkValue to ensure that it exists and to get its contents.
      maybeLinkValueQueryResult <- findLinkValueByLinkTriple(
        subjectIri = sourceResourceIri,
        predicateIri = linkPropertyIri,
        objectIri = targetResourceIri,
        featureFactoryConfig = featureFactoryConfig,
        userProfile = userProfile
      )

      // Did we find it?
      linkUpdate = maybeLinkValueQueryResult match {
        case Some(linkValueQueryResult) =>
          // Yes. Make a SparqlTemplateLinkUpdate.

          // Decrement the LinkValue's reference count.
          val currentReferenceCount = linkValueQueryResult.value.referenceCount
          val newReferenceCount = currentReferenceCount - 1

          // If the new reference count is 0, specify that the direct link between the source and target
          // resources should be removed.
          val deleteDirectLink = linkValueQueryResult.directLinkExists && newReferenceCount == 0

          // Generate an IRI for the new LinkValue.
          val newLinkValueIri = stringFormatter.makeValueIri(sourceResourceIri)

          SparqlTemplateLinkUpdate(
            linkPropertyIri = linkPropertyIri.toSmartIri,
            directLinkExists = linkValueQueryResult.directLinkExists,
            insertDirectLink = false,
            deleteDirectLink = deleteDirectLink,
            linkValueExists = true,
            linkTargetExists = true,
            newLinkValueIri = newLinkValueIri,
            linkTargetIri = targetResourceIri,
            currentReferenceCount = currentReferenceCount,
            newReferenceCount = newReferenceCount,
            newLinkValueCreator = valueCreator,
            newLinkValuePermissions = valuePermissions
          )

        case None =>
          // We didn't find the LinkValue. This shouldn't happen.
          throw InconsistentRepositoryDataException(
            s"There should be a knora-base:LinkValue describing a direct link from resource $sourceResourceIri to resource $targetResourceIri using property $linkPropertyIri, but it seems to be missing")
      }
    } yield linkUpdate
  }

  /**
    * Checks a [[TextValueV1]] to make sure that the resource references in its [[StandoffTagV2]] objects match
    * the list of resource IRIs in its `resource_reference` member variable.
    *
    * @param textValue the [[TextValueV1]] to be checked.
    */
  @throws(classOf[BadRequestException])
  private def checkTextValueResourceRefs(textValue: TextValueWithStandoffV1): Unit = {

    // please note that the function `StringFormatter.getResourceIrisFromStandoffTags` is not used here
    // because we want a double check (the function has already been called in the route or in standoff responder)
    val resourceRefsInStandoff: Set[IRI] = textValue.standoff.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], standoffNode: StandoffTagV2) =>
        if (standoffNode.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag)) {
          val maybeTargetIri: Option[IRI] = standoffNode.attributes.collectFirst {
            case iriTagAttr: StandoffTagIriAttributeV2
                if iriTagAttr.standoffPropertyIri.toString == OntologyConstants.KnoraBase.StandoffTagHasLink =>
              iriTagAttr.value
          }

          acc + maybeTargetIri.getOrElse(throw NotFoundException(s"No link found in $standoffNode"))
        } else {
          acc
        }
    }

    if (resourceRefsInStandoff != textValue.resource_reference) {
      throw BadRequestException(
        s"The list of resource references in this text value does not match the resource references in its Standoff markup: $textValue")
    }
  }

  /**
    * Given a set of IRIs of standoff resource reference targets, checks that each one actually refers to a `knora-base:Resource`.
    *
    * @param targetIris           the IRIs to check.
    * @param featureFactoryConfig the feature factory configuration.
    * @param userProfile          the profile of the user making the request.
    * @return a `Future[Unit]` on success, otherwise a `Future` containing an exception ([[NotFoundException]] if the target resource is not found,
    *         or [[BadRequestException]] if the target IRI isn't a `knora-base:Resource`).
    */
  private def checkStandoffResourceReferenceTargets(targetIris: Set[IRI],
                                                    featureFactoryConfig: FeatureFactoryConfig,
                                                    userProfile: UserADM): Future[Unit] = {
    if (targetIris.isEmpty) {
      Future(())
    } else {
      val targetIriCheckFutures: Set[Future[Unit]] = targetIris.map { targetIri =>
        for {
          checkTargetClassResponse <- (responderManager ? ResourceCheckClassRequestV1(
            resourceIri = targetIri,
            owlClass = OntologyConstants.KnoraBase.Resource,
            featureFactoryConfig = featureFactoryConfig,
            userProfile = userProfile
          )).mapTo[ResourceCheckClassResponseV1]

          _ = if (!checkTargetClassResponse.isInClass)
            throw BadRequestException(
              s"$targetIri cannot be the object of a standoff resource reference, because it is not a knora-base:Resource")
        } yield ()
      }

      for {
        targetIriChecks: Set[Unit] <- Future.sequence(targetIriCheckFutures)
      } yield targetIriChecks.head
    }
  }

  /**
    * Implements a pre-update check to ensure that an [[UpdateValueV1]] has the correct type for the `knora-base:objectClassConstraint` of
    * the property that is supposed to point to it.
    *
    * @param propertyIri                   the IRI of the property.
    * @param propertyObjectClassConstraint the IRI of the `knora-base:objectClassConstraint` of the property.
    * @param updateValueV1                 the value to be updated.
    * @param featureFactoryConfig          the feature factory configuration.
    * @param userProfile                   the profile of the user making the request.
    * @return an empty [[Future]] on success, or a failed [[Future]] if the value has the wrong type.
    */
  private def checkPropertyObjectClassConstraintForValue(propertyIri: IRI,
                                                         propertyObjectClassConstraint: IRI,
                                                         updateValueV1: UpdateValueV1,
                                                         featureFactoryConfig: FeatureFactoryConfig,
                                                         userProfile: UserADM): Future[Unit] = {
    for {
      result <- updateValueV1 match {
        case linkUpdate: LinkUpdateV1 =>
          // We're creating a link. Ask the resources responder to check the OWL class of the target resource.
          for {
            checkTargetClassResponse <- (responderManager ? ResourceCheckClassRequestV1(
              resourceIri = linkUpdate.targetResourceIri,
              owlClass = propertyObjectClassConstraint,
              featureFactoryConfig = featureFactoryConfig,
              userProfile = userProfile
            )).mapTo[ResourceCheckClassResponseV1]

            _ = if (!checkTargetClassResponse.isInClass) {
              throw OntologyConstraintException(
                s"Resource ${linkUpdate.targetResourceIri} cannot be the target of property $propertyIri, because it is not a member of OWL class $propertyObjectClassConstraint")
            }
          } yield ()

        case otherValue =>
          // We're creating an ordinary value. Check that its type is valid for the property's knora-base:objectClassConstraint.
          valueUtilV1.checkValueTypeForPropertyObjectClassConstraint(
            propertyIri = propertyIri,
            propertyObjectClassConstraint = propertyObjectClassConstraint,
            valueType = otherValue.valueTypeIri,
            responderManager = responderManager,
            userProfile = userProfile
          )
      }
    } yield result
  }

  /**
    * The permissions that are granted by every `knora-base:LinkValue` describing a standoff link.
    */
  lazy val standoffLinkValuePermissions: String = {
    val permissions: Set[PermissionADM] = Set(
      PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.SystemUser),
      PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
    )

    PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)
  }
}
