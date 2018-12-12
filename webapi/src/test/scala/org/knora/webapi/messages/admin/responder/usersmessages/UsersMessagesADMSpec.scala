/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.admin.responder.usersmessages

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataType, PermissionsDataADM}
import org.knora.webapi.util.StringFormatter
import org.scalatest.{Matchers, WordSpecLike}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

/**
  * This spec is used to test subclasses of the [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]] class.
  */
class UsersMessagesADMSpec extends WordSpecLike with Matchers {

    private val id = SharedTestDataADM.rootUser.id
    private val username = SharedTestDataADM.rootUser.username
    private val email = SharedTestDataADM.rootUser.email
    private val password = SharedTestDataADM.rootUser.password
    private val token = SharedTestDataADM.rootUser.token
    private val givenName = SharedTestDataADM.rootUser.givenName
    private val familyName = SharedTestDataADM.rootUser.familyName
    private val status = SharedTestDataADM.rootUser.status
    private val lang = SharedTestDataADM.rootUser.lang
    private val groups = SharedTestDataADM.rootUser.groups
    private val projects = SharedTestDataADM.rootUser.projects
    private val sessionId = SharedTestDataADM.rootUser.sessionId
    private val permissions = SharedTestDataADM.rootUser.permissions

    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    "The UserADM case class" should {
        "return a RESTRICTED UserADM when requested " in {
            val rootUser = UserADM(id = id, username = username, email = email, password = password, token = token, givenName = givenName, familyName = familyName, status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = permissions)
            val rootUserRestricted = UserADM(id = id, username = username, email = email, password = None, token = None, givenName = givenName, familyName = familyName, status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = permissions.ofType(PermissionDataType.RESTRICTED))

            assert(rootUser.ofType(UserInformationTypeADM.RESTRICTED) === rootUserRestricted)
        }
        "allow checking the password (1)" in {
            //hashedPassword =  encoder.encode(createRequest.password);
            val encoder = new SCryptPasswordEncoder
            val hp = encoder.encode("123456")
            val up = UserADM(id = "something", username = "something" , email = "something", password = Some(hp), token = None, givenName = "something", familyName = "something", status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = PermissionsDataADM())

            // test SCrypt
            assert(encoder.matches("123456", encoder.encode("123456")))

            // test UserProfileV1 BCrypt usage
            assert(up.passwordMatch("123456"))
        }

        "allow checking the password (2)" in {
            SharedTestDataADM.rootUser.passwordMatch("test") should equal(true)
        }
    }

    "The CreateUserApiRequestADM case class" should {

        "throw 'BadRequestException' if 'username'is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "",
                    email = "ddd@example.com",
                    givenName = "Donald",
                    familyName = "Duck",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'email' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "ddd",
                    email = "",
                    givenName = "Donald",
                    familyName = "Duck",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'password' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "donald.duck",
                    email = "donald.duck@example.com",
                    givenName = "Donald",
                    familyName = "Duck",
                    password = "",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'givenName' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "donald.duck",
                    email = "donald.duck@example.com",
                    givenName = "",
                    familyName = "Duck",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'familyName' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "donald.duck",
                    email = "donald.duck@example.com",
                    givenName = "Donald",
                    familyName = "",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }
    }

    "The UserIdentifierADM case class" should {

        "return the identifier type" in {

            // FIXME: not working!!!
            val iriIdentifier = UserIdentifierADM("http://rdfh.ch/users/root")
            iriIdentifier.hasType should be (UserIdentifierType.IRI)

            val emailIdentifier = UserIdentifierADM("root@example.com")
            emailIdentifier.hasType should be (UserIdentifierType.EMAIL)

            val usernameIdentifier = UserIdentifierADM("root")
            usernameIdentifier.hasType should be (UserIdentifierType.USERNAME)
        }

        "throw a BadRequestException for an empty identifier string" in {

            assertThrows[BadRequestException](
                UserIdentifierADM("")
            )

        }
    }
}
