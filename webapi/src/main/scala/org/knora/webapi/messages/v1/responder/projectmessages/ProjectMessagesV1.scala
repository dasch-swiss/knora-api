/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.messages.v1.responder.projectmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoType.ProjectInfoType
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1, UserV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.{BadRequestException, IRI, InconsistentTriplestoreDataException}
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new project.
  *
  * @param shortName           the shortname of the project to be created (unique).
  * @param longName            the longname of the project to be created.
  * @param basePath            the basepath of the project to be created.
  * @param isActiveProject     the status of the project to be created.
  * @param hasSelfJoinEnabled  the status of self-join of the project to be created.
  */
case class CreateProjectApiRequestV1(shortName: String,
                                     longName: String,
                                     basePath: String,
                                     isActiveProject: Boolean,
                                     hasSelfJoinEnabled: Boolean
                                    ) extends ProjectV1JsonProtocol {
    def toJsValue = createProjectApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update one property of an existing project.
  *
  * @param propertyIri  the property of the project to be updated.
  * @param newValue     the new value for the property of the project to be updated.
  */
case class UpdateProjectApiRequestV1(propertyIri: String,
                                     newValue: String) extends ProjectV1JsonProtocol {
    def toJsValue = updateProjectApiRequestV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[org.knora.webapi.responders.v1.ProjectsResponderV1]].
  */
sealed trait ProjectsResponderRequestV1 extends KnoraRequestV1

// Requests
/**
  * Get all information about all projects.
  *
  * @param infoType is the type of the project information: full or short.
  * @param userProfile the profile of the user making the request.
  */
case class ProjectsGetRequestV1(infoType: ProjectInfoType, userProfile: Option[UserProfileV1]) extends ProjectsResponderRequestV1


/**
  * Get all the existing named graphs from all projects as a vector of [[org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class ProjectsNamedGraphGetV1(userProfile: UserProfileV1) extends ProjectsResponderRequestV1

/**
  * Get everything about a single project identified through it's IRI.
  *
  * @param iri Iri of the project.
  * @param infoType is the type of the project information: full or short.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectInfoByIRIGetRequestV1(iri: IRI, infoType: ProjectInfoType, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1


/**
  * Find everything about a single project identified through it's shortname.
  *
  * @param shortname of the project.
  * @param infoType is the type of the project information.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectInfoByShortnameGetRequestV1(shortname: String, infoType: ProjectInfoType, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Requests the cration of a new project.
  *
  * @param newProjectDataV1 the [[NewProjectDataV1]] information for creation a new project.
  * @param userProfileV1 the user profile of the user creating the new project.
  */
case class ProjectCreateRequestV1(newProjectDataV1: NewProjectDataV1,
                                  userProfileV1: UserProfileV1) extends ProjectsResponderRequestV1

/**
  * Requests updating an existing project
  *
  * @param projectIri the IRI of the project to be updated.
  * @param propertyIri the IRI of the property to be updated.
  * @param newValue the new value for the property.
  * @param userProfileV1 the user profile of the user requesting the update.
  */
case class ProjectUpdateRequestV1(projectIri: IRI,
                                  propertyIri: IRI,
                                  newValue: Any,
                                  userProfileV1: UserProfileV1) extends ProjectsResponderRequestV1

// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all projects.
  *
  * @param projects information about all existing projects.
  * @param userdata information about the user that made the request.
  */
case class ProjectsResponseV1(projects: Seq[ProjectInfoV1], userdata: Option[UserDataV1]) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue = projectsResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single project.
  *
  * @param project_info all information about the project.
  * @param userdata     information about the user that made the request.
  */
case class ProjectInfoResponseV1(project_info: ProjectInfoV1, userdata: Option[UserDataV1]) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue = projectInfoResponseV1Format.write(this)
}

/**
  * Represents an answer to a project creating/modifying operation.
  * @param project_info the new project info of the created/modified project.
  * @param userData     information about the user that made the request.
  */
case class ProjectOperationResponseV1(project_info: ProjectInfoV1, userData: UserDataV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue = projectOperationResponseV1Format.write(this)
}
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

case class ProjectInfoV1(id: IRI,
                         shortname: String,
                         longname: String,
                         description: String,
                         keywords: Option[String] = None,
                         logo: Option[String] = None,
                         belongsToInstitution: Option[IRI] = None,
                         basepath: String,
                         projectOntologyGraph: IRI,
                         projectDataGraph: IRI,
                         isActiveProject: Boolean,
                         hasSelfJoinEnabled: Boolean
                        ) {

    def ofType(projectInfoType: ProjectInfoType): ProjectInfoV1 = {

        projectInfoType match {
            case ProjectInfoType.FULL => {
                ProjectInfoV1(
                    id = id,
                    shortname = shortname,
                    longname = longname,
                    description = description,
                    keywords = keywords,
                    logo = logo,
                    basepath = basepath,
                    belongsToInstitution = belongsToInstitution,
                    projectOntologyGraph = projectOntologyGraph,
                    projectDataGraph = projectDataGraph,
                    isActiveProject = isActiveProject,
                    hasSelfJoinEnabled = hasSelfJoinEnabled
                )
            }
            case ProjectInfoType.SHORT => {
                ProjectInfoV1(
                    id = id,
                    shortname = shortname,
                    longname = longname,
                    description = description,
                    keywords = None, // removed
                    belongsToInstitution = None, //removed
                    logo = None, //removed
                    basepath = basepath,
                    projectOntologyGraph = projectOntologyGraph,
                    projectDataGraph = projectDataGraph,
                    isActiveProject = isActiveProject,
                    hasSelfJoinEnabled = hasSelfJoinEnabled
                )
            }
            case _ => throw BadRequestException(s"The requested projectInfoType: $projectInfoType is invalid.")
        }
    }
}

object ProjectInfoType extends Enumeration {

    type ProjectInfoType = Value

    val SHORT = Value(0, "short")
    val FULL = Value(1, "full")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Project info type not supported: $name")
        }
    }
}


case class NewProjectDataV1(shortname: String,
                            longname: String,
                            description: String,
                            keywords: String,
                            logo: String,
                            basepath: String,
                            isActiveProject: Boolean,
                            hasSelfJoinEnabled: Boolean
                           )



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
  */
trait ProjectV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    import UserV1JsonProtocol.userDataV1Format

    implicit val projectInfoV1Format: JsonFormat[ProjectInfoV1] = jsonFormat12(ProjectInfoV1)
    // we have to use lazyFormat here because `UserV1JsonProtocol` contains an import statement for this object.
    // this results in recursive import statements
    // rootFormat makes it return the expected type again.
    // https://github.com/spray/spray-json#jsonformats-for-recursive-types
    implicit val projectsResponseV1Format: RootJsonFormat[ProjectsResponseV1] = rootFormat(lazyFormat(jsonFormat2(ProjectsResponseV1)))
    implicit val projectInfoResponseV1Format: RootJsonFormat[ProjectInfoResponseV1] = rootFormat(lazyFormat(jsonFormat2(ProjectInfoResponseV1)))
    implicit val createProjectApiRequestV1Format: RootJsonFormat[CreateProjectApiRequestV1] = rootFormat(lazyFormat(jsonFormat5(CreateProjectApiRequestV1)))
    implicit val updateProjectApiRequestV1Format: RootJsonFormat[UpdateProjectApiRequestV1] = rootFormat(lazyFormat(jsonFormat2(UpdateProjectApiRequestV1)))
    implicit val projectOperationResponseV1Format: RootJsonFormat[ProjectOperationResponseV1] = rootFormat(lazyFormat(jsonFormat2(ProjectOperationResponseV1)))
}