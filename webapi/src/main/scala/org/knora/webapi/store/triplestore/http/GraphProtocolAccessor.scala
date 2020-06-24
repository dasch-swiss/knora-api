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

package org.knora.webapi.store.triplestore.http

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.scalalogging.Logger
import org.knora.webapi._
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext}


/**
  * The GraphProtocolAccessor object is a basic implementation of the
  * SPARQL 1.1 Graph Store HTTP Protocol: http://www.w3.org/TR/sparql11-http-rdf-update
  */
object GraphProtocolAccessor {

    val HTTP_PUT_METHOD = "PUT"
    val HTTP_POST_METHOD = "POST"

    val log = Logger(LoggerFactory.getLogger(this.getClass))

    /**
      * Use the HTTP PUT method to send the data. Put is defined as SILENT DELETE of the graph and an INSERT.
      *
      * @param graphName the name of the graph.
      * @param filepath  a path to the file containing turtle.
      * @return String
      */
    def put(graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: Materializer): StatusCode = {
        this.execute(HTTP_PUT_METHOD, graphName, filepath)
    }

    /**
      * Use the HTTP PUT method to send the data. Put is defined as SILENT DELETE of the graph and an INSERT.
      *
      * @param graphName the name of the graph.
      * @param filepath  path to the file containing turtle.
      * @return String
      */
    def put_string_payload(graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: Materializer): StatusCode = {
        this.execute(HTTP_PUT_METHOD, graphName, filepath)
    }

    /**
      * Use the HTTP POST method to send the data. Post is defined as an INSERT.
      *
      * @param graphName the name of the graph.
      * @param filepath  a path to the file containing turtle.
      * @return String
      */
    def post(graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: Materializer): StatusCode = {
        this.execute(HTTP_POST_METHOD, graphName, filepath)
    }

    private def execute(method: String, graphName: String, filepath: String)(implicit _system: ActorSystem, materializer: Materializer): StatusCode = {

        val log = akka.event.Logging(_system, this.getClass)
        val settings = KnoraSettings(_system)
        implicit val executionContext: ExecutionContext = _system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

        log.debug("GraphProtocolAccessor - execute started")

        val file = new File(filepath)

        if (!file.exists) {
            throw BadRequestException(s"File ${file.getAbsolutePath} does not exist")
        }

        if (graphName.toLowerCase == "default") {
            throw TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported")
        }

        // Use HTTP basic authentication.
        val authorization = headers.Authorization(BasicHttpCredentials(settings.triplestoreUsername, settings.triplestorePassword))

        // HTTP paths for the SPARQL 1.1 Graph Store HTTP Protocol
        val requestPath = settings.triplestoreType match {
            case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => s"/repositories/${settings.triplestoreDatabaseName}/rdf-graphs/service"
            case TriplestoreTypes.HttpFuseki if !settings.fusekiTomcat => s"/${settings.triplestoreDatabaseName}/data"
            case TriplestoreTypes.HttpFuseki if settings.fusekiTomcat => s"/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/data"
            case ts_type => throw TriplestoreUnsupportedFeatureException(s"GraphProtocolAccessor does not support: $ts_type")
        }

        // Construct a URI.
        val uri = Uri(
            scheme = "http",
            authority = Uri.Authority(Uri.Host(settings.triplestoreHost), port = settings.triplestorePort),
            path = Uri.Path(requestPath)
        ).withQuery(Query("graph" -> graphName))

        // Choose a request method.
        val requestMethod = if (method == HTTP_PUT_METHOD) {
            HttpMethods.PUT
        } else if (method == HTTP_POST_METHOD) {
            HttpMethods.POST
        } else {
            throw TriplestoreUnsupportedFeatureException("Only PUT or POST supported by the GraphProtocolAccessor")
        }

        // Stream the file data into the HTTP request.
        val fileEntity = HttpEntity.fromPath(ContentType(MediaType.text("turtle"), HttpCharsets.`UTF-8`), file.toPath, chunkSize = 100000)

        val request = HttpRequest(
            method = requestMethod,
            uri = uri,
            entity = fileEntity,
            headers = List(authorization)
        )

        val responseFuture = for {
            // Send the HTTP request.
            response <- Http().singleRequest(request)

            // Convert the HTTP response body to a string.
            responseString <- Unmarshal(response.entity).to[String]

            _ = if (!response.status.isSuccess) {
                throw TriplestoreResponseException(s"Unable to load file $filepath; triplestore responded with HTTP code ${response.status}: $responseString")
            }
        } yield response.status

        responseFuture.recover {
            case tre: TriplestoreResponseException => throw tre
            case e: Exception => throw TriplestoreResponseException("GraphProtocolAccessor Communication Exception", e, log)
        }

        log.debug("==>> GraphProtocolAccessor END")

        Await.result(responseFuture, settings.defaultTimeout)
    }

}
