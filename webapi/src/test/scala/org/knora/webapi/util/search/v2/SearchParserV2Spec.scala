/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util.search.v2

import org.knora.webapi.SparqlSearchException
import org.knora.webapi.util.search._
import org.scalatest.{Matchers, WordSpec}

/**
  * Tests [[SearchParserV2]].
  */
class SearchParserV2Spec extends WordSpec with Matchers {

    import SearchParserV2Spec._

    "The SearchParserV2 object" should {
        "parse a CONSTRUCT query for an extended search using the simplified API" in {
            val parsed: ConstructQuery = SearchParserV2.parseSearchQuery(SimpleApiQuery)
            parsed should ===(ParsedSimpleApiQuery)
            val reparsed = SearchParserV2.parseSearchQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "reject a CONSTRUCT query with a BIND" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleApiQueryWithBind)
            }
        }

        "reject a SELECT query" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SparqlSelect)
            }
        }

        "reject a DESCRIBE query" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SparqlDescribe)
            }
        }

        "reject an INSERT" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SparqlInsert)
            }
        }

        "reject a DELETE" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SparqlDelete)
            }
        }

        "reject an internal ontology IRI" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleApiQueryWithInternalEntityIri)
            }
        }

        "reject left-nested UNIONs" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleApiQueryWithLeftNestedUnion)
            }
        }

        "reject right-nested UNIONs" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleApiQueryStrWithRightNestedUnion)
            }
        }

        "reject a nested OPTIONAL" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleApiQueryStrWithNestedOptional)
            }
        }

        "reject an unsupported FILTER" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleApiQueryWithWrongFilter)
            }
        }

        "parse an extended search query with a FILTER containing a Boolean operator using the simplified API" in {
            val parsed: ConstructQuery = SearchParserV2.parseSearchQuery(SimpleApiQueryForAThingRelatingToAnotherThing)
            parsed should ===(ParsedSimpleApiQueryWithBooleanOperatorInFilter)
            val reparsed = SearchParserV2.parseSearchQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse an extended search query containing a GRAPH clause using the simplified API" in {
            val parsed: ConstructQuery = SearchParserV2.parseSearchQuery(SimpleApiQueryWithGraph)
            parsed should ===(ParsedSimpleApiQueryWithGraph)
            val reparsed = SearchParserV2.parseSearchQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse an extended search query using the value-object API" in {

        }
    }
}

object SearchParserV2Spec {
    val SimpleApiQuery: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:publisher ?bookPublisher .
          |    ?book incunabula:publoc ?bookPubLoc .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher ?bookPublisher .
          |        ?book incunabula:publoc ?bookPubLoc .
          |    }
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |
          |    {
          |        ?page incunabula:pagenum "a7r"^^xsd:string .
          |        ?page incunabula:seqnum "14"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a8r"^^xsd:string .
          |        ?page incunabula:seqnum "16"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a9r"^^xsd:string .
          |        ?page incunabula:seqnum ?seqnum.
          |        FILTER(?seqnum > "17"^^xsd:integer)
          |    }
          |}
        """.stripMargin

    val ParsedSimpleApiQuery = ConstructQuery(
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#book"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "bookLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "book")
            ),
            OptionalPattern(patterns = Vector(
                StatementPattern(
                    namedGraph = None,
                    obj = QueryVariable(variableName = "bookPublisher"),
                    pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#publisher"),
                    subj = QueryVariable(variableName = "book")
                ),
                StatementPattern(
                    namedGraph = None,
                    obj = QueryVariable(variableName = "bookPubLoc"),
                    pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#publoc"),
                    subj = QueryVariable(variableName = "book")
                )
            )),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "pubdate"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pubdate"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#page"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "pageLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "book"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#isPartOf"),
                subj = QueryVariable(variableName = "page")
            ),
            UnionPattern(blocks = Vector(
                Vector(
                    StatementPattern(
                        namedGraph = None,
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string",
                            value = "a7r"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pagenum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    StatementPattern(
                        namedGraph = None,
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer",
                            value = "14"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#seqnum"),
                        subj = QueryVariable(variableName = "page")
                    )
                ),
                Vector(
                    StatementPattern(
                        namedGraph = None,
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string",
                            value = "a8r"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pagenum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    StatementPattern(
                        namedGraph = None,
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer",
                            value = "16"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#seqnum"),
                        subj = QueryVariable(variableName = "page")
                    )
                ),
                Vector(
                    StatementPattern(
                        namedGraph = None,
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string",
                            value = "a9r"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pagenum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    StatementPattern(
                        namedGraph = None,
                        obj = QueryVariable(variableName = "seqnum"),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#seqnum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    FilterPattern(expression = CompareExpression(
                        rightArg = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer",
                            value = "17"
                        ),
                        operator = ">",
                        leftArg = QueryVariable(variableName = "seqnum")
                    ))
                )
            )),
            FilterPattern(expression = CompareExpression(
                rightArg = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#string",
                    value = "GREGORIAN:1500"
                ),
                operator = "<",
                leftArg = QueryVariable(variableName = "pubdate")
            ))
        )),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "bookType"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "bookLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "bookPublisher"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#publisher"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "bookPubLoc"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#publoc"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean",
                    value = "true"
                ),
                pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "pageType"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "pageLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "book"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#isPartOf"),
                subj = QueryVariable(variableName = "page")
            )
        ))
    )

    val ParsedSimpleApiQueryWithBooleanOperatorInFilter = ConstructQuery(
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                obj = IriRef(iri = "http://api.knora.org/ontology/anything/simple/v2#Thing"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                obj = IriRef(iri = "http://data.knora.org/a-thing"),
                pred = QueryVariable(variableName = "linkingProp"),
                subj = QueryVariable(variableName = "resource")
            ),
            FilterPattern(expression = OrExpression(
                rightArg = CompareExpression(
                    rightArg = IriRef(iri = "http://api.knora.org/ontology/anything/simple/v2#hasOtherThing"),
                    operator = "=",
                    leftArg = QueryVariable(variableName = "linkingProp")
                ),
                leftArg = CompareExpression(
                    rightArg = IriRef(iri = "http://api.knora.org/ontology/anything/simple/v2#isPartOfOtherThing"),
                    operator = "=",
                    leftArg = QueryVariable(variableName = "linkingProp")
                )
            ))
        )),
        constructClause = ConstructClause(statements = Vector(StatementPattern(
            obj = IriRef(iri = "http://data.knora.org/a-thing"),
            pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo"),
            subj = QueryVariable(variableName = "resource")
        )))
    )

    val ParsedSimpleApiQueryWithGraph = ConstructQuery(
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://api.knora.org/ontology/anything/simple/v2#Thing"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://data.knora.org/a-thing"),
                pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "value"),
                pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasValue"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = Some(IriRef(iri = "http://www.ontotext.com/explicit")),
                obj = IriRef(iri = "http://data.knora.org/a-thing"),
                pred = QueryVariable(variableName = "linkProp"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = Some(IriRef(iri = "http://www.ontotext.com/explicit")),
                obj = QueryVariable(variableName = "value"),
                pred = QueryVariable(variableName = "valueProp"),
                subj = QueryVariable(variableName = "resource")
            )
        )),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://api.knora.org/ontology/anything/simple/v2#Thing"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://data.knora.org/a-thing"),
                pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(iri = "http://data.knora.org/a-thing"),
                pred = QueryVariable(variableName = "linkProp"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "value"),
                pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasValue"),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "value"),
                pred = QueryVariable(variableName = "valueProp"),
                subj = QueryVariable(variableName = "resource")
            )
        ))
    )

    val SimpleApiQueryWithBind: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://api.knora.org/ontology/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing a ?thingType .
          |    ?thing rdfs:label ?thingLabel .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    BIND(<http://data.knora.org/a-thing> AS ?aThing)
          |    ?thing anything:hasOtherThing ?aThing .
          |}
        """.stripMargin

    val SimpleApiQueryWithWrongFilter: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(BOUND(?pubdate))
          |}
        """.stripMargin

    val SimpleApiQueryWithInternalEntityIri: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://www.knora.org/ontology/incunabula#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SparqlSelect: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |SELECT ?subject ?predicate ?object
          |WHERE {
          |    ?subject a incunabula:book .
          |    ?book rdfs:label ?predicate .
          |    ?book incunabula:pubdate ?object .
          |}
        """.stripMargin

    val SparqlDescribe: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |DESCRIBE ?book
          |WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SparqlInsert: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |INSERT DATA {
          |    <http://example.org/12345> a incunabula:book .
          |}
        """.stripMargin

    val SparqlDelete: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |DELETE {
          |    <http://example.org/12345> a incunabula:book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SimpleApiQueryWithLeftNestedUnion: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |
          |    {
          |        ?page incunabula:pagenum "a6r"^^xsd:string .
          |        ?page incunabula:seqnum "12"^^xsd:integer.
          |
          |        {
          |            ?page incunabula:pagenum "a7r"^^xsd:string .
          |            ?page incunabula:seqnum "14"^^xsd:integer.
          |        } UNION {
          |            ?page incunabula:pagenum "a8r"^^xsd:string .
          |            ?page incunabula:seqnum "16"^^xsd:integer.
          |        }
          |    } UNION {
          |        ?page incunabula:pagenum "a9r"^^xsd:string .
          |        ?page incunabula:seqnum "18"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a10r"^^xsd:string .
          |        ?page incunabula:seqnum "20"^^xsd:integer.
          |    }
          |}
        """.stripMargin

    val SimpleApiQueryStrWithRightNestedUnion: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |
          |    {
          |        ?page incunabula:pagenum "a7r"^^xsd:string .
          |        ?page incunabula:seqnum "14"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a8r"^^xsd:string .
          |        ?page incunabula:seqnum "16"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a9r"^^xsd:string .
          |        ?page incunabula:seqnum "18"^^xsd:integer.
          |
          |        {
          |            ?page incunabula:pagenum "a10r"^^xsd:string .
          |            ?page incunabula:seqnum "20"^^xsd:integer.
          |        } UNION {
          |            ?page incunabula:pagenum "a11r"^^xsd:string .
          |            ?page incunabula:seqnum "22"^^xsd:integer.
          |        }
          |    }
          |}
        """.stripMargin

    val SimpleApiQueryStrWithNestedOptional: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?book incunabula:title ?bookTitle .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |
          |        OPTIONAL {
          |            ?book incunabula:title ?bookTitle .
          |        }
          |    }
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SimpleApiQueryForAThingRelatingToAnotherThing: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://api.knora.org/ontology/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?resource knora-api:hasLinkTo <http://data.knora.org/a-thing> .
          |} WHERE {
          |    ?resource a anything:Thing .
          |
          |    ?resource ?linkingProp <http://data.knora.org/a-thing> .
          |    FILTER(?linkingProp = anything:isPartOfOtherThing || ?linkingProp = anything:hasOtherThing)
          |
          |}
        """.stripMargin

    val SimpleApiQueryWithExplicitTypeAnnotations: String =
        """
          |PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter a knora-api:Resource .
          |    ?letter a beol:letter .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |
          |    <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |
          |    <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
          |
          |} WHERE {
          |    ?letter a knora-api:Resource .
          |    ?letter a beol:letter .
          |
          |    # Scheuchzer, Johann Jacob 1672-1733
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
          |
          |    <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .
          |
          |    # Hermann, Jacob 1678-1733
          |    ?letter ?linkingProp2 <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?linkingProp2 knora-api:objectType knora-api:Resource .
          |
          |    FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
          |
          |    <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
          |}
        """.stripMargin

    val SimpleApiQueryWithGraph: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://api.knora.org/ontology/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?resource a anything:Thing .
          |    ?resource knora-api:hasLinkTo <http://data.knora.org/a-thing> .
          |    ?resource ?linkProp <http://data.knora.org/a-thing> .
          |    ?resource knora-api:hasValue ?value .
          |    ?resource ?valueProp ?value .
          |} WHERE {
          |    ?resource a anything:Thing .
          |    ?resource knora-api:hasLinkTo <http://data.knora.org/a-thing> .
          |    ?resource knora-api:hasValue ?value .
          |
          |    GRAPH <http://www.ontotext.com/explicit> {
          |        ?resource ?linkProp <http://data.knora.org/a-thing> .
          |        ?resource ?valueProp ?value .
          |    }
          |}
        """.stripMargin

}
