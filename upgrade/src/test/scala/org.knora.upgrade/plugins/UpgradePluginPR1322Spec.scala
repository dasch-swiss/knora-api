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

package org.knora.upgrade.plugins

import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectResponse, SparqlSelectResponseBody}

class UpgradePluginPR1322Spec extends UpgradePluginSpec {
    "Upgrade plugin PR1322" should {
        "add UUIDs to values" in {
            // Parse the input file.
            val model: Model = trigFileToModel("upgrade/test_data/pr1322.trig")

            // Use the plugin to transform the input.
            val plugin = new UpgradePluginPR1322
            plugin.transform(model)

            // Make an in-memory repository containing the transformed model.
            val repository: SailRepository = makeRepository(model)
            val connection = repository.getConnection

            // Check that UUIDs were added.

            val query: String =
                """
                  |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                  |
                  |SELECT ?value WHERE {
                  |    ?value knora-base:valueHasUUID ?valueHasUUID .
                  |} ORDER BY ?value
                  |""".stripMargin

            val queryResult1: SparqlSelectResponse = doSelect(selectQuery = query, connection = connection)

            val expectedResultBody: SparqlSelectResponseBody = expectedResult(
                Seq(
                    Map(
                        "value" -> "http://rdfh.ch/0001/thing-with-history/values/1c"
                    ),
                    Map(
                        "value" -> "http://rdfh.ch/0001/thing-with-history/values/2c"
                    )
                )
            )

            assert(queryResult1.results == expectedResultBody)

            connection.close()
            repository.shutDown()
        }
    }
}
