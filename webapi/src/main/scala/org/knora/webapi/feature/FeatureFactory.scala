/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.feature

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.RequestContext
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.settings.KnoraSettingsImpl

/**
 * An abstract class for module-specific factories that produce implementations of features.
 *
 * @param featureFactoryConfig the configuration of this feature factory.
 */
abstract class FeatureFactory(protected val featureFactoryConfig: FeatureFactoryConfig) {
    /**
     * A convenience method that calls `featureFactoryConfig.featureIsOn`.
     *
     * @param featureName the name of a feature.
     * @return `true` if the feature is turned on.
     */
    protected def featureIsOn(featureName: String): Boolean = featureFactoryConfig.featureIsOn(featureName)
}

/**
 * An abstract class representing configuration for a [[FeatureFactory]] from a particular
 * configuration source.
 *
 * @param maybeParent if this [[FeatureFactoryConfig]] has no setting for a particular
 *                    feature toggle, it delegates to its parent.
 */
abstract class FeatureFactoryConfig(protected val maybeParent: Option[FeatureFactoryConfig]) {
    /**
     * Each concrete implementation of this class implements this method, which reads
     * configuration from a particular source.
     *
     * @param featureName the name of a feature.
     * @return the setting for that feature, as read from this [[FeatureFactoryConfig]]'s configuration
     *         source, or `None` if the source contains no setting for that feature.
     */
    protected def getFeatureToggle(featureName: String): Option[Boolean]

    /**
     * Determines whether a feature is turned on. First check the configuration
     * loaded by this [[FeatureFactoryConfig]]. If a setting for the feature is found,
     * it is returned. Otherwise, this method delegates to the parent [[FeatureFactoryConfig]].
     *
     * @param featureName the name of the feature.
     * @return `true` if the feature is turned on, `false` otherwise.
     */
    def featureIsOn(featureName: String): Boolean = {
        // Do we have a setting for this feature?
        getFeatureToggle(featureName) match {
            case Some(setting) =>
                // Yes. Return it.
                setting

            case None =>
                // No. Do we have a parent FeatureFactoryConfig?
                maybeParent match {
                    case Some(parent) =>
                        // Yes. Delegate to the parent.
                        parent.featureIsOn(featureName)

                    case None =>
                        // No. Default to false.
                        false
                }
        }
    }
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from the application's configuration file.
 *
 * @param knoraSettings a [[KnoraSettingsImpl]] representing the configuration in the application's
 *                      configuration file.
 * @param maybeParent the parent [[FeatureFactoryConfig]].
 */
class KnoraSettingsFeatureFactoryConfig(private val knoraSettings: KnoraSettingsImpl,
                                        maybeParent: Option[FeatureFactoryConfig]) extends FeatureFactoryConfig(maybeParent) {
    protected def getFeatureToggle(featureName: String): Option[Boolean] = {
        knoraSettings.getFeatureToggle(featureName)
    }
}

object RequestContextFeatureFactoryConfig {
    /**
     * The name of the HTTP header containing feature toggles.
     */
    val FEATURE_TOGGLE_HEADER: String = "x-knora-feature-toggle"
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from a header in an HTTP request.
 *
 * @param requestContext the HTTP request context.
 * @param maybeParent the parent [[FeatureFactoryConfig]].
 */
class RequestContextFeatureFactoryConfig(private val requestContext: RequestContext,
                                         maybeParent: Option[FeatureFactoryConfig]) extends FeatureFactoryConfig(maybeParent) {
    import RequestContextFeatureFactoryConfig._

    // Read feature toggles from an HTTP header.
    private val featureToggles: Map[String, Boolean] = {
        // Was the feature toggle header submitted?
        requestContext.request.headers.find(_.lowercaseName == FEATURE_TOGGLE_HEADER) match {
            case Some(featureToggleHeader: HttpHeader) =>
                // Yes. Parse it into comma-separated key-value pairs.
                featureToggleHeader.value.split(',').map {
                    headerValueItem: String =>
                        headerValueItem.split('=').map(_.trim) match {
                            case Array(featureName: String, featureToggle: String)
                                if featureToggle == "true" || featureToggle == "false" =>
                                featureName -> featureToggle.toBoolean

                            case _ =>
                                throw BadRequestException(s"Invalid value for header $FEATURE_TOGGLE_HEADER: ${featureToggleHeader.value}")
                        }
                }.toMap

            case None => Map.empty
        }
    }

    protected def getFeatureToggle(featureName: String): Option[Boolean] = {
        featureToggles.get(featureName)
    }
}
