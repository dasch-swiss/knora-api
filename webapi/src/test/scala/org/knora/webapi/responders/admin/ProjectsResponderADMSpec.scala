/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.util.{MutableTestIri, StringFormatter}

import scala.concurrent.duration._


object ProjectsResponderADMSpec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[ProjectsResponderADM]] actor.
  */
class ProjectsResponderADMSpec extends CoreSpec(ProjectsResponderADMSpec.config) with ImplicitSender {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val timeout = 5.seconds

    private val rootUser= SharedTestDataADM.rootUser

    "The ProjectsResponderADM" when {

        "used to query for project information" should {

            "return information for every project" in {

                responderManager ! ProjectsGetRequestADM(rootUser)
                val received = expectMsgType[ProjectsGetResponseADM](timeout)

                assert(received.projects.contains(SharedTestDataADM.imagesProject))
                assert(received.projects.contains(SharedTestDataADM.incunabulaProject))
            }

            "return information about a project identified by IRI" in {

                /* Incunabula project */
                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some(SharedTestDataADM.incunabulaProject.id)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.incunabulaProject))

                /* Images project */
                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some(SharedTestDataADM.imagesProject.id)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.imagesProject))

                /* 'SystemProject' */
                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some(SharedTestDataADM.systemProject.id)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.systemProject))

            }

            "return information about a project identified by shortname" in {
                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeShortname = Some(SharedTestDataADM.incunabulaProject.shortname)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(ProjectGetResponseADM(SharedTestDataADM.incunabulaProject))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {

                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some("http://rdfh.ch/projects/notexisting")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found")))

            }

            "return 'NotFoundException' when the project shortname is unknown " in {
                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeShortname = Some("wrongshortname")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found")))
            }

            "return 'NotFoundException' when the project shortcode is unknown " in {
                responderManager ! ProjectGetRequestADM(
                    ProjectIdentifierADM(maybeShortcode = Some("9999")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project '9999' not found")))
            }
        }

        "used to query project's restricted view settings" should {

            val expectedResult = ProjectRestrictedViewSettingsADM(size = Some("!512,512"), watermark = Some("path_to_image"))

            "return restricted view settings using project IRI" in {
                responderManager ! ProjectRestrictedViewSettingsGetADM(
                    identifier = ProjectIdentifierADM(maybeIri = Some(SharedTestDataADM.imagesProject.id)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Some(expectedResult))
            }

            "return restricted view settings using project SHORTNAME" in {
                responderManager ! ProjectRestrictedViewSettingsGetADM(
                    identifier = ProjectIdentifierADM(maybeShortname = Some(SharedTestDataADM.imagesProject.shortname)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Some(expectedResult))
            }

            "return restricted view settings using project SHORTCODE" in {
                responderManager ! ProjectRestrictedViewSettingsGetADM(
                    identifier = ProjectIdentifierADM(maybeShortcode = Some(SharedTestDataADM.imagesProject.shortcode)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Some(expectedResult))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {
                responderManager ! ProjectRestrictedViewSettingsGetRequestADM(
                    identifier = ProjectIdentifierADM(maybeIri = Some("http://rdfh.ch/projects/notexisting")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFoundException' when the project SHORTCODE is unknown" in {
                responderManager ! ProjectRestrictedViewSettingsGetRequestADM(
                    identifier = ProjectIdentifierADM(maybeShortcode = Some("9999")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project '9999' not found.")))
            }

            "return 'NotFoundException' when the project SHORTNAME is unknown" in {
                responderManager ! ProjectRestrictedViewSettingsGetRequestADM(
                    identifier = ProjectIdentifierADM(maybeShortname = Some("wrongshortname")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
            }

        }

        "used to modify project information" should {

            val newProjectIri = new MutableTestIri

            "CREATE a project and return the project info if the supplied shortname is unique" in {
                responderManager ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject",
                        shortcode = "111c", // lower case
                        longname = Some("project longname"),
                        description = Seq(StringLiteralWithLanguageV2(value = "project description", language = "en")),
                        keywords = Seq("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

                received.project.shortname should be("newproject")
                received.project.shortcode should be("111C") // upper case
                received.project.longname should contain("project longname")
                received.project.description should be(Seq(StringLiteralV2(value = "project description", language = Some("en"))))

                newProjectIri.set(received.project.id)
                //println(s"newProjectIri: ${newProjectIri.get}")
            }

            "CREATE a project and return the project info if the supplied shortname and shortcode is unique" in {
                responderManager ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject2",
                        shortcode = "1112",
                        longname = Some("project longname"),
                        description = Seq(StringLiteralWithLanguageV2(value = "project description", language = "en")),
                        keywords = Seq("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)

                received.project.shortname should be("newproject2")
                received.project.shortcode should be("1112")
                received.project.longname should contain("project longname")
                received.project.description should be(Seq(StringLiteralV2(value = "project description", language = Some("en"))))

                //println(s"newProjectIri: ${newProjectIri.get}")
            }

            "return a 'DuplicateValueException' during creation if the supplied project shortname is not unique" in {
                responderManager ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject",
                        shortcode = "111C",
                        longname = Some("project longname"),
                        description = Seq(StringLiteralWithLanguageV2(value = "project description", language = "en")),
                        keywords = Seq("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortname: 'newproject' already exists")))
            }

            "return a 'DuplicateValueException' during creation if the supplied project shortname is unique but the shortcode is not" in {
                responderManager ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject3",
                        shortcode = "111C",
                        longname = Some("project longname"),
                        description = Seq(StringLiteralWithLanguageV2(value = "project description", language = "en")),
                        keywords = Seq("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortcode: '111C' already exists")))
            }

            "return 'BadRequestException' if project 'shortname' during creation is missing" in {

                responderManager ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "",
                        shortcode = "1114",
                        longname = Some("project longname"),
                        description = Seq(StringLiteralWithLanguageV2(value = "project description", language = "en")),
                        keywords = Seq("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("'Shortname' cannot be empty")))
            }

            "return 'BadRequestException' if project 'shortcode' during creation is missing" in {

                responderManager ! ProjectCreateRequestADM(
                    CreateProjectApiRequestADM(
                        shortname = "newproject4",
                        shortcode = "",
                        longname = Some("project longname"),
                        description = Seq(StringLiteralWithLanguageV2(value = "project description", language = "en")),
                        keywords = Seq("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("The supplied short code: '' is not valid.")))
            }

            "UPDATE a project" in {
                responderManager ! ProjectChangeRequestADM(
                    projectIri = newProjectIri.get,
                    changeProjectRequest = ChangeProjectApiRequestADM(
                        shortname = None,
                        longname = Some("updated project longname"),
                        description = Some(Seq(StringLiteralWithLanguageV2(value = """updated project description with "quotes" and <html tags>""", language = "en"))),
                        keywords = Some(Seq("updated", "keywords")),
                        logo = Some("/fu/bar/baz-updated.jpg"),
                        status = Some(false),
                        selfjoin = Some(true)
                    ),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseADM = expectMsgType[ProjectOperationResponseADM](timeout)
                received.project.shortname should be("newproject")
                received.project.shortcode should be("111C")
                received.project.longname should be (Some("updated project longname"))
                received.project.description should be (Seq(StringLiteralWithLanguageV2(value = """updated project description with "quotes" and <html tags>""", language = "en")))
                received.project.keywords.sorted should be (Seq("updated", "keywords").sorted)
                received.project.logo should be (Some("/fu/bar/baz-updated.jpg"))
                received.project.status should be (false)
                received.project.selfjoin should be (true)
            }

            "return 'NotFound' if a not existing project IRI is submitted during update" in {
                responderManager ! ProjectChangeRequestADM(
                    projectIri = "http://rdfh.ch/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestADM(longname = Some("new long name")),
                    SharedTestDataADM.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found. Aborting update request.")))
            }

            "return 'BadRequest' if nothing would be changed during the update" in {

                an [BadRequestException] should be thrownBy ChangeProjectApiRequestADM(None, None, None, None, None, None, None)

                /*
                actorUnderTest ! ProjectChangeRequestADM(
                    projectIri = "http://rdfh.ch/projects/notexisting",
                    changeProjectRequest = ChangeProjectApiRequestADM(None, None, None, None, None, None, None, None, None, None),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("No data would be changed. Aborting update request.")))
                */
            }
        }

        /*
        "used to query named graphs" should {
            "return all named graphs" in {
                actorUnderTest ! ProjectsNamedGraphGetADM(SharedTestDataADM.rootUser)

                val received: Seq[NamedGraphADM] = expectMsgType[Seq[NamedGraphADM]]
                received.size should be (7)
            }

            "return all named graphs after adding a new ontology" in {
                actorUnderTest ! ProjectOntologyAddADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    ontologyIri = "http://wwww.knora.org/ontology/00FF/blabla1",
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received01: ProjectADM = expectMsgType[ProjectADM](timeout)
                received01.ontologies.size should be (2)

                actorUnderTest ! ProjectsNamedGraphGetADM(SharedTestDataADM.rootUser)

                val received02: Seq[NamedGraphADM] = expectMsgType[Seq[NamedGraphADM]]
                received02.size should be (8)
            }
        }
        */

        "used to query members" should {

            "return all members of a project identified by IRI" in {
                responderManager ! ProjectMembersGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some(SharedTestDataADM.imagesProject.id)),
                    SharedTestDataADM.rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
                val members = received.members

                members.size should be (4)

                members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.RESTRICTED),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.RESTRICTED)
                ).map(_.id)
            }

            "return all members of a project identified by shortname" in {
                responderManager ! ProjectMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortname = Some(SharedTestDataADM.imagesProject.shortname)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
                val members = received.members

                members.size should be (4)

                members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.SHORT)
                ).map(_.id)
            }

            "return all members of a project identified by shortcode" in {
                responderManager ! ProjectMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortcode = Some(SharedTestDataADM.imagesProject.shortcode)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)
                val members = received.members

                members.size should be (4)

                members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesUser02.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.SHORT)
                ).map(_.id)
            }

            "return 'NotFound' when the project IRI is unknown (project membership)" in {
                responderManager ! ProjectMembersGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some("http://rdfh.ch/projects/notexisting")),
                    SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project membership)" in {
                responderManager ! ProjectMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortname = Some("wrongshortname")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
            }

            "return 'NotFound' when the project shortcode is unknown (project membership)" in {
                responderManager ! ProjectMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortcode = Some("9999")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project '9999' not found.")))
            }

            "return all project admin members of a project identified by IRI" in {
                responderManager ! ProjectAdminMembersGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some(SharedTestDataADM.imagesProject.id)),
                    SharedTestDataADM.rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
                val members = received.members

                members.size should be (2)

                members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT)
                ).map(_.id)
            }

            "return all project admin members of a project identified by shortname" in {
                responderManager ! ProjectAdminMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortname = Some(SharedTestDataADM.imagesProject.shortname)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
                val members = received.members

                members.size should be (2)

                members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT)
                ).map(_.id)
            }

            "return all project admin members of a project identified by shortcode" in {
                responderManager ! ProjectAdminMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortcode = Some(SharedTestDataADM.imagesProject.shortcode)),
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)
                val members = received.members

                members.size should be (2)

                members.map(_.id) should contain allElementsOf Seq(
                    SharedTestDataADM.imagesUser01.ofType(UserInformationTypeADM.SHORT),
                    SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.SHORT)
                ).map(_.id)
            }

            "return 'NotFound' when the project IRI is unknown (project admin membership)" in {
                responderManager ! ProjectAdminMembersGetRequestADM(
                    ProjectIdentifierADM(maybeIri = Some("http://rdfh.ch/projects/notexisting")),
                    SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project admin membership)" in {
                responderManager ! ProjectAdminMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortname = Some("wrongshortname")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project 'wrongshortname' not found.")))
            }

            "return 'NotFound' when the project shortcode is unknown (project admin membership)" in {
                responderManager ! ProjectAdminMembersGetRequestADM(
                    ProjectIdentifierADM(maybeShortcode = Some("9999")),
                    requestingUser = SharedTestDataADM.rootUser
                )
                expectMsg(Failure(NotFoundException(s"Project '9999' not found.")))
            }
        }

        "used to query keywords" should {

            "return all unique keywords for all projects" in {
                responderManager ! ProjectsKeywordsGetRequestADM(SharedTestDataADM.rootUser)
                val received: ProjectsKeywordsGetResponseADM = expectMsgType[ProjectsKeywordsGetResponseADM](timeout)
                received.keywords.size should be (18)
            }

            "return all keywords for a single project" in {
                responderManager ! ProjectKeywordsGetRequestADM(
                    projectIri = SharedTestDataADM.incunabulaProject.id,
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectKeywordsGetResponseADM = expectMsgType[ProjectKeywordsGetResponseADM](timeout)
                received.keywords should be (SharedTestDataADM.incunabulaProject.keywords)
            }

            "return empty list for a project without keywords" in {
                responderManager ! ProjectKeywordsGetRequestADM(
                    projectIri = SharedTestDataADM.anythingProject.id,
                    requestingUser = SharedTestDataADM.rootUser
                )
                val received: ProjectKeywordsGetResponseADM = expectMsgType[ProjectKeywordsGetResponseADM](timeout)
                received.keywords should be (Seq.empty[String])
            }

            "return 'NotFound' when the project IRI is unknown" in {
                responderManager ! ProjectKeywordsGetRequestADM(
                    projectIri = "http://rdfh.ch/projects/notexisting",
                    SharedTestDataADM.rootUser
                )

                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }
        }
    }

}
