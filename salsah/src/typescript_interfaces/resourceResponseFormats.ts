/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import {basicMessageComponents} from "./basicMessageComponents"

export module resourceResponseFormats {
    
    /**
     * Represents a property value (no parallel arrays)
     */
    interface propval {
        /**
         * Textual representation of the value.
         */
        textval:string;

        /**
         * Owner of the value.
         */
        person_id?:string;

        /**
         * Date of last modification of the value.
         */
        lastmod?:string;

        /**
         * IRI of the value.
         */
        id:string;

        /**
         * Comment on the value.
         */
        comment:string;

        /**
         * date of last modification of the value as UTC.
         */
        lastmod_utc?:string;

        /**
         * typed representation of the value.
         */
        value:basicMessageComponents.knoraValue;
    }

    /**
     * Represents a property (no parallel arrays)
     */
    interface prop {
        /**
         * Type of the value as a string
         */
        valuetype:string;

        /**
         * obsolete
         */
        is_annotation:string;

        /**
         * IRI of the value type.
         */
        valuetype_id:string;

        /**
         * Label of the property type
         */
        label:string;

        /**
         * GUI element of the property
         */
        guielement:string;

        /**
         * HTML attributes for the GUI element used to render this property
         */
        attributes:string;

        /**
         * IRI of the property type
         */
        pid:string;

        /**
         * The property's values if given.
         * If an instance of this property type does not exists for the requested resource,
         * only the information about the property type is returned.
         */
        values?:Array<propval>;
    }

    /**
     * Represents a property (parallel arrays)
     */
    interface property {
        /**
         * obsolete
         */
        regular_property:number;

        /**
         * If the property's value is another resource, contains the `rdfs:label` of the OWL class
         * of each resource referred to.
         */
        value_restype?:Array<string>;

        /**
         * Order of property type in GUI
         */
        guiorder:number;

        /**
         * If the property's value is another resource, contains the `rdfs:label` of each resource
         * referred to.
         */
        value_firstprops?:Array<string>;

        /**
         * obsolote
         */
        is_annotation:string;

        /**
         * The type of this property's values
         */
        valuetype_id:string;

        /**
         * The label of thi property type
         */
        label:string;
        /**
         * if the property's value is another resource, contains the icon representing the OWL
         * class of each resource referred to.
         */

        value_iconsrcs?:Array<string>;
        /**
         * the type of GUI element used to render this property.
         */

        guielement:string;
        /**
         * HTML attributes for the GUI element used to render this property
         */

        attributes:string;
        /**
         * The cardinality of this property type for the given resource class
         */

        occurrence:string;
        /**
         * The IRIs of the value objects representing the property's values for this resource
         */

        value_ids?:Array<string>;

        /**
         * The given user's permissions on the value objects.
         */
        value_rights?:Array<number>;

        /**
         * The IRI of the property type
         */
        pid:string;

        /**
         * The property's values
         */
        values?:Array<basicMessageComponents.knoraValue>;

        /**
         * Comments on the property's values
         */
        comments?:Array<string>;

        /**
         * List of binary representations attached to the requested resource (when doing a full resource request)
         */
        locations?: Array<basicMessageComponents.locationItem>;
    }

    /**
     * Represents a permission assertion for the current user
     */
    interface permissionItem {
        /**
         * Permission level
         */
        permission:string;

        /**
         * User group that the permission level is granted to
         */
        granted_to:string;
    }

    /**
     * Represents the regions attached to a resource
     */
    interface region {
        /**
         * A map of property types to property values and res_id and iconsrc
         */
        [index:string]:prop|string;
    }

    /**
     * Represents information about a resource and its class
     */
    interface resinfo {
        /**
         * Digital representations of the resource
         */
        locations:Array<basicMessageComponents.locationItem>;

        /**
         * Label of the resource's class
         */
        restype_label:string;

        /**
         * Indicates if there is a location (digital representation) attached
         */
        resclass_has_location:boolean;

        /**
         * Preview representation of the resource: Thumbnail or Icon
         */
        preview:basicMessageComponents.locationItem;

        /**
         * The owner of the resource
         */
        person_id:string;

        /**
         * Points to the parent resource in case the resource depends on it
         */
        value_of:string|number;

        /**
         * The given user's permissions on the resource
         */
        permissions:Array<permissionItem>;

        /**
         * Date of last modification
         */
        lastmod:string;

        /**
         * The resource class's name
         */
        resclass_name:string;

        /**
         * Regions if there are any
         */
        regions?:Array<region>

        /**
         * Description of the resource type
         */
        restype_description:string;

        /**
         * The project IRI the resource belongs to
         */
        project_id:string;

        /**
         * Full quality representation of the resource
         */
        locdata:basicMessageComponents.locationItem;

        /**
         * The Knora IRI identifying the resource's class
         */
        restype_id:string;

        /**
         * The resource's label
         */
        firstproperty:string;

        /**
         * The URL of an icon for the resource class
         */
        restype_iconsrc:string;

        /**
         * The Knora IRI identifying the resource's class
         */
        restype_name:string;
    }

    /**
     * Represents information about a resource
     */
    interface resdata {
        /**
         * IRI of the resource
         */
        res_id:string;

        /**
         * IRI of the resource's class.
         */
        restype_name:string;

        /**
         * Label of the resource's class
         */
        restype_label:string;

        /**
         * Icon of the resource's class.
         */
        iconsrc:string;

        /**
         * The given user's permissions on the resource
         */
        rights:number;
    }

    /**
     * Represents a resource referring to the requested resource.
     */
    interface incomingItem {
        /**
         * Representation of the referring resource
         */
        ext_res_id:{
            /**
             * The Iri of the referring resource
             */
            id:string;

            /**
             * The IRI of the referring property type
             */
            pid:string;
        };

        /**
         * Resinfo of the referring resource
         */
        resinfo:resinfo;

        /**
         * First property of the referring resource
         */
        value:string;
    }

    /**
     * Represents the context of a resource
     */
    interface context {
        /**
         * Context code: 0 for none, 1 for is partOf (e.g. a page of a book), 2 for isCompound (e.g. a book that has pages)
         */
        context:number;

        /**
         * The Iri of the resource
         */
        canonical_res_id:string;

        /**
         * IRO of the parent resource
         */
        parent_res_id?:string;

        /**
         * Resinfo of the parent resource (if the requested resource is a dependent resource like a page that belongs to a book)
         */
        parent_resinfo?:resinfo;

        /**
         * Resinfo of the requested resource (if requested: resinfo=true)
         */
        resinfo?:resinfo;

        /**
         * Locations of depending resources (e.g. representation of pages of a book)
         */
        locations?:Array<Array<basicMessageComponents.locationItem>>;

        /**
         * Preview locations of depending resources (e.g. representation of pages of a book)
         */
        preview?:Array<basicMessageComponents.locationItem>

        /**
         * First properties of depending resources (e.g. of pages of a book)
         */
        firstprop?:Array<string>;

        /**
         * obsolete
         */
        region?:Array<string>;

        /**
         * obsolete
         */
        resclass_name?:string;

        /**
         * Iris of dependent resources (e.g. pages of a book)
         */
        res_id?:Array<string>;
    }

    /**
     * Represents information about a property type
     */
    interface propertyDefinition {
        /**
         * IRI of the property type
         */
        name:string;

        /**
         * Description of the property type
         */
        description:string;

        /**
         * IRI of the property type's value
         */
        valuetype_id:string;

        /**
         * Label of the property type
         */
        label:string;

        /**
         * IRI of the vocabulary the property type belongs to
         */
        vocabulary:string;

        /**
         * GUI attributes (HTML) of the property type
         */
        attributes:string;

        /**
         * Cardinality of the property type for the requested resource class (not given if property type is requested for a vocabulary)
         */
        occurrence?:string;

        /**
         * IRI of the property type
         */
        id:string;

        /**
         * Name of the GUI element used for the property type
         */
        gui_name:string;

    }

    /**
     * Represents information about the requested resource class
     */
    interface restype {

        /**
         * IRI of the resource class
         */
        name:string;

        /**
         * Description of the resource class
         */
        description:string;

        /**
         * Label of the resource class
         */
        label:string;

        /**
         * Property types that the resource class may have
         */
        properties:Array<propertyDefinition>;

        /**
         * Path to the resource class icon
         */
        iconsrc:string;

    }

    /**
     * Represents a property type attached to a resource class.
     */
    interface propItemForResType {

        id:string;

        label:string;
    }

    /**
     * Represents a resource class
     */
    interface resTypeItem {
        /**
         * IRI of the resource class
         */
        id:string;

        /**
         * Label of the resource class
         */
        label:string;

        /**
         * Property Types that this resource class may have
         */
        properties:Array<propItemForResType>

    }

    /**
     * Represents a vocabulary
     */
    interface vocabularyItem {
        /**
         * The vocabulay's short name
         */
        shortname: string;

        /**
         * Description of the vocbulary
         */
        description: string;

        /**
         * The vocabulay's IRI
         */
        uri: string;

        /**
         * The vocabulay's IRI
         */
        id: string;

        /**
         * The project the vocabulary belongs to
         */
        project_id:string;

        /**
         * The vocabulay's long name
         */
        longname: string;

        /**
         * Indicates if this is the vocabulary the user's project belongs to
         */
        active: Boolean;

    }

    interface resourceLabelSearchItem {

        /**
         * The IRI of the retrieved resource
         */
        id: string;

        /**
         * Values representing the retrieved resource
         */
        value: Array<string>;

        /**
         * The user's permissions on the retrieved resource
         */
        rights: number;

    }

    /**
     * Represents a list of property types for the requested resource class or vocabulary.
     *
     * http://www.knora.org/v1/propertylists?restype=resourceClassIRI
     *
     * http://www.knora.org/v1/propertylists?vocabulary=vocabularyIRI
     */
    export interface propertyTypesInResourceClassResponse extends basicMessageComponents.basicResponse {
        /**
         * Lists the property types the indicated resource class or vocabulary may have.
         */
        properties:Array<propertyDefinition>;

    }

    /**
     * Represents the Knora API V1 response to a resource type request for a vocabulary.
     *
     * http://www.knora.org/v1/resourcetypes?vocabulary=vocabularyIRI
     */
    export interface resourceTypesInVocabularyResponse extends basicMessageComponents.basicResponse {
        /**
         * Lists the resource classes that are defined for the given vocabulary.
         */
        resourcetypes:Array<resTypeItem>;

    }

    /**
     * Represents the Knora API V1 response to a resource type request.
     *
     * http://www.knora.org/v1/resourcetypes/resourceClassIRI
     */
    export interface resourceTypeResponse extends basicMessageComponents.basicResponse {
        /**
         * Represents information about the requested resource class
         */
        restype_info:restype;

    }

    /**
     * Represents the Knora API V1 response to a properties request for a resource.
     * This response just returns a resource's properties.
     *
     * http://www.knora.org/v1/properties/resourceIRI
     */
    export interface resourcePropertiesResponse extends basicMessageComponents.basicResponse {

        /**
         * A map of property type IRIs to property instances
         */
        properties:{
            [index:string]:prop;
        }

    }

    /**
     * Represents the Knora API V1 response to a full resource request.
     *
     * http://www.knora.org/v1/resources/resourceIRI
     */
    export interface resourceFullResponse extends basicMessageComponents.basicResponse {
        /**
         * Description of the resource and its class
         */
        resinfo:resinfo;

        /**
         * Additional information about the requested resource (no parameters)
         */
        resdata:resdata;

        /**
         * The resource's properties
         */
        props:{
            [index:string]:property;
        }

        /**
         * Resources referring to the requested resource
         */
        incoming:Array<incomingItem>

        /**
         * The given user's permissions on the resource
         */
        access:string;

    }

    /**
     * Represents the Knora API V1 response to a resource info request (reqtype=info).
     *
     * http://www.knora.org/v1/resources/resourceIRI?reqtype=info
     */
    export interface resourceInfoResponse extends basicMessageComponents.basicResponse {
        /**
         * The current user's permissions on the resource
         */
        rights:number;

        /**
         * Description of the resource and its class
         */
        resource_info:resinfo;

    }

    /**
     * Represents the Knora API V1 response to a resource rights request (reqtype=rights).
     *
     * http://www.knora.org/v1/resources/resourceIRI?reqtype=rights
     */
    export interface resourceRightsResponse extends basicMessageComponents.basicResponse {
        /**
         * The current user's permissions on the resource
         */
        rights:number;

    }

    /**
     * Represents the Knora API V1 response to a context request (reqtype=context) with or without resinfo (resinfo=true).
     *
     * http://www.knora.org/v1/resources/resourceIRI?reqtype=context[&resinfo=true]
     */
    export interface resourceContextResponse extends basicMessageComponents.basicResponse {
        /**
         * Context of the requested resource
         */
        resource_context:context;

    }

    /**
     * Represents the available vocabularies
     *
     * http://www.knora.org/v1/vocabularies
     */
    export interface vocabularyResponse extends basicMessageComponents.basicResponse {

        vocabularies: Array<vocabularyItem>;

    }

    /**
     * Represents resources that matched the search term in their label.
     * The search can be restricted to resource classes and a limit can be defined for the results to be returned.
     * The amount of values values to be returned for each retrieved resource can also be defined.
     *
     * http://www.knora.org/v1/resources?searchstr=searchValue[&restype_id=resourceClassIRI][&numprops=Integer][&limit=Integer]
     */
    export interface resourceLabelSearchResponse extends basicMessageComponents.basicResponse {

        resources: Array<resourceLabelSearchItem>;

    }

}
