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
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{BadRequestException, InconsistentRepositoryDataException}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupGetResponseADM, MultipleGroupsGetRequestADM}
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType.PermissionType
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionType}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.{LiteralV2, SparqlExtendedConstructResponse}
import org.knora.webapi.messages.util.GroupedProps.{ValueLiterals, ValueProps}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A utility that responder actors use to determine a user's permissions on an RDF entity in the triplestore.
  */
object PermissionUtilADM extends LazyLogging {

  // TODO: unify EntityPermission with PermissionADM.

  /**
    * Represents a permission granted to a group on an entity. The `toString` method of an `EntityPermission`
    * returns one of the codes in [[OntologyConstants.KnoraBase.EntityPermissionAbbreviations]].
    */
  sealed trait EntityPermission extends Ordered[EntityPermission] {

    /**
      * Represents this [[EntityPermission]] as an integer, as required by Knora API v1.
      */
    def toInt: Int

    override def compare(that: EntityPermission): Int = this.toInt - that.toInt

    def getName: String

    def toPermissionADM(groupIri: IRI): PermissionADM
  }

  /**
    * Represents restricted view permission on an entity.
    */
  case object RestrictedViewPermission extends EntityPermission {
    override def toInt: Int = 1

    override def toString: String = OntologyConstants.KnoraBase.RestrictedViewPermission

    override val getName: String = "restricted view permission"

    override def toPermissionADM(groupIri: IRI): PermissionADM = {
      PermissionADM.restrictedViewPermission(groupIri)
    }
  }

  /**
    * Represents unrestricted view permission on an entity.
    */
  case object ViewPermission extends EntityPermission {
    override def toInt: Int = 2

    override def toString: String = OntologyConstants.KnoraBase.ViewPermission

    override val getName: String = "view permission"

    override def toPermissionADM(groupIri: IRI): PermissionADM = {
      PermissionADM.viewPermission(groupIri)
    }
  }

  /**
    * Represents modify permission on an entity.
    */
  case object ModifyPermission extends EntityPermission {
    override def toInt: Int = 6

    override def toString: String = OntologyConstants.KnoraBase.ModifyPermission

    override val getName: String = "modify permission"

    override def toPermissionADM(groupIri: IRI): PermissionADM = {
      PermissionADM.modifyPermission(groupIri)
    }
  }

  /**
    * Represents delete permission on an entity.
    */
  case object DeletePermission extends EntityPermission {
    override def toInt: Int = 7

    override def toString: String = OntologyConstants.KnoraBase.DeletePermission

    override val getName: String = "delete permission"

    override def toPermissionADM(groupIri: IRI): PermissionADM = {
      PermissionADM.deletePermission(groupIri)
    }
  }

  /**
    * Represents permission to change the permissions on an entity.
    */
  case object ChangeRightsPermission extends EntityPermission {
    override def toInt: Int = 8

    override def toString: String = OntologyConstants.KnoraBase.ChangeRightsPermission

    override val getName: String = "change rights permission"

    override def toPermissionADM(groupIri: IRI): PermissionADM = {
      PermissionADM.changeRightsPermission(groupIri)
    }
  }

  /**
    * The highest permission, i.e. the one that is least restrictive.
    */
  private val MaxPermissionLevel: EntityPermission = ChangeRightsPermission

  private val permissionStringsToPermissionLevels: Map[String, EntityPermission] = Set(
    RestrictedViewPermission,
    ViewPermission,
    ModifyPermission,
    DeletePermission,
    ChangeRightsPermission
  ).map { level =>
    level.toString -> level
  }.toMap

  /**
    * A set of assertions that are relevant for calculating permissions.
    */
  private val permissionRelevantAssertions = Set(
    OntologyConstants.KnoraBase.AttachedToUser,
    OntologyConstants.KnoraBase.AttachedToProject,
    OntologyConstants.KnoraBase.HasPermissions
  )

  /**
    * Given the IRI of an RDF property, returns `true` if the property is relevant to calculating permissions. This
    * is the case if the property is [[OntologyConstants.KnoraBase.AttachedToUser]],
    * [[OntologyConstants.KnoraBase.AttachedToProject]], or
    * or [[OntologyConstants.KnoraBase.HasPermissions]].
    *
    * @param p the IRI of the property.
    * @return `true` if the property is relevant to calculating permissions.
    */
  def isPermissionRelevant(p: IRI): Boolean = permissionRelevantAssertions.contains(p)

  /**
    * Given a list of predicates and objects pertaining to a entity, returns only the ones that are relevant to
    * permissions (i.e. the permissions themselves, plus the creator and project).
    *
    * @param assertions a list containing the permission-relevant predicates and objects
    *                   pertaining to the entity. Other predicates will be filtered out.
    * @return a list of permission-relevant predicates and objects.
    */
  def filterPermissionRelevantAssertions(assertions: Seq[(IRI, IRI)]): Vector[(IRI, IRI)] = {
    assertions.filter {
      case (p, o) => isPermissionRelevant(p)
    }.toVector
  }

  /**
    * Given a [[ValueProps]] describing a `knora-base:Value`, returns the permission-relevant assertions contained
    * in the [[ValueProps]] (i.e. permission assertion, plus assertions about the entity's creator and project).
    *
    * @param valueProps a [[ValueProps]] describing a `knora-base:Value`.
    * @return a list of permission-relevant predicates and objects.
    */
  def filterPermissionRelevantAssertionsFromValueProps(valueProps: ValueProps): Vector[(IRI, IRI)] = {
    valueProps.literalData.foldLeft(Vector.empty[(IRI, IRI)]) {
      case (acc, (predicate: IRI, ValueLiterals(literals))) =>
        if (isPermissionRelevant(predicate)) {
          acc ++ literals.map(literal => (predicate, literal))
        } else {
          acc
        }
    }
  }

  /**
    * Calculates the highest permission level a user can be granted on a entity.
    *
    * @param entityPermissions a map of permissions on a entity to the groups they are granted to.
    * @param userGroups        the groups that the user belongs to.
    * @return the code of the highest permission the user has on the entity, or `None` if the user has no permissions
    *         on the entity.
    */
  private def calculateHighestGrantedPermissionLevel(entityPermissions: Map[EntityPermission, Set[IRI]],
                                                     userGroups: Set[IRI]): Option[EntityPermission] = {
    // Make a set of all the permissions the user can obtain for this entity.
    val permissionLevels: Set[EntityPermission] = entityPermissions.foldLeft(Set.empty[EntityPermission]) {
      case (acc, (permissionLevel, grantedToGroups)) =>
        if (grantedToGroups.intersect(userGroups).nonEmpty) {
          acc + permissionLevel
        } else {
          acc
        }
    }

    if (permissionLevels.nonEmpty) {
      // The user has some permissions; return the code of the highest one.
      Some(permissionLevels.max)
    } else {
      // The user has no permissions.
      None
    }
  }

  /**
    * Determines the permissions that a user has on a entity, and returns an [[EntityPermission]].
    *
    * @param entityCreator           the IRI of the user that created the entity.
    * @param entityProject           the IRI of the entity's project.
    * @param entityPermissionLiteral the literal that is the object of the entity's `knora-base:hasPermissions` predicate.
    * @param requestingUser          the user making the request.
    * @return an [[EntityPermission]] representing the user's permission level for the entity, or `None` if the user
    *         has no permissions on the entity.
    */
  def getUserPermissionADM(entityCreator: IRI,
                           entityProject: IRI,
                           entityPermissionLiteral: String,
                           requestingUser: UserADM): Option[EntityPermission] = {
    val maybePermissionLevel =
      if (requestingUser.isSystemUser || requestingUser.isSystemAdmin || requestingUser.permissions
            .hasProjectAdminAllPermissionFor(entityProject)) {
        // If the user is the system user, is in the SystemAdmin group, or has ProjectAdminAllPermission, just give them the maximum permission.
        Some(MaxPermissionLevel)
      } else {
        val entityPermissions: Map[EntityPermission, Set[IRI]] = parsePermissions(entityPermissionLiteral)

        // Make a list of all the groups (both built-in and custom) that the user belongs to in relation
        // to the entity.
        val userGroups: Set[IRI] = if (requestingUser.isAnonymousUser) {
          // The user is an unknown user; put them in the UnknownUser built-in group.
          Set(OntologyConstants.KnoraAdmin.UnknownUser)
        } else {
          // The user is a known user.
          // If the user is the creator of the entity, put the user in the "creator" built-in group.
          val creatorOption = if (requestingUser.id == entityCreator) {
            Some(OntologyConstants.KnoraAdmin.Creator)
          } else {
            None
          }

          val otherGroups = requestingUser.permissions.groupsPerProject.get(entityProject) match {
            case Some(groups) => groups
            case None         => Set.empty[IRI]
          }

          // Make the complete list of the user's groups: KnownUser, the user's built-in (e.g., ProjectAdmin,
          // ProjectMember) and non-built-in groups, possibly creator, and possibly SystemAdmin.
          Set(OntologyConstants.KnoraAdmin.KnownUser) ++ otherGroups ++ creatorOption
        }

        // Find the highest permission that can be granted to the user.
        calculateHighestGrantedPermissionLevel(entityPermissions, userGroups) match {
          case Some(highestPermissionLevel) => Some(highestPermissionLevel)

          case None =>
            // If the result is that they would get no permissions, give them user whatever permission an
            // unknown user would have.
            calculateHighestGrantedPermissionLevel(entityPermissions, Set(OntologyConstants.KnoraAdmin.UnknownUser))
        }
      }

    maybePermissionLevel
  }

  /**
    * A trait representing a result returned by [[comparePermissionsADM]].
    */
  sealed trait PermissionComparisonResult

  /**
    * Indicates that the user would have a lower permission with permission string A.
    */
  case object ALessThanB extends PermissionComparisonResult

  /**
    * Indicates that permission strings A and B would give the user the same permission.
    */
  case object AEqualToB extends PermissionComparisonResult

  /**
    * Indicates that the user would have a higher permission with permission string A.
    */
  case object AGreaterThanB extends PermissionComparisonResult

  /**
    * Calculates the permissions that the specified user would have on an entity with two permission strings,
    * and returns:
    *
    * - [[ALessThanB]] if the user would have a lower permission with `permissionLiteralA`.
    * - [[AEqualToB]] if `permissionLiteralA` and `permissionLiteralB` would give the user the same permission.
    * - [[AGreaterThanB]] if the user would have a higher permission with `permissionLiteralA`.
    *
    * @param entityCreator      the IRI of the user that created the entity.
    * @param entityProject      the IRI of the entity's project.
    * @param permissionLiteralA the first permission string.
    * @param permissionLiteralB the second permission string.
    * @param requestingUser     the user making the request.
    * @return a [[PermissionComparisonResult]].
    */
  def comparePermissionsADM(entityCreator: IRI,
                            entityProject: IRI,
                            permissionLiteralA: String,
                            permissionLiteralB: String,
                            requestingUser: UserADM): PermissionComparisonResult = {
    val maybePermissionA: Option[EntityPermission] = getUserPermissionADM(
      entityCreator = requestingUser.id,
      entityProject = entityProject,
      entityPermissionLiteral = permissionLiteralA,
      requestingUser = requestingUser
    )

    val maybePermissionB: Option[EntityPermission] = getUserPermissionADM(
      entityCreator = requestingUser.id,
      entityProject = entityProject,
      entityPermissionLiteral = permissionLiteralB,
      requestingUser = requestingUser
    )

    (maybePermissionA, maybePermissionB) match {
      case (None, None)    => AEqualToB
      case (None, Some(_)) => ALessThanB
      case (Some(_), None) => AGreaterThanB

      case (Some(permissionA: EntityPermission), Some(permissionB: EntityPermission)) =>
        if (permissionA == permissionB) {
          AEqualToB
        } else if (permissionA < permissionB) {
          ALessThanB
        } else {
          AGreaterThanB
        }
    }
  }

  /**
    * Given data from a [[SparqlExtendedConstructResponse]], determines the permissions that a user has on a entity,
    * and returns an [[EntityPermission]].
    *
    * @param entityIri      the IRI of the entity.
    * @param assertions     a [[Seq]] containing all the permission-relevant predicates and objects
    *                       pertaining to the entity. The predicates must include
    *                       [[OntologyConstants.KnoraBase.AttachedToUser]] and
    *                       [[OntologyConstants.KnoraBase.AttachedToProject]], and should include
    *                       [[OntologyConstants.KnoraBase.HasPermissions]].
    *                       Other predicates may be included, but they will be ignored, so there is no need to filter
    *                       them before passing them to this function.
    * @param requestingUser the profile of the user making the request.
    * @return a code representing the user's permission level for the entity.
    */
  def getUserPermissionFromConstructAssertionsADM(entityIri: IRI,
                                                  assertions: ConstructPredicateObjects,
                                                  requestingUser: UserADM): Option[EntityPermission] = {
    val assertionsAsStrings: Seq[(IRI, String)] = assertions.toSeq.flatMap {
      case (pred: SmartIri, objs: Seq[LiteralV2]) =>
        objs.map { obj =>
          pred.toString -> obj.toString
        }
    }

    getUserPermissionFromAssertionsADM(
      entityIri = entityIri,
      assertions = assertionsAsStrings,
      requestingUser = requestingUser
    )
  }

  /**
    * Determines the permissions that a user has on a entity, and returns an [[EntityPermission]].
    *
    * @param entityIri      the IRI of the entity.
    * @param assertions     a [[Seq]] containing all the permission-relevant predicates and objects
    *                       pertaining to the entity. The predicates must include
    *                       [[OntologyConstants.KnoraBase.AttachedToUser]] and
    *                       [[OntologyConstants.KnoraBase.AttachedToProject]], and should include
    *                       [[OntologyConstants.KnoraBase.HasPermissions]].
    *                       Other predicates may be included, but they will be ignored, so there is no need to filter
    *                       them before passing them to this function.
    * @param requestingUser the profile of the user making the request.
    * @return a code representing the user's permission level for the entity.
    */
  def getUserPermissionFromAssertionsADM(entityIri: IRI,
                                         assertions: Seq[(IRI, String)],
                                         requestingUser: UserADM): Option[EntityPermission] = {
    // Get the entity's creator, project, and permissions.
    val assertionMap: Map[IRI, String] = assertions.toMap

    // Anything with permissions must have an creator and a project.
    val entityCreator: IRI = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.AttachedToUser,
      throw InconsistentRepositoryDataException(s"Entity $entityIri has no creator"))
    val entityProject: IRI = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.AttachedToProject,
      throw InconsistentRepositoryDataException(s"Entity $entityIri has no project"))
    val entityPermissionLiteral: String = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.HasPermissions,
      throw InconsistentRepositoryDataException(s"Entity $entityIri has no knora-base:hasPermissions predicate"))

    getUserPermissionADM(
      entityCreator = entityCreator,
      entityProject = entityProject,
      entityPermissionLiteral = entityPermissionLiteral,
      requestingUser = requestingUser
    )
  }

  /**
    * Parses the literal object of the predicate `knora-base:hasPermissions`.
    *
    * @param permissionLiteral the literal to parse.
    * @return a [[Map]] in which the keys are permission abbreviations in
    *         [[OntologyConstants.KnoraBase.EntityPermissionAbbreviations]], and the values are sets of
    *         user group IRIs.
    */
  def parsePermissions(permissionLiteral: String, errorFun: String => Nothing = { permissionLiteral: String =>
    throw InconsistentRepositoryDataException(s"invalid permission literal: $permissionLiteral")
  }): Map[EntityPermission, Set[IRI]] = {
    val permissions: Seq[String] = permissionLiteral.split(OntologyConstants.KnoraBase.PermissionListDelimiter)

    permissions.map { permission =>
      val splitPermission: Array[String] = permission.split(' ')

      if (splitPermission.length != 2) {
        errorFun(permissionLiteral)
      }

      val abbreviation: String = splitPermission(0)

      if (!OntologyConstants.KnoraBase.EntityPermissionAbbreviations.contains(abbreviation)) {
        errorFun(permissionLiteral)
      }

      val shortGroups: Set[String] = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
      val groups = shortGroups.map(
        _.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                  OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion))
      (permissionStringsToPermissionLevels(abbreviation), groups)
    }.toMap
  }

  /**
    * Parses the literal object of the predicate `knora-base:hasPermissions`.
    *
    * @param maybePermissionListStr the literal to parse.
    * @return a [[Map]] in which the keys are permission abbreviations in
    *         [[OntologyConstants.KnoraBase.EntityPermissionAbbreviations]], and the values are sets of
    *         user group IRIs.
    */
  def parsePermissionsWithType(maybePermissionListStr: Option[String],
                               permissionType: PermissionType): Set[PermissionADM] = {
    maybePermissionListStr match {
      case Some(permissionListStr) => {
        val cleanedPermissionListStr = permissionListStr replaceAll ("[<>]", "")
        val permissions: Seq[String] =
          cleanedPermissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter)
        logger.debug(s"PermissionUtil.parsePermissionsWithType - split permissions: $permissions")
        permissions.flatMap { permission =>
          val splitPermission = permission.split(' ')
          val abbreviation = splitPermission(0)

          permissionType match {
            case PermissionType.AP =>
              if (!OntologyConstants.KnoraAdmin.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                throw InconsistentRepositoryDataException(s"Unrecognized permission abbreviation '$abbreviation'")
              }

              if (splitPermission.length > 1) {
                val shortGroups: Array[String] =
                  splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter)
                val groups: Set[IRI] = shortGroups
                  .map(
                    _.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                              OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion))
                  .toSet
                buildPermissionObject(abbreviation, groups)
              } else {
                buildPermissionObject(abbreviation, Set.empty[IRI])
              }

            case PermissionType.OAP =>
              if (!OntologyConstants.KnoraBase.EntityPermissionAbbreviations.contains(abbreviation)) {
                throw InconsistentRepositoryDataException(s"Unrecognized permission abbreviation '$abbreviation'")
              }
              val shortGroups: Array[String] = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter)
              val groups: Set[IRI] = shortGroups
                .map(
                  _.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                            OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion))
                .toSet
              buildPermissionObject(abbreviation, groups)
          }
        }
      }.toSet
      case None => Set.empty[PermissionADM]
    }
  }

  /**
    * Helper method used to convert the permission string stored inside the triplestore to a permission object.
    *
    * @param name the name of the permission.
    * @param iris the optional set of additional information (e.g., group IRIs, resource class IRIs).
    * @return a sequence of permission objects.
    */
  def buildPermissionObject(name: String, iris: Set[IRI]): Set[PermissionADM] = {
    name match {
      case OntologyConstants.KnoraAdmin.ProjectResourceCreateAllPermission =>
        Set(PermissionADM.ProjectResourceCreateAllPermission)

      case OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission =>
        if (iris.nonEmpty) {
          logger.debug(s"buildPermissionObject - ProjectResourceCreateRestrictedPermission - iris: $iris")
          iris.map(iri => PermissionADM.projectResourceCreateRestrictedPermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case OntologyConstants.KnoraAdmin.ProjectAdminAllPermission => Set(PermissionADM.ProjectAdminAllPermission)

      case OntologyConstants.KnoraAdmin.ProjectAdminGroupAllPermission =>
        Set(PermissionADM.ProjectAdminGroupAllPermission)

      case OntologyConstants.KnoraAdmin.ProjectAdminGroupRestrictedPermission =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.projectAdminGroupRestrictedPermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case OntologyConstants.KnoraAdmin.ProjectAdminRightsAllPermission =>
        Set(PermissionADM.ProjectAdminRightsAllPermission)

      case OntologyConstants.KnoraBase.ChangeRightsPermission =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.changeRightsPermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case OntologyConstants.KnoraBase.DeletePermission =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.deletePermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case OntologyConstants.KnoraBase.ModifyPermission =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.modifyPermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case OntologyConstants.KnoraBase.ViewPermission =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.viewPermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case OntologyConstants.KnoraBase.RestrictedViewPermission =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.restrictedViewPermission(iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }
    }

  }

  /**
    * Helper method used to remove remove duplicate permissions.
    *
    * @param permissions the sequence of permissions with possible duplicates.
    * @return a set containing only unique permission.
    */
  def removeDuplicatePermissions(permissions: Seq[PermissionADM]): Set[PermissionADM] = {

    val result = permissions.groupBy(perm => perm.name + perm.additionalInformation).map { case (k, v) => v.head }.toSet
    //log.debug(s"removeDuplicatePermissions - result: $result")
    result
  }

  /**
    * Helper method used to remove lesser permissions, i.e. permissions which are already given by
    * the highest permission.
    *
    * @param permissions    a set of permissions possibly containing lesser permissions.
    * @param permissionType the type of permissions.
    * @return a set of permissions without possible lesser permissions.
    */
  def removeLesserPermissions(permissions: Set[PermissionADM], permissionType: PermissionType): Set[PermissionADM] = {
    permissionType match {
      case PermissionType.OAP =>
        if (permissions.nonEmpty) {
          /* Handling object access permissions which always have 'additionalInformation' and 'permissionCode' set */
          permissions
            .groupBy(_.additionalInformation)
            .map {
              case (groupIri, perms) =>
                // sort in descending order and then take the first one (the highest permission)
                perms.toArray.sortWith(_.permissionCode.get > _.permissionCode.get).head
            }
            .toSet
        } else {
          Set.empty[PermissionADM]
        }

      case PermissionType.AP => ???
    }
  }

  /**
    * Helper method used to transform a set of permissions into a permissions string ready to be written into the
    * triplestore as the value for the 'knora-base:hasPermissions' property.
    *
    * @param permissions    the permissions to be formatted.
    * @param permissionType a [[PermissionType]] indicating the type of permissions to be formatted.
    * @return
    */
  def formatPermissionADMs(permissions: Set[PermissionADM], permissionType: PermissionType): String = {
    permissionType match {
      case PermissionType.OAP =>
        if (permissions.nonEmpty) {

          /* a map with permission names, shortened groups, and full group names. */
          val groupedPermissions: Map[String, String] = permissions.groupBy(_.name).map {
            case (name: String, perms: Set[PermissionADM]) =>
              val shortGroupsString = perms.toVector.sortBy(_.additionalInformation.get).foldLeft("") {
                case (acc: String, perm: PermissionADM) =>
                  if (acc.isEmpty) {
                    acc + perm.additionalInformation.get.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion,
                                                                 OntologyConstants.KnoraAdmin.KnoraAdminPrefix)
                  } else {
                    acc + OntologyConstants.KnoraBase.GroupListDelimiter + perm.additionalInformation.get.replace(
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion,
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefix)
                  }
              }
              (name, shortGroupsString)
          }

          /* Sort permissions in descending order */
          val sortedPermissions: Array[(String, String)] = groupedPermissions.toArray.sortWith { (left, right) =>
            permissionStringsToPermissionLevels(left._1) > permissionStringsToPermissionLevels(right._1)
          }

          /* create the permissions string */
          sortedPermissions.foldLeft("") { (acc, perm: (String, String)) =>
            if (acc.isEmpty) {
              acc + perm._1 + " " + perm._2
            } else {
              acc + OntologyConstants.KnoraBase.PermissionListDelimiter + perm._1 + " " + perm._2
            }
          }
        } else {
          throw InconsistentRepositoryDataException("Permissions cannot be empty")
        }
      case PermissionType.AP =>
        if (permissions.nonEmpty) {

          val permNames: Set[String] = permissions.map(_.name)

          /* creates the permissions string. something like "ProjectResourceCreateAllPermission|ProjectAdminAllPermission" */
          permNames.foldLeft("") { (acc, perm: String) =>
            if (acc.isEmpty) {
              acc + perm
            } else {
              acc + OntologyConstants.KnoraBase.PermissionListDelimiter + perm
            }
          }

        } else {
          throw InconsistentRepositoryDataException("Permissions cannot be empty")
        }
    }
  }

  /**
    * Given parsed custom permissions, checks that they refer to valid permissions and groups.
    *
    * @param parsedPermissions the parsed custom permissions.
    * @param featureFactoryConfig the feature factory configuration.
    * @param responderManager  a reference to the responder manager.
    * @param timeout           a timeout for `ask` messages.
    * @param executionContext  an execution context for futures.
    */
  def validatePermissions(
      parsedPermissions: Map[PermissionUtilADM.EntityPermission, Set[IRI]],
      featureFactoryConfig: FeatureFactoryConfig,
      responderManager: ActorRef)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Unit] =
    for {
      // Get the group IRIs that are mentioned, minus the built-in groups.
      projectSpecificGroupIris: Set[IRI] <- Future.successful(
        parsedPermissions.values.flatten.toSet -- OntologyConstants.KnoraAdmin.BuiltInGroups)

      stringFormatter = StringFormatter.getGeneralInstance

      validatedProjectSpecificGroupIris: Set[IRI] = projectSpecificGroupIris.map(iri =>
        stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid group IRI: $iri")))

      // Check that those groups exist.
      _ <- (responderManager ? MultipleGroupsGetRequestADM(
        groupIris = validatedProjectSpecificGroupIris,
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )).mapTo[Set[GroupGetResponseADM]]
    } yield ()

  /**
    * Given parsed custom permissions, reformats the given permission literal.
    *
    * @param parsedPermissions the parsed custom permissions.
    * @return the normalised and reformatted permission literal.
    */
  def reformatCustomPermission(parsedPermissions: Map[PermissionUtilADM.EntityPermission, Set[IRI]]): String = {
    // Reformat the permission literal.
    val permissionADMs: Set[PermissionADM] = parsedPermissions.flatMap {
      case (entityPermission, groupIris) =>
        groupIris.map { groupIri =>
          entityPermission.toPermissionADM(groupIri)
        }
    }.toSet
    formatPermissionADMs(permissions = permissionADMs, permissionType = PermissionType.OAP)
  }

  /**
    * if a custom permission is given for an entity (resource or value), it parses it and returns parsedPermission and
    * reformatted permission literal. Otherwise, returns the default permission of the entity and an empty set of parsed permissions.
    *
    * @param customPermissions the given custom permissions.
    * @param defaultPermissions the default permissions of the entity.
    * @return the parsed permissions and normalised and reformatted permission literal.
    */
  def parseAndReformatPermissions(
      customPermissions: Option[String],
      defaultPermissions: String): (Map[PermissionUtilADM.EntityPermission, Set[IRI]], String) =
    customPermissions match {
      case Some(permissionStr) =>
        val parsedPermissions = parsePermissions(permissionLiteral = permissionStr, errorFun = { literal =>
          throw BadRequestException(s"Invalid permission literal: $literal")
        })
        val reformattedCustomResourcePermissions =
          PermissionUtilADM.reformatCustomPermission(parsedPermissions)
        (parsedPermissions, reformattedCustomResourcePermissions)
      case None => (Map.empty[PermissionUtilADM.EntityPermission, Set[IRI]], defaultPermissions)
    }

  /////////////////////////////////////////
  // API v1 methods

  /**
    * Determines the permissions that a user has on a entity, and returns an integer permissions code.
    *
    * @param entityIri   the IRI of the entity.
    * @param assertions  a [[Seq]] containing all the permission-relevant predicates and objects
    *                    pertaining to the entity. The predicates must include
    *                    [[OntologyConstants.KnoraBase.AttachedToUser]] and
    *                    [[OntologyConstants.KnoraBase.AttachedToProject]], and should include
    *                    [[OntologyConstants.KnoraBase.HasPermissions]].
    *                    Other predicates may be included, but they will be ignored, so there is no need to filter
    *                    them before passing them to this function.
    * @param userProfile the profile of the user making the request.
    * @return a code representing the user's permission level for the entity.
    */
  def getUserPermissionFromAssertionsV1(entityIri: IRI,
                                        assertions: Seq[(IRI, String)],
                                        userProfile: UserProfileV1): Option[Int] = {
    // Get the entity's creator, project, and permissions.
    val assertionMap: Map[IRI, String] = assertions.toMap

    // Anything with permissions must have an creator and a project.
    val entityCreator: IRI = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.AttachedToUser,
      throw InconsistentRepositoryDataException(s"entity $entityIri has no creator"))
    val entityProject: IRI = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.AttachedToProject,
      throw InconsistentRepositoryDataException(s"entity $entityIri has no project"))
    val entityPermissionLiteral: String = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.HasPermissions,
      throw InconsistentRepositoryDataException(s"entity $entityIri has no knora-base:hasPermissions predicate"))

    getUserPermissionV1(entityIri = entityIri,
                        entityCreator = entityCreator,
                        entityProject = entityProject,
                        entityPermissionLiteral = entityPermissionLiteral,
                        userProfile = userProfile)
  }

  /**
    * Checks whether an integer permission code implies a particular permission property.
    *
    * @param userHasPermissionCode the integer permission code that the user has, or [[None]] if the user has no permissions
    *                              (in which case this method returns `false`).
    * @param userNeedsPermission   the abbreviation of the permission that the user needs.
    * @return `true` if the user has the needed permission.
    */
  def impliesPermissionCodeV1(userHasPermissionCode: Option[Int], userNeedsPermission: String): Boolean = {
    userHasPermissionCode match {
      case Some(permissionCode) => permissionCode >= permissionStringsToPermissionLevels(userNeedsPermission).toInt
      case None                 => false
    }
  }

  /**
    * Determines the permissions that a user has on a `knora-base:Value`, and returns an integer permission code.
    *
    * @param valueIri      the IRI of the `knora-base:Value`.
    * @param valueProps    a [[ValueProps]] containing the permission-relevant predicates and objects
    *                      pertaining to the value, grouped by predicate. The predicates must include
    *                      [[OntologyConstants.KnoraBase.AttachedToUser]], and should include
    *                      [[OntologyConstants.KnoraBase.AttachedToProject]]
    *                      and [[OntologyConstants.KnoraBase.HasPermissions]]. Other predicates may be
    *                      included, but they will be ignored, so there is no need to filter them before passing them to
    *                      this function.
    * @param entityProject if provided, the `knora-base:attachedToProject` of the resource containing the value. Otherwise,
    *                      this predicate must be in `valueProps`.
    * @param userProfile   the profile of the user making the request.
    * @return a code representing the user's permission level on the value.
    */
  def getUserPermissionWithValuePropsV1(valueIri: IRI,
                                        valueProps: ValueProps,
                                        entityProject: Option[IRI],
                                        userProfile: UserProfileV1): Option[Int] = {

    // Either entityProject must be provided, or there must be a knora-base:attachedToProject in valueProps.

    val valuePropsAssertions: Vector[(IRI, IRI)] = filterPermissionRelevantAssertionsFromValueProps(valueProps)
    val valuePropsProject: Option[IRI] =
      valuePropsAssertions.find(_._1 == OntologyConstants.KnoraBase.AttachedToProject).map(_._2)
    val providedProjects = Vector(valuePropsProject, entityProject).flatten.distinct

    if (providedProjects.isEmpty) {
      throw InconsistentRepositoryDataException(s"No knora-base:attachedToProject was provided for entity $valueIri")
    }

    if (providedProjects.size > 1) {
      throw InconsistentRepositoryDataException(
        s"Two different values of knora-base:attachedToProject were provided for entity $valueIri: ${valuePropsProject.get} and ${entityProject.get}")
    }

    val valuePropsAssertionsWithoutProject: Vector[(IRI, IRI)] =
      valuePropsAssertions.filter(_._1 != OntologyConstants.KnoraBase.AttachedToProject)
    val projectAssertion: (IRI, IRI) = (OntologyConstants.KnoraBase.AttachedToProject, providedProjects.head)

    getUserPermissionFromAssertionsV1(
      entityIri = valueIri,
      assertions = valuePropsAssertionsWithoutProject :+ projectAssertion,
      userProfile = userProfile
    )
  }

  /**
    * Determines the permissions that a user has on a entity, and returns an integer permission code.
    *
    * @param entityIri               the IRI of the entity.
    * @param entityCreator           the IRI of the user that created the entity.
    * @param entityProject           the IRI of the entity's project.
    * @param entityPermissionLiteral the literal that is the object of the entity's `knora-base:hasPermissions` predicate.
    * @param userProfile             the profile of the user making the request.
    * @return a code representing the user's permission level for the entity.
    */
  def getUserPermissionV1(entityIri: IRI,
                          entityCreator: IRI,
                          entityProject: IRI,
                          entityPermissionLiteral: String,
                          userProfile: UserProfileV1): Option[Int] = {

    val maybePermissionLevel =
      if (userProfile.isSystemUser || userProfile.permissionData.isSystemAdmin || userProfile.permissionData
            .hasProjectAdminAllPermissionFor(entityProject)) {
        // If the user is the system user, is in the SystemAdmin group or has ProjectAdminAllPermission, just give them the maximum permission.
        Some(MaxPermissionLevel)
      } else {
        val entityPermissions: Map[EntityPermission, Set[IRI]] = parsePermissions(entityPermissionLiteral)

        // Make a list of all the groups (both built-in and custom) that the user belongs to in relation
        // to the entity.
        val userGroups: Set[IRI] = userProfile.userData.user_id match {
          case Some(userIri) =>
            // The user is a known user.
            // If the user is the creator of the entity, put the user in the "creator" built-in group.
            val creatorOption = if (userIri == entityCreator) {
              Some(OntologyConstants.KnoraAdmin.Creator)
            } else {
              None
            }

            val otherGroups = userProfile.permissionData.groupsPerProject.get(entityProject) match {
              case Some(groups) => groups
              case None         => Set.empty[IRI]
            }

            // Make the complete list of the user's groups: KnownUser, the user's built-in (e.g., ProjectAdmin,
            // ProjectMember) and non-built-in groups, possibly creator, and possibly SystemAdmin.
            Set(OntologyConstants.KnoraAdmin.KnownUser) ++ otherGroups ++ creatorOption

          case None =>
            // The user is an unknown user; put them in the UnknownUser built-in group.
            Set(OntologyConstants.KnoraAdmin.UnknownUser)
        }

        // Find the highest permission that can be granted to the user.
        calculateHighestGrantedPermissionLevel(entityPermissions, userGroups) match {
          case Some(highestPermissionCode) => Some(highestPermissionCode)
          case None                        =>
            // If the result is that they would get no permissions, give them user whatever permission an
            // unknown user would have.
            calculateHighestGrantedPermissionLevel(entityPermissions, Set(OntologyConstants.KnoraAdmin.UnknownUser))
        }
      }

    // println(s"getUserPermissionV1: $maybePermissionLevel, $userProfile")

    maybePermissionLevel.map(_.toInt)
  }

}
