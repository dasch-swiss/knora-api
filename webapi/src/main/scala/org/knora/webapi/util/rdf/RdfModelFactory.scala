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

package org.knora.webapi.util.rdf

import org.knora.webapi.feature.{FeatureFactory, FeatureFactoryConfig}
import org.knora.webapi.util.rdf.jenaimpl.JenaModelFactory
import org.knora.webapi.util.rdf.rdf4jimpl.RDF4JModelFactory

/**
 * A feature factory that creates RDF models.
 */
class RdfModelFactory extends FeatureFactory {
    /**
     * Creates an empty [[RdfModel]].
     *
     * @param featureFactoryConfig the feature factory configuration.
     * @return an empty [[RdfModel]].
     */
    def makeRdfModel(featureFactoryConfig: FeatureFactoryConfig): RdfModel = {
        if (featureFactoryConfig.getToggle("jena-rdf-library").isEnabled) {
            JenaModelFactory.makeEmptyModel
        } else {
            RDF4JModelFactory.makeEmptyModel
        }
    }
}
