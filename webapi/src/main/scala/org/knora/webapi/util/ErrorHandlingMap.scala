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

package org.knora.webapi.util

import org.knora.webapi.InconsistentTriplestoreDataException

import scala.collection.{GenTraversableOnce, Iterator, MapLike}

/**
  * A [[Map]] that facilitates error-handling, by wrapping an ordinary [[Map]] and overriding the `default`
  * method to provide custom behaviour (by default, throwing an [[InconsistentTriplestoreDataException]]) if a required
  * value is missing.
  *
  * @param toWrap           the [[Map]] to wrap.
  * @param errorTemplateFun a function that generates an appropriate error message if a required value is missing. The function's
  *                         argument is the key that was not found.
  * @param errorFun         an optional function that is called if a required value is missing. The function's argument is the
  *                         error message generated by `errorTemplateFun`.
  * @tparam A the type of keys in the map.
  * @tparam B the type of values in the map.
  */
class ErrorHandlingMap[A, B](toWrap: Map[A, B],
                             private val errorTemplateFun: A => String,
                             private val errorFun: String => B = { errorMessage: String => throw InconsistentTriplestoreDataException(errorMessage) })
    extends Map[A, B] with MapLike[A, B, ErrorHandlingMap[A, B]] {

    // As an optimization, if the Map we're supposed to wrap is another ErrorHandlingMap, wrap its underlying wrapped Map instead.
    private val wrapped: Map[A, B] = toWrap match {
        case errHandlingMap: ErrorHandlingMap[A, B] => errHandlingMap.wrapped
        case otherMap => otherMap
    }

    override def empty: ErrorHandlingMap[A, B] = {
        new ErrorHandlingMap(Map.empty[A, B], errorTemplateFun, errorFun)
    }

    override def get(key: A): Option[B] = {
        wrapped.get(key)
    }

    override def iterator: Iterator[(A, B)] = {
        wrapped.iterator
    }

    override def foreach[U](f: ((A, B)) => U): Unit = {
        wrapped.foreach(f)
    }

    override def size: Int = {
        wrapped.size
    }

    override def +[B1 >: B](kv: (A, B1)): ErrorHandlingMap[A, B1] = {
        new ErrorHandlingMap(wrapped + kv, errorTemplateFun, errorFun)
    }

    override def -(key: A): ErrorHandlingMap[A, B] = {
        new ErrorHandlingMap(wrapped - key, errorTemplateFun, errorFun)
    }

    override def ++[V1 >: B](xs: GenTraversableOnce[(A, V1)]): ErrorHandlingMap[A, V1] = {
        new ErrorHandlingMap(wrapped ++ xs, errorTemplateFun, errorFun)
    }

    override def --(xs: GenTraversableOnce[A]): ErrorHandlingMap[A, B] = {
        new ErrorHandlingMap(wrapped -- xs, errorTemplateFun, errorFun)
    }

    /**
      * Called when a key is not found.
      *
      * @param key the given key value for which a binding is missing.
      */
    override def default(key: A): B = errorFun(errorTemplateFun(key))
}
