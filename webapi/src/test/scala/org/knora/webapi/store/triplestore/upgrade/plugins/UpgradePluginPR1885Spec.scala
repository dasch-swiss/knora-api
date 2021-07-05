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

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.exceptions.{AssertionException, BadRequestException}
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.messages.util.rdf._

import java.io.{BufferedInputStream, BufferedOutputStream, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}

class UpgradePluginPR1885Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(defaultFeatureFactoryConfig)
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  private def checkResourceUUID(model: RdfModel, subj: IriNode, expectedUUID: DatatypeLiteral): Unit = {
    val pred = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ResourceHasUUID)
    model
      .find(
        subj = Some(subj),
        pred = Some(pred),
        obj = None
      )
      .toSet
      .headOption match {
      case Some(statement: Statement) =>
        statement.obj match {
          case datatypeLiteral: DatatypeLiteral =>
            assert(datatypeLiteral.datatype == OntologyConstants.Xsd.String)
            assert(datatypeLiteral.value == expectedUUID.value)
          case other =>
            throw AssertionException(s"Unexpected object for $pred: $other")
        }

      case None => throw AssertionException(s"No statement found with predicate $pred")
    }
  }

  "Upgrade plugin PR1885" should {
    // Parse the input file.
    val model: RdfModel = trigFileToModel("test_data/upgrade/pr1885.trig")

//    val fileInputStream = new BufferedInputStream(new FileInputStream("test_data/all_data/incunabula-data.ttl"))
//    val model: RdfModel = rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
//    fileInputStream.close()

    // Use the plugin to transform the input.
    val plugin = new UpgradePluginPR1885(defaultFeatureFactoryConfig, logger)
    plugin.transform(model)

    "add UUID to resources with IRI that has UUID" in {

      // Check that the UUID is added.
      val subj = nodeFactory.makeIriNode("http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg")
      val expectedUUID: DatatypeLiteral = nodeFactory.makeStringLiteral("Lz7WEqJETJqqsUZQYexBQg")
      checkResourceUUID(model, subj, expectedUUID)
    }

    "change resource IRI of `thing with picture` and add resourceUUID" in {

      val creationDateIri = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)
      val obj = nodeFactory.makeDatatypeLiteral("2021-05-11T10:00:00Z", OntologyConstants.Xsd.DateTime)

      model
        .find(
          subj = None,
          pred = Some(creationDateIri),
          obj = Some(obj)
        )
        .toSet
        .headOption match {
        case Some(statement: Statement) =>
          statement.subj match {
            case iriNode: IriNode =>
              assert(iriNode.iri.startsWith("http://rdfh.ch/resources/"))
              val ending = iriNode.iri.split("/").last
              stringFormatter.validateBase64EncodedUuid(ending,
                                                        throw BadRequestException(s"${ending} is not a validUUID"))
              // Check that resource UUID is added to the statement with new resource IRI
              val subj = iriNode
              val expectedUUID: DatatypeLiteral = nodeFactory.makeStringLiteral(ending)
              checkResourceUUID(model, subj, expectedUUID)
            case other =>
              throw AssertionException(s"Unexpected object for $creationDateIri: $other")
          }

        case None => throw AssertionException(s"No statement found with predicate $creationDateIri")
      }
    }

    "change resource IRI of `g5r of incunabula project` that already has a UUID" in {

      val creationDateIri = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)
      val obj = nodeFactory.makeDatatypeLiteral("2016-03-02T15:05:48Z", OntologyConstants.Xsd.DateTime)

      model
        .find(
          subj = None,
          pred = Some(creationDateIri),
          obj = Some(obj)
        )
        .toSet
        .headOption match {
        case Some(statement: Statement) =>
          statement.subj match {
            case iriNode: IriNode =>
              assert(iriNode.iri.startsWith("http://rdfh.ch/resources/"))
              val ending = iriNode.iri.split("/").last
              assert(ending == "bLvsDqJcO8FBUWi3tMdKMQ")
              // Check that resource UUID is added to the statement with new resource IRI
              val subj = iriNode
              val expectedUUID: DatatypeLiteral = nodeFactory.makeStringLiteral(ending)
              checkResourceUUID(model, subj, expectedUUID)

              //Check that any resource IRI given as obj are also reformatted
              val partOf = nodeFactory.makeIriNode("http://www.knora.org/ontology/0803/incunabula#partOf")
              val partOfStatements: Set[Statement] = model
                .find(
                  subj = Some(iriNode),
                  pred = Some(partOf),
                  obj = None
                )
                .toSet
              assert(partOfStatements.size == 1)
              partOfStatements.head.obj match {
                case iriNode: IriNode =>
                  assert(iriNode.iri.startsWith("http://rdfh.ch/resources/"))
                case otherObj => throw AssertionException(s"Unexpected object for $partOf: $otherObj")
              }

            case other =>
              throw AssertionException(s"Unexpected object for $creationDateIri: $other")
          }

        case None => throw AssertionException(s"No statement found with predicate $creationDateIri")
      }
    }

  }
}