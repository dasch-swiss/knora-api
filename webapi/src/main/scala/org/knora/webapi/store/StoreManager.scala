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

package org.knora.webapi.store

import akka.actor._
import akka.event.LoggingReceive
import org.knora.webapi.{ActorMaker, KnoraDispatchers, LiveActorMaker, Settings, UnexpectedMessageException}
import org.knora.webapi.messages.store.redismessages.RedisRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.store.iiif.IIIFManager
import org.knora.webapi.store.redis.RedisManager
import org.knora.webapi.store.triplestore.TriplestoreManager
import org.knora.webapi.util.ActorUtil

import scala.concurrent.ExecutionContext

/**
  * This actor receives messages for different stores, and forwards them to the corresponding store manager.
  * At the moment only triple stores and Sipi are implemented, but in the future, support for different
  * remote repositories will probably be needed. This place would then be the crossroad for these different kinds
  * of 'stores' and their requests.
  */
class StoreManager extends Actor with ActorLogging {
    this: ActorMaker =>

    /**
      * The Knora Akka actor system.
      */
    protected implicit val system: ActorSystem = context.system

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /**
      * The Knora settings.
      */
    protected val settings = Settings(system)

    /**
      * Starts the Triplestore Manager Actor
      */
    protected lazy val triplestoreManager = makeActor(Props(new TriplestoreManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), TriplestoreManagerActorName)

    /**
      * Starts the IIIF Manager Actor
      */
    protected lazy val iiifManager = makeActor(Props(new IIIFManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), IIIFManagerActorName)

    /**
      * Instantiates the Redis Manager
      */
    protected lazy val redisManager: RedisManager = new RedisManager(system)

    def receive = LoggingReceive {
        case tripleStoreMessage: TriplestoreRequest => triplestoreManager forward tripleStoreMessage
        case iiifMessages: IIIFRequest => iiifManager forward iiifMessages
        case redisMessages: RedisRequest => ActorUtil.future2Message(sender(), redisManager receive redisMessages, log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"StoreManager received an unexpected message: $other"))
    }
}
