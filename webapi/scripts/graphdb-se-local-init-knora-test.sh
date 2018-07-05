#!/usr/bin/env bash

set -e

GRAPHDB="http://localhost:7200"
CONSOLE="openrdf-console/bin/console.sh --force --echo --serverURL $GRAPHDB"

GREEN='\033[0;32m'
RED='\033[0;31m'
NO_COLOUR='\033[0m'
DELIMITER="****************************************************************************************************\n* "

printf "${GREEN}${DELIMITER}Deleting repository${NO_COLOUR}\n\n"

cat graphdb-drop-knora-test-repository.ttl | $CONSOLE

printf "\n${GREEN}${DELIMITER}Creating repository${NO_COLOUR}\n\n"

sed -e 's@PIE_FILE@'"$PWD/KnoraRules.pie"'@' graphdb-se-knora-test-repository-config.ttl.tmpl > graphdb-se-knora-test-repository-config.ttl

curl -X POST -H "Content-Type:text/turtle" -T graphdb-se-knora-test-repository-config.ttl "$GRAPHDB/repositories/SYSTEM/rdf-graphs/service?graph=http://www.knora.org/config-test"

curl -X POST -H "Content-Type:text/turtle" -d "<http://www.knora.org/config-test> a <http://www.openrdf.org/config/repository#RepositoryContext>." $GRAPHDB/repositories/SYSTEM/statements

printf "${GREEN}Repository created.\n\n${DELIMITER}Creating Lucene Index${NO_COLOUR}\n\n"

curl -X POST --data-urlencode 'update@./graphdb-se-knora-index-create.rq' $GRAPHDB/repositories/knora-test/statements

printf "${GREEN}Lucene Index created.\n\n${DELIMITER}Loading Data${NO_COLOUR}\n\n"

./graphdb-knora-test-data.expect $GRAPHDB

printf "${GREEN}Data Loaded.\n\n${DELIMITER}Updating Lucene Index${NO_COLOUR}\n\n"

curl -X POST --data-urlencode 'update@./graphdb-se-knora-index-update.rq' $GRAPHDB/repositories/knora-test/statements

printf "${GREEN}Lucene Index Updated.${NO_COLOUR}"