"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.12.8"
AKKA_VERSION = "2.5.26"
AKKA_HTTP_VERSION = "10.1.7"
JENA_VERSION = "3.4.0"
METRICS_VERSION = "4.0.1"

# Sipi
SIPI_REPOSITORY = "dhlabbasel/sipi"
SIPI_VERSION = "2.0.1"
SIPI_TAG = "v" + SIPI_VERSION
SIPI_IMAGE = SIPI_REPOSITORY + ":" + SIPI_VERSION

# GraphDB-SE
GDB_SE_REPOSITORY = "daschswiss/graphdb"
GDB_SE_VERSION = "9.0.0"
GDB_SE_TAG = GDB_SE_VERSION + "-se"
GDB_SE_IMAGE = GDB_SE_REPOSITORY + ":" + GDB_SE_TAG

# GraphDB-Free
GDB_FREE_REPOSITORY = "daschswiss/graphdb"
GDB_FREE_VERSION = "9.0.0"
GDB_FREE_TAG = GDB_FREE_VERSION + "-free"
GDB_FREE_IMAGE = GDB_FREE_REPOSITORY + ":" + GDB_FREE_TAG