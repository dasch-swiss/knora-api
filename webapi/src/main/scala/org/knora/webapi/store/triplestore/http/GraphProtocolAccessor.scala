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

package org.knora.webapi.store.triplestore.http

import java.io.File

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.Authorization
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import org.knora.webapi.SettingsConstants._
import org.knora.webapi.{Settings, TriplestoreResponseException, TriplestoreUnsupportedFeatureException}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * The GraphProtocolAccessor object is a basic implementation of the
  * SPARQL 1.1 Graph Store HTTP Protocol: http://www.w3.org/TR/sparql11-http-rdf-update
  */
object GraphProtocolAccessor {

    val HTTP_PUT_METHOD = "PUT"
    val HTTP_POST_METHOD = "POST"

    /**
      * Use the HTTP PUT method to send the data. Put is defined as SILENT DELETE of the graph and an INSERT.
      *
      * @param graphName the name of the graph.
      * @param filepath  a path to the file containing turtle.
      * @return String
      */
    def put(graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: ActorMaterializer): String = {
        this.execute(HTTP_PUT_METHOD, graphName, filepath)
    }

    /**
      * Use the HTTP PUT method to send the data. Put is defined as SILENT DELETE of the graph and an INSERT.
      *
      * @param graphName the name of the graph.
      * @param filepath  path to the file containing turtle.
      * @return String
      */
    def put_string_payload(graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: ActorMaterializer): String = {
        this.execute(HTTP_PUT_METHOD, graphName, filepath)
    }

    /**
      * Use the HTTP POST method to send the data. Post is defined as an INSERT.
      *
      * @param graphName the name of the graph.
      * @param filepath  a path to the file containing turtle.
      * @return String
      */
    def post(graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: ActorMaterializer): String = {
        this.execute(HTTP_POST_METHOD, graphName, filepath)
    }

    private def execute(method: String, graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: ActorMaterializer): String = {
        val file = new File(filepath)
        require(file.exists())

        val log = akka.event.Logging(_system, this.getClass)
        val settings = Settings(_system)
        implicit val executionContext = _system.dispatcher
        val tsType = settings.triplestoreType
        val triplestoreBaseUrl = s"${settings.triplestoreHost}:${settings.triplestorePort}"
        val authorization = Authorization.basic(settings.triplestoreUsername, settings.triplestorePassword)
        val http = Http(_system)

        log.debug("==>> GraphProtocolAccessor START")

        /* HTTP paths for the SPARQL 1.1 Graph Store HTTP Protocol */
        val requestPath = tsType match {
            case HTTP_GRAPH_DB_TS_TYPE | HTTP_GRAPH_DB_FREE_TS_TYPE => s"$triplestoreBaseUrl/repositories/${settings.triplestoreDatabaseName}/rdf-graphs/service"
            case HTTP_SESAME_TS_TYPE => s"$triplestoreBaseUrl/openrdf-sesame/repositories/${settings.triplestoreDatabaseName}/rdf-graphs/service"
            case HTTP_FUSEKI_TS_TYPE if !settings.fusekiTomcat => s"$triplestoreBaseUrl/${settings.triplestoreDatabaseName}/data"
            case HTTP_FUSEKI_TS_TYPE if settings.fusekiTomcat => s"$triplestoreBaseUrl/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/data"
            case ts_type => throw TriplestoreUnsupportedFeatureException(s"GraphProtocolAccessor does not support: $ts_type")
        }

        /* set the right method */
        val requestMethod = if (method == HTTP_PUT_METHOD) {
            HttpMethods.PUT
        } else if (method == HTTP_POST_METHOD) {
            HttpMethods.POST
        } else {
            throw TriplestoreUnsupportedFeatureException("Only PUT or POST supported by the GraphProtocolAccessor")
        }

        if (graphName.toLowerCase == "default") {
            throw TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported")
        }

        val formData = Multipart.FormData(
            Source.single(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity(ContentType(MediaType.text("turtle"), HttpCharsets.`UTF-8`), file.length(), FileIO.fromPath(file.toPath, chunkSize = 100000)),
                    Map("graph" -> graphName)
                )
            )
        )

        val responseFuture = for {
            requestEntity <- Marshal(formData).to[RequestEntity]

            request = HttpRequest(
                method = requestMethod,
                uri = requestPath,
                entity = requestEntity,
                headers = List(authorization)
            )

            response <- http.singleRequest(request)
            responseStatusCode: Int = response.status.intValue / 100
            responseString <- response.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))

            responseCodeCategory = responseStatusCode / 100
            _ = if (!(responseCodeCategory == 2 || responseCodeCategory == 3)) {
                throw TriplestoreResponseException(s"Unable to load file $filepath; triplestore responded with HTTP code ${response.status}: $responseString")
            }
            responseMessage = responseStatusCode.toString
        } yield responseMessage

        responseFuture.recover {
            case tre: TriplestoreResponseException => throw tre
            case e: Exception => throw TriplestoreResponseException("GraphProtocolAccessor Communication Exception", e, log)
        }

        val result = Await.result(responseFuture, 300.seconds)
        log.debug(s"==>> Received result: $result")

        log.debug("==>> GraphProtocolAccessor END")

        result
    }

}
