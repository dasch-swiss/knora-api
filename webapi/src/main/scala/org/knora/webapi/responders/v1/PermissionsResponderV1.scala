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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoResponseV1, GroupInfoType}
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionType.PermissionType
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionForProjectGroupGetResponseV1, AdministrativePermissionV1, PermissionType, _}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetRequest, ProjectInfoResponseV1, ProjectInfoType, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIdUtil, MessageUtil}

import scala.concurrent.duration._
import scala.collection.immutable.Iterable
import scala.concurrent.{Await, Future}


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsResponderRequestV1]].
      * If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case PermissionProfileGetV1(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup) => future2Message(sender(), permissionsProfileGetV1(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup), log)
        case AdministrativePermissionIrisForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), getAdministrativePermissionIrisForProjectV1(projectIri, userProfileV1), log)
        case AdministrativePermissionForIriGetRequestV1(administrativePermissionIri, userProfileV1) => future2Message(sender(), getAdministrativePermissionForIriV1(administrativePermissionIri, userProfileV1), log)
        case AdministrativePermissionForProjectGroupGetV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), administrativePermissionForProjectGroupGetV1(projectIri, groupIri), log)
        case AdministrativePermissionForProjectGroupGetRequestV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), administrativePermissionForProjectGroupGetRequestV1(projectIri, groupIri), log)
        case AdministrativePermissionCreateRequestV1(newAdministrativePermissionV1, userProfileV1) => future2Message(sender(), createAdministrativePermissionV1(newAdministrativePermissionV1, userProfileV1), log)
        //case AdministrativePermissionDeleteRequestV1(administrativePermissionIri, userProfileV1) => future2Message(sender(), deleteAdministrativePermissionV1(administrativePermissionIri, userProfileV1), log)
        case DefaultObjectAccessPermissionIrisForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), getDefaultObjectAccessPermissionIrisForProjectV1(projectIri, userProfileV1), log)
        case DefaultObjectAccessPermissionGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), getDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        //case DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1, userProfileV1) => future2Message(sender(), createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1, userProfileV1), log)
        //case DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        //case TemplatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1) => future2Message(sender(), templatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }


    def permissionsProfileGetV1(projectIris: Seq[IRI], groupIris: Seq[IRI], isInProjectAdminGroups: Seq[IRI], isInSystemAdminGroup: Boolean): Future[PermissionProfileV1] = {

        for {
            a <- Future("")

            projectInfoFutures: Seq[Future[ProjectInfoV1]] = projectIris.map {
                projectIri => (responderManager ? ProjectInfoByIRIGetRequest(projectIri, ProjectInfoType.SHORT, None)).mapTo[ProjectInfoResponseV1] map (_.project_info)
            }

            projectInfos <- Future.sequence(projectInfoFutures)
            //_ = log.debug(s"getPermissionsProfileV1 - projectInfos: ${MessageUtil.toSource(projectInfos)}")

            // find out to which project each group belongs to
            //_ = log.debug("getPermissionsProfileV1 - find out to which project each group belongs to")
            groups: List[(IRI, IRI)] = if (groupIris.nonEmpty) {
                groupIris.map {
                    groupIri => {
                        val resFuture = for {
                            groupInfo <- (responderManager ? GroupInfoByIRIGetRequest(groupIri, GroupInfoType.SAFE, None)).mapTo[GroupInfoResponseV1]
                            res = (groupInfo.group_info.belongsToProject, groupIri)
                        } yield res
                        Await.result(resFuture, 1.seconds)
                    }
                }.toList
            } else {
                List.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"getPermissionsProfileV1 - found: $groups")


            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectMember' group for each project */
            projectMembers: List[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectIri <- projectIris.toList
                    res = (projectIri, OntologyConstants.KnoraBase.ProjectMember)
                } yield res
            } else {
                List.empty[(IRI, IRI)]
            }

            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group for each project */
            projectAdmins: List[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    pAdmin <- isInProjectAdminGroups.toList
                    res = (pAdmin, OntologyConstants.KnoraBase.ProjectAdmin)
                } yield res
            } else {
                List.empty[(IRI, IRI)]
            }

            //ToDo: Maybe we need to add KnownUser group for all other projects
            /* combine explicit groups with materialized implicit groups */
            allGroups = groups ::: projectMembers ::: projectAdmins
            groupsPerProject = allGroups.groupBy(_._1).map { case (k,v) => (k,v.map(_._2))}

            /* retrieve the administrative permissions for each group per project the user is member of */
            administrativePermissionsPerProjectFuture: Future[Map[IRI, Set[PermissionV1]]] = if (projectIris.nonEmpty) {
                getUserAdministrativePermissionsRequestV1(groupsPerProject)
            } else {
                Future(Map.empty[IRI, Set[PermissionV1]])
            }
            administrativePermissionsPerProject <- administrativePermissionsPerProjectFuture

            /* retrieve the default object access permissions for each group per project the user is member of */
            defaultObjectAccessPermissionsPerProjectFuture: Future[Map[IRI, Set[PermissionV1]]] = if (projectIris.nonEmpty) {
                getUserDefaultObjectAccessPermissionsRequestV1(groupsPerProject)
            } else {
                Future(Map.empty[IRI, Set[PermissionV1]])
            }
            defaultObjectAccessPermissionsPerProject <- defaultObjectAccessPermissionsPerProjectFuture

            /* construct the permission profile from the different parts */
            result = PermissionProfileV1(
                projectInfos = projectInfos,
                groupsPerProject = groupsPerProject,
                isInSystemAdminGroup = isInSystemAdminGroup,
                administrativePermissionsPerProject = administrativePermissionsPerProject,
                defaultObjectAccessPermissionsPerProject = defaultObjectAccessPermissionsPerProject
            )
            //_ = log.debug(s"getPermissionsProfileV1 - result: $result")

        } yield result



    }

    /**
      * Gets all IRI's of all administrative permissions defined inside a project.
      *
      * @param forProject    the IRI of the project.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[AdministrativePermissionV1]] objects.
      */
    private def getAdministrativePermissionIrisForProjectV1(forProject: IRI, userProfileV1: UserProfileV1): Future[Seq[IRI]] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectAdministrativePermissions(
                triplestore = settings.triplestoreType,
                projectIri = forProject
            ).toString())
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            apIris: List[String] = permissionsQueryResponseRows.map(_.rowMap("s")).toList
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - iris: $apIris")

            administrativePermissionIris = if (apIris.nonEmpty) {
                apIris
            } else {
                Vector.empty[IRI]
            }

        } yield administrativePermissionIris
    }

    /**
      * Gets a single administrative permission identified by it's IRI.
      *
      * @param administrativePermissionIRI the IRI of the administrative permission.
      * @return a single [[AdministrativePermissionV1]] object.
      */
    private def getAdministrativePermissionForIriV1(administrativePermissionIRI: IRI, userProfileV1: UserProfileV1): Future[Option[AdministrativePermissionV1]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermission(
                triplestore = settings.triplestoreType,
                administrativePermissionIri = administrativePermissionIRI
            ).toString())
            //_ = log.debug(s"getAdministrativePermissionForIriV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionForIriV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"getAdministrativePermissionForIriV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)

            //_ = log.debug(s"getAdministrativePermissionForIriV1 - hasPermissions: ${MessageUtil.toSource(hasPermissions)}")

            administrativePermission = if (groupedPermissionsQueryResponse.nonEmpty) {
                Some(AdministrativePermissionV1(
                    forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no project attached")).head,
                    forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no group attached")).head,
                    hasPermissions = hasPermissions
                ))
            } else {
                None
            }

        } yield administrativePermission
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIRI the project.
      * @param groupIRI   the group.
      * @return an option containing an [[AdministrativePermissionV1]]
      */
    private def administrativePermissionForProjectGroupGetV1(projectIRI: IRI, groupIRI: IRI): Future[Option[AdministrativePermissionV1]] = {
        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (projectIRI.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (groupIRI.isEmpty) throw BadRequestException("Group cannot be empty")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getAPForProjectAndGroup(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI,
                groupIri = groupIRI
            ).toString())
            _ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            _ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            administrativePermission: Option[AdministrativePermissionV1] = if (permissionQueryResponseRows.nonEmpty) {
                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
                Some(
                    AdministrativePermissionV1(
                        forProject = projectIRI,
                        forGroup = groupIRI,
                        hasPermissions = hasPermissions
                    )
                )
            } else {
                None
            }
            //_ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - administrativePermission: $administrativePermission")
        } yield administrativePermission
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIRI the project.
      * @param groupIRI   the group.
      * @return an [[AdministrativePermissionForProjectGroupGetResponseV1]]
      */
    private def administrativePermissionForProjectGroupGetRequestV1(projectIRI: IRI, groupIRI: IRI): Future[AdministrativePermissionForProjectGroupGetResponseV1] = {
        for {
            ap <- administrativePermissionForProjectGroupGetV1(projectIRI, groupIRI)
            result = ap match {
                case Some(ap) => AdministrativePermissionForProjectGroupGetResponseV1(ap)
                case None => throw NotFoundException(s"No Administrative Permission found for project: $projectIRI, group: $groupIRI combination")
            }
        } yield result
    }

    /**
      *
      * @param newAdministrativePermissionV1
      * @param userProfileV1
      * @return
      */
    private def createAdministrativePermissionV1(newAdministrativePermissionV1: NewAdministrativePermissionV1, userProfileV1: UserProfileV1): Future[AdministrativePermissionCreateResponseV1] = {
        log.debug("createAdministrativePermissionV1")
        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (newAdministrativePermissionV1.forProject.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (newAdministrativePermissionV1.forGroup.isEmpty) throw BadRequestException("Group cannot be empty")
            _ = if (newAdministrativePermissionV1.hasPermissions.isEmpty) throw BadRequestException("Permissions cannot be empty")

            checkResult <- administrativePermissionForProjectGroupGetV1(newAdministrativePermissionV1.forProject, newAdministrativePermissionV1.forGroup)

            _ = checkResult match {
                case Some(ap) => throw DuplicateValueException(s"Permission for project: '${newAdministrativePermissionV1.forProject}' and group: '${newAdministrativePermissionV1.forGroup}' combination already exists.")
                case None =>
            }

            response = AdministrativePermissionCreateResponseV1(administrativePermission = AdministrativePermissionV1(forProject = "mock-project", forGroup = "mock-group", hasPermissions = Seq.empty[PermissionV1]))

        } yield response


    }

/*
    private def deleteAdministrativePermissionV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionOperationResponseV1] = ???
*/
    /**
      * Gets all IRI's of all default object access permissions defined inside a project.
      *
      * @param forProject    the IRI of the project.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[DefaultObjectAccessPermissionV1]] objects.
      */
    private def getDefaultObjectAccessPermissionIrisForProjectV1(forProject: IRI, userProfileV1: UserProfileV1): Future[Seq[IRI]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectDefaultObjectAccessPermissions(
                triplestore = settings.triplestoreType,
                projectIri = forProject
            ).toString())
            //_ = log.debug(s"getProjectDefaultObjectAccessPermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectDefaultObjectAccessPermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            doapIris: List[String] = permissionsQueryResponseRows.map(_.rowMap("s")).toList
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - iris: $apIris")

            defaultObjectAccessPermissionIris = if (doapIris.nonEmpty) {
                doapIris
            } else {
                Vector.empty[IRI]
            }

        } yield defaultObjectAccessPermissionIris

    }

    /**
      * Gets a single default object access permission identified by it's IRI.
      *
      * @param defaultObjectAccessPermissionIri the IRI of the default object access permission.
      * @return a single [[DefaultObjectAccessPermissionV1]] object.
      */
    private def getDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionV1] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermission(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = defaultObjectAccessPermissionIri
            ).toString())
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.DOAP)

            // TODO: Handle IRI not found, i.e. should return Option
            defaultObjectAccessPermission = DefaultObjectAccessPermissionV1(
                forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no project")).head,
                forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no group")).head,
                forResourceClass = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForResourceClass, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no resource class")).head,
                forProperty = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProperty, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no property")).head,
                hasPermissions = hasPermissions
            )

        } yield defaultObjectAccessPermission
    }
/*

    private def createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1: NewDefaultObjectAccessPermissionV1, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionOperationResponseV1] = ???

    private def deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionOperationResponseV1] = ???
*/

    /*
    private def templatePermissionsCreateRequestV1(projectIri: IRI, permissionsTemplate: PermissionsTemplate, userProfileV1: UserProfileV1): Future[TemplatePermissionsCreateResponseV1] = {
        for {
        /* find and delete all administrative permissions */
            administrativePermissionsIris <- getAdministrativePermissionIrisForProjectV1(projectIri, userProfileV1)
            _ = administrativePermissionsIris.foreach { iri =>
                deleteAdministrativePermissionV1(iri, userProfileV1)
            }

            /* find and delete all default object access permissions */
            defaultObjectAccessPermissionIris <- getDefaultObjectAccessPermissionIrisForProjectV1(projectIri, userProfileV1)
            _ = defaultObjectAccessPermissionIris.foreach { iri =>
                deleteDefaultObjectAccessPermissionV1(iri, userProfileV1)
            }

            _ = if (permissionsTemplate == PermissionsTemplate.OPEN) {
                /* create administrative permissions */
                /* is the user a SystemAdmin then skip adding project admin permissions*/
                if (!userProfileV1.isSystemAdmin) {

                }

                /* create default object access permissions */
            }

            templatePermissionsCreateResponse = TemplatePermissionsCreateResponseV1(
                success = true,
                administrativePermissions = Vector.empty[AdministrativePermissionV1],
                defaultObjectAccessPermissions = Vector.empty[DefaultObjectAccessPermissionV1],
                msg = "OK"
            )

        } yield templatePermissionsCreateResponse

    }
    */

    /**
      * By providing the all the projects and groups in which the user is a member of, calculate the user's max
      * administrative permissions of each project.
      *
      * @param groupsPerProject the groups in each project the user is member of.
      * @return a the user's max permissions for each project.
      */
    def getUserAdministrativePermissionsRequestV1(groupsPerProject: Map[IRI, List[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {

        //FixMe: loop through each project the user is part of and retrieve the administrative permissions attached to each group he is in, calculate max permissions, package everything in a neat little object, and return it back


        val pp: Iterable[Future[(IRI, Seq[PermissionV1])]] = for {
            (projectIri, groups) <- groupsPerProject
            groupIri <- groups
            //_ = log.debug(s"getUserAdministrativePermissionsRequestV1 - projectIri: $projectIri, groupIri: $groupIri")
            projectPermission: Future[(IRI, Seq[PermissionV1])] = administrativePermissionForProjectGroupGetV1(projectIri, groupIri).map {
                case Some(ap: AdministrativePermissionV1) => (projectIri, ap.hasPermissions)
                case None => (projectIri, Seq.empty[PermissionV1])
            }

        } yield projectPermission

        val allPermissionsFuture: Future[Iterable[(IRI, Seq[PermissionV1])]] = Future.sequence(pp)

        val result: Future[Map[IRI, Set[PermissionV1]]] = for {
            allPermission <- allPermissionsFuture
            result = allPermission.groupBy(_._1).map { case (k, v) =>

                /* Combine permission sequences */
                val combined = v.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                    acc ++ seq._2
                }
                /* Squash the resulting permission sequence */
                val squashed: Set[PermissionV1] = squashPermissions(combined)
                (k, squashed)
            }
            _ = log.debug(s"getUserAdministrativePermissionsRequestV1 - result: $result")
        } yield result
        result
    }

    def getUserDefaultObjectAccessPermissionsRequestV1(groupsPerProject: Map[IRI, List[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {

        //FixMe: loop through each project the user is part of and retrieve the default object access permissions attached to each group he is in, calculate max permissions, package everything in a neat little object, and return it back
        val result = Map.empty[IRI, Set[PermissionV1]]
        log.debug(s"getUserDefaultObjectAccessPermissionsRequestV1 - result: $result")
        Future(result)
    }


    ///////////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////////

    /**
      * -> Need this for reading permission literals.
      *
      * Parses the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param maybePermissionListStr the literal to parse.
      * @return a [[Map]] in which the keys are permission abbreviations in
      *         [[OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *         user group IRIs.
      */
    def parsePermissions(maybePermissionListStr: Option[String], permissionType: PermissionType): Seq[PermissionV1] = {
        maybePermissionListStr match {
            case Some(permissionListStr) => {
                val permissions: Seq[String] = permissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter)
                //log.debug(s"parsePermissions - split permissions: $permissions")
                permissions.map {
                    permission =>
                        val splitPermission = permission.split(' ')
                        val abbreviation = splitPermission(0)

                        permissionType match {
                            case PermissionType.AP => {
                                if (!OntologyConstants.KnoraBase.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                if (splitPermission.length > 1) {
                                    val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                                    val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))
                                    buildPermissionObject(abbreviation, Some(groups))
                                } else {
                                    buildPermissionObject(abbreviation, None)
                                }
                            }
                            case PermissionType.DOAP => {
                                if (!OntologyConstants.KnoraBase.DefaultObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                                val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))
                                buildPermissionObject(abbreviation, Some(groups))
                            }
                        }
                }
            }
            case None => Seq.empty[PermissionV1]
        }
    }

    /**
      * Helper method used to convert the permission string stored inside the triplestore to a permission object.
      *
      * @param name the name of the permission.
      * @param iris the optional additional information.
      * @return a permission object.
      */
    def buildPermissionObject(name: String, iris: Option[Set[IRI]]): PermissionV1 = {
        name match {
            case OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission => PermissionV1.ProjectResourceCreateAllPermission
            case OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission => PermissionV1.ProjectResourceCreateRestrictedPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.ProjectAdminAllPermission => PermissionV1.ProjectAdminAllPermission
            case OntologyConstants.KnoraBase.ProjectAdminGroupAllPermission => PermissionV1.ProjectAdminGroupAllPermission
            case OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission => PermissionV1.ProjectAdminGroupRestrictedPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.ProjectAdminRightsAllPermission => PermissionV1.ProjectAdminRightsAllPermission
            case OntologyConstants.KnoraBase.ProjectAdminOntologyAllPermission => PermissionV1.ProjectAdminOntologyAllPermission
            case OntologyConstants.KnoraBase.DefaultChangeRightsPermission => PermissionV1.DefaultChangeRightsPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultDeletePermission => PermissionV1.DefaultDeletePermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultModifyPermission => PermissionV1.DefaultModifyPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultViewPermission => PermissionV1.DefaultViewPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultRestrictedViewPermission => PermissionV1.DefaultRestrictedViewPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
        }

    }

    /**
      * Helper method used to squash a sequence of [[PermissionV1]] objects, by combining the duplicates where
      * applicable, before they are removed.
      *
      * @param permissions the sequence of permissions.
      * @return a squashed sequence containing only unique permission.
      */
    def squashPermissions(permissions: Seq[PermissionV1]): Set[PermissionV1] = {

        val result = permissions.groupBy(_.name).map { case (k, v) =>
            k match {
                case OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission => {
                    val combinedRestrictions: Set[IRI] = v.foldLeft(Set.empty[IRI]) { (acc, perm) =>
                        acc ++ perm.restrictions
                    }
                    PermissionV1.ProjectResourceCreateRestrictedPermission(combinedRestrictions)
                }
                case OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission => {
                    val combinedRestrictions: Set[IRI] = v.foldLeft(Set.empty[IRI]) { (acc, perm) =>
                        acc ++ perm.restrictions
                    }
                    PermissionV1.ProjectAdminGroupRestrictedPermission(combinedRestrictions)
                }
                case rest => v.head
            }
        }.toSet
        //log.debug(s"squashPermissions - result: $result")
        result
    }
}
