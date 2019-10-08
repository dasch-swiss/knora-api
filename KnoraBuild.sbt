
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerRepository}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.knora.Dependencies
import sbt.Keys.version

import scala.sys.process.Process

//////////////////////////////////////
// GLOBAL SETTINGS
//////////////////////////////////////

// custom docker-compose plugin settings
lazy val GDB = config("gdb") extend Default

lazy val GDBSIPI = config("gdbsipi") extend Default

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(docs, salsah1, webapi, knoraGraphDbSe, knoraGraphdbFree, knoraSipi, knoraAssets)

lazy val buildSettings = Dependencies.Versions ++ Seq(
    organization := "org.knora",
    version := (ThisBuild / version).value
)

lazy val root = Project(id = "knora", file("."))
        .aggregate(aggregatedProjects: _*)
        .enablePlugins(DockerComposePlugin, GitVersioning, GitBranchPrompt)
        .settings(Dependencies.Versions)
        .settings(
            // values set for all sub-projects
            // These are normal sbt settings to configure for release, skip if already defined

            ThisBuild / licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
            ThisBuild / homepage := Some(url("https://github.com/dhlab-basel/Knora")),
            ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/dhlab-basel/Knora"), "scm:git:git@github.com:dhlab-basel/Knora.git")),

            // use 'git describe' for deriving the version
            git.useGitDescribe := true,

            // override generated version string because docker hub rejects '+' in tags
            ThisBuild / version ~= (_.replace('+', '-')),

            // use Ctrl-c to stop current task but not quit SBT
            Global / cancelable := true,

            publish / skip := true,

            Dependencies.sysProps := sys.props.toString(),
            Dependencies.sysEnvs := sys.env.toString(),

            // these can be set by the user as system environment variables
            ThisBuild / Dependencies.gdbHomePath := sys.env.getOrElse("KNORA_GDB_HOME", sys.props("user.dir") + "/triplestores/graphdb/home"),
            ThisBuild / Dependencies.gdbLicensePath := sys.env.getOrElse("KNORA_GDB_LICENSE", sys.props("user.dir") + "/triplestores/graphdb/graphdb.license"),

            // these are calculated in their respective targets
            Dependencies.knoraSipiImage := "dhlabbasel/knora-sipi:" + version.value,
            Dependencies.knoraGdbImage := {
                if (Dependencies.gdbTypeString.equals("graphdb-free")) {
                    "dhlabbasel/knora-graphdb-free:" + version.value
                } else {
                    "dhlabbasel/knora-graphdb-se:" + version.value
                }
            },
            Dependencies.knoraWebapiImage := "dhlabbasel/webapi:" + version.value,
            Dependencies.knoraSalsah1Image := "dhlabbasel/salsah1:" + version.value,

            // use these values for variable substitution in the docker-compose.yml
            variablesForSubstitution := Map(
                "KNORA_GDB_HOME" -> Dependencies.gdbHomePath.value,
                "KNORA_GDB_LICENSE" -> Dependencies.gdbLicensePath.value,
                "KNORA_GDB_TYPE" -> Dependencies.gdbTypeString,
                "KNORA_GDB_IMAGE" -> Dependencies.knoraGdbImage.value,
                "KNORA_SIPI_IMAGE" ->  Dependencies.knoraSipiImage.value,
                "KNORA_WEBAPI_IMAGE" -> Dependencies.knoraWebapiImage.value,
                "KNORA_SALSAH1_IMAGE" -> Dependencies.knoraSalsah1Image.value
            ),

            dockerImageCreationTask := Seq(
                (salsah1 / Docker / publishLocal).value,
                (webapi / Docker / publishLocal).value,
                (knoraGraphDbSe / Docker / publishLocal).value,
                (knoraGraphdbFree / Docker / publishLocal).value,
                (knoraSipi / Docker / publishLocal).value,
                (knoraAssets / Docker / publishLocal).value
            )
        )


//////////////////////////////////////
// DOCS (./docs)
//////////////////////////////////////

// Define `Configuration` instances representing our different documentation trees
lazy val ParadoxSite = config("paradox")

lazy val docs = knoraModule("docs")
        .enablePlugins(JekyllPlugin, ParadoxPlugin, ParadoxSitePlugin, ParadoxMaterialThemePlugin, GhpagesPlugin)
        .configs(
            ParadoxSite
        )
        .settings(
            // Apply default settings to our two custom configuration instances
            ParadoxSitePlugin.paradoxSettings(ParadoxSite),
            ParadoxMaterialThemePlugin.paradoxMaterialThemeGlobalSettings, // paradoxTheme and version
            ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(ParadoxSite)
        )
        .settings(

            // Ghpages settings
            ghpagesNoJekyll := true,
            git.remoteRepo := "git@github.com:dhlab-basel/Knora.git",
            ghpagesCleanSite / excludeFilter :=
                    new FileFilter {
                        def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
                    } || "LICENSE.md" || "README.md",

            // (sbt-site) Customize the source directory
            // sourceDirectory in Jekyll := sourceDirectory.value / "overview",
            ParadoxSite / sourceDirectory := sourceDirectory.value / "paradox",

            // (sbt-site) Customize the output directory (subdirectory of site)
            ParadoxSite / siteSubdirName := "paradox",

            // Set some paradox properties
            ParadoxSite / paradoxProperties ++= Map(
                "project.name" -> "Knora Documentation",
                "github.base_url" -> "https://github.com/dhlab-basel/Knora",
                "image.base_url" -> ".../assets/images",
                "extref.rfc.base_url" -> "http://tools.ietf.org/html/rfc%s",
                "snip.src.base_dir" -> ((baseDirectory in ThisBuild).value / "webapi" / "src" / "main" / "scala").getAbsolutePath,
                "snip.test.base_dir" -> ((baseDirectory in ThisBuild).value / "webapi" / "src" / "test" / "scala").getAbsolutePath
            ),

            // Paradox Material Theme Settings
            ParadoxSite / paradoxMaterialTheme ~= {
                _.withColor("blue", "yellow")
                        .withRepository(uri("https://github.com/dhlab-basel/Knora/docs"))
                        .withFavicon("cloud")
                        .withLogoIcon("cloud")
                        .withSocial(
                            uri("https://github.com/dhlab-basel"),
                            uri("https://twitter.com/dhlabbasel")
                        )
                        .withLanguage(java.util.Locale.ENGLISH)
                        .withCopyright("Copyright 2015-2019 the contributors (see Contributors.md)")
            },
            makeSite / mappings ++= Seq(
                file("docs/src/api-admin/index.html") -> "api-admin/index.html",
                file("docs/src/api-admin/swagger.json") -> "api-admin/swagger.json"
            ),
            makeSite := makeSite.dependsOn(buildPrequisites).value
        )

lazy val buildPrequisites = taskKey[Unit]("Build typescript API documentation and Graphviz diagrams.")

docs / buildPrequisites := {
    val s: TaskStreams = streams.value

    val execDir: Option[File] = if (sys.props("user.dir").endsWith("docs")) {
        // running from docs directory, which is fine
        None
    } else {
        // running from project root directory
        Some(new File(sys.props("user.dir") + "/docs"))
    }

    val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
    val clean: Seq[String] = shell :+ "make clean"
    val jsonformat: Seq[String] = shell :+ "make jsonformat"
    val graphvizfigures: Seq[String] = shell :+ "make graphvizfigures"
    val jsonformattest: Seq[String] = shell :+ "make jsonformattest"

    s.log.info("building typescript documentation and graphviz diagrams...")

    if ((Process(clean, execDir) #&& Process(jsonformattest, execDir) #&& Process(jsonformat, execDir) #&& Process(graphvizfigures, execDir) !) == 0) {
        Thread.sleep(500)
        s.log.success("typescript documentation and graphviz diagrams built successfully")
    } else {
        throw new IllegalStateException("typescript documentation and graphviz diagrams failed to build")
    }
}


//////////////////////////////////////
// Knora's custom GraphDB-SE
//////////////////////////////////////

lazy val graphdbseCommonSettings = Seq(
    name := "knora-graphdb-se"
)

lazy val knoraGraphDbSe = knoraModule("knora-graphdb-se")
  .enablePlugins(DockerPlugin)
  .settings(
      graphdbseCommonSettings
  )
  .settings( // enable deployment staging with `sbt stage`
      Universal / mappings ++= {
          // copy the webapi/scripts folder
          directory("webapi/scripts")
      },

      // add dockerCommands used to create the image
      // docker:stage, docker:publishLocal, docker:publish, docker:clean

      dockerRepository := Some("dhlabbasel"),

      maintainer := "ivan.subotic@unibas.ch",

      Docker / dockerExposedPorts ++= Seq(7200),
      Docker / dockerCommands := Seq(
          // FIXME: Someday find out how to reference here Dependencies.Versions.gdbSEImage
          Cmd("FROM", "ontotext/graphdb:8.5.0-se"),
          Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
          Cmd("ADD", "opt/docker/scripts", "/scripts"),
          Cmd("RUN", "mkdir -p /graphdb && cp /scripts/KnoraRules.pie /graphdb/KnoraRules.pie && rm -rf /scripts"),
      )
  )

//////////////////////////////////////
// Knora's custom GraphDB-Free
//////////////////////////////////////

lazy val graphdbfreeCommonSettings = Seq(
    name := "knora-graphdb-free"
)

lazy val knoraGraphdbFree = knoraModule("knora-graphdb-free")
  .enablePlugins(DockerPlugin)
  .settings(
      graphdbfreeCommonSettings
  )
  .settings(
      Universal / mappings ++= {
          // copy the webapi/scripts folder
          directory("webapi/scripts")
      },

      // add dockerCommands used to create the image
      // docker:stage, docker:publishLocal, docker:publish, docker:clean

      dockerRepository := Some("dhlabbasel"),

      maintainer := "ivan.subotic@unibas.ch",

      Docker / dockerExposedPorts ++= Seq(7200),
      Docker / dockerCommands := Seq(
          // FIXME: Someday find out how to reference here Dependencies.Versions.gdbFreeImage
          Cmd("FROM", "dhlabbasel/graphdb:8.10.0-free"),
          Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
          Cmd("ADD", "opt/docker/scripts", "/scripts"),
          Cmd("RUN", "mkdir -p /graphdb && cp /scripts/KnoraRules.pie /graphdb/KnoraRules.pie && rm -rf /scripts"),
      )
  )

//////////////////////////////////////
// Knora's custom Sipi
//////////////////////////////////////

lazy val knoraSipiCommonSettings = Seq(
    name := "knora-sipi"
)

lazy val knoraSipi = knoraModule("knora-sipi")
  .enablePlugins(DockerPlugin)
  .settings(
      knoraSipiCommonSettings
  )
  .settings(
      Universal / mappings ++= {
          // copy the sipi/scripts folder
          directory("sipi/scripts")
      },

      // add dockerCommands used to create the image
      // docker:stage, docker:publishLocal, docker:publish, docker:clean

      dockerRepository := Some("dhlabbasel"),

      maintainer := "ivan.subotic@unibas.ch",

      Docker / dockerExposedPorts ++= Seq(1024),
      Docker / dockerCommands := Seq(
          // FIXME: Someday find out how to reference here Dependencies.Versions.sipiImage
          Cmd("FROM", "dhlabbasel/sipi:v2.0.1"),
          Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
          Cmd("ADD", "opt/docker/scripts", "/sipi/scripts"),
      )
  )


//////////////////////////////////////
// Knora's assets
//////////////////////////////////////

lazy val knoraAssetsCommonSettings = Seq(
    name := "knora-assets"
)

lazy val knoraAssets = knoraModule("knora-assets")
  .enablePlugins(DockerPlugin)
  .settings(
      knoraAssetsCommonSettings
  )
  .settings(
      Universal / mappings ++= {
          // copy the different folders
          directory("webapi/scripts") ++
          directory("webapi/_test_data") ++
          directory("webapi/_assets") ++
          directory("knora-ontologies")
      },

      // add dockerCommands used to create the image
      // docker:stage, docker:publishLocal, docker:publish, docker:clean

      dockerRepository := Some("dhlabbasel"),

      maintainer := "ivan.subotic@unibas.ch",
      Docker / dockerExposedPorts ++= Seq(9999), // not used. added just so that there is no warning
      Docker / dockerCommands := Seq(
          Cmd("FROM", "scratch"),
          Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
          Cmd("ADD", "opt/docker", "/assets")
      )
  )

//////////////////////////////////////
// Knora upgrade scripts
//////////////////////////////////////

lazy val knoraUpgradeCommonSettings = Seq(
    name := "knora-upgrade"
)

lazy val knoraUpgrade = knoraModule("knora-upgrade")
  .enablePlugins(DockerPlugin)
  .settings(
      knoraUpgradeCommonSettings
  )
  .settings(
      Universal / mappings ++= {
          // copy the different folders
          directory("upgrade")
      },

      // add dockerCommands used to create the image
      // docker:stage, docker:publishLocal, docker:publish, docker:clean

      dockerRepository := Some("dhlabbasel"),

      maintainer := "ivan.subotic@unibas.ch",
      Docker / dockerExposedPorts ++= Seq(9999), // not used. added just so that there is no warning
      Docker / dockerCommands := Seq(
          Cmd("FROM", "python:3.7-stretch"),
          Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
          Cmd("ADD", "opt/docker", "/"),
          Cmd("RUN", "pip install -r /upgrade/requirements.txt"),
          Cmd("ENTRYPOINT", "/upgrade/update-repository.py"),
          Cmd("CMD", "-h")
      )
  )

//////////////////////////////////////
// SALSAH1 (./salsah1)
//////////////////////////////////////

lazy val salsahCommonSettings = Seq(
    name := "salsah1"
)

lazy val salsah1 = knoraModule("salsah1")
        .enablePlugins(JavaAppPackaging, DockerPlugin, DockerComposePlugin)
        .configs(
            HeadlessTest
        )
        .settings(
            salsahCommonSettings,
            Revolver.settings
        )
        .settings(inConfig(HeadlessTest)(
            Defaults.testTasks ++ Seq(
                fork := true,
                javaOptions ++= javaHeadlessTestOptions,
                testOptions += Tests.Argument("-oDF") // show full stack traces and test case durations
            )
        ): _*)
        .settings(
            Dependencies.salsahLibraryDependencies,
            logLevel := Level.Info,
            run / fork := true,
            run / javaOptions ++= javaRunOptions,
            Compile / run / mainClass := Some("org.knora.salsah.Main"),
            Test / fork := true,
            Test / javaOptions ++= javaTestOptions,
            Test / parallelExecution := false,
            /* show full stack traces and test case durations */
            Test / testOptions += Tests.Argument("-oDF")
        )
        .settings( // enable deployment staging with `sbt stage`
            Universal / mappings ++= {
                // copy the public folder
                directory("salsah1/src/public") ++
                // copy the configuration files to config directory
                // contentOf("salsah1/configs").toMap.mapValues("config/" + _) ++
                // copy configuration files to config directory
                contentOf("salsah1/src/main/resources").toMap.mapValues("config/" + _)
            },
            // add 'config' directory first in the classpath of the start script,
            scriptClasspath := Seq("../config/") ++ scriptClasspath.value,
            // need this here, but why?
            Compile / mainClass := Some("org.knora.salsah.Main"),

            // add dockerCommands used to create the image
            // docker:stage, docker:publishLocal, docker:publish, docker:clean

            dockerRepository := Some("dhlabbasel"),

            maintainer := "ivan.subotic@unibas.ch",

            Docker / dockerExposedPorts ++= Seq(3335),
            Docker / dockerCommands := Seq(
                Cmd("FROM", "openjdk:10-jre-slim-sid"),
                Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),

                Cmd("ENV", """LANG="en_US.UTF-8""""),
                Cmd("ENV", """JAVA_OPTS="-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8""""),
                Cmd("ENV", "KNORA_SALSAH1_DEPLOYED=true"),
                Cmd("ENV", "KNORA_SALSAH1_WORKDIR=/salsah1"),

                Cmd("ADD", "opt/docker", "/salsah1"),
                Cmd("WORKDIR", "/salsah1"),

                Cmd("EXPOSE", "3335"),

                ExecCmd("ENTRYPOINT", "bin/salsah1"),
            ),


        )

lazy val javaRunOptions = Seq(
    // "-showversion",
    "-Xms256m",
    "-Xmx256m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
)

lazy val javaTestOptions = Seq(
    // "-showversion",
    "-Xms512m",
    "-Xmx512m"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)


lazy val HeadlessTest = config("headless") extend (Test)
lazy val javaHeadlessTestOptions = Seq(
    "-Dconfig.resource=headless-testing.conf"
) ++ javaTestOptions



//////////////////////////////////////
// WEBAPI (./webapi)
//////////////////////////////////////

import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import sbt._
import sbt.librarymanagement.Resolver

connectInput in run := true

lazy val webApiCommonSettings = Seq(
    name := "webapi"
)

// custom test and it settings
lazy val GDBSE = config("gdbse") extend Test
lazy val GDBSEIt = config("gdbse-it") extend IntegrationTest
lazy val GDBFree = config("gdbfree") extend Test
lazy val GDBFreeIt = config("gdbfree-it") extend IntegrationTest
lazy val FusekiTest = config("fuseki") extend Test
lazy val FusekiIt = config("fuseki-it") extend IntegrationTest
lazy val EmbeddedJenaTDBTest = config("tdb") extend Test

// GatlingPlugin - load testing
// JavaAgent - adds AspectJ Weaver configuration
// BuildInfoPlugin - allows generation of scala code with version information

lazy val webapi = knoraModule("webapi")
        .enablePlugins(SbtTwirl, JavaAppPackaging, DockerPlugin, GatlingPlugin, JavaAgent, RevolverPlugin, BuildInfoPlugin)
        .configs(
            IntegrationTest,
            Gatling,
            GatlingIt,
            GDBSE,
            GDBSEIt,
            GDBFree,
            GDBFreeIt,
            FusekiTest,
            FusekiIt,
            EmbeddedJenaTDBTest
        )
        .settings(
            webApiCommonSettings,
            resolvers ++= Seq(
                Resolver.bintrayRepo("hseeberger", "maven")
            ),
            Dependencies.webapiLibraryDependencies
        )
        .settings(
            inConfig(Test)(Defaults.testTasks ++ baseAssemblySettings),
            inConfig(IntegrationTest)(Defaults.testSettings),
            inConfig(Gatling)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GatlingIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBSE)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBSEIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBFree)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(GDBFreeIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(FusekiTest)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(FusekiIt)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value)),
            inConfig(EmbeddedJenaTDBTest)(Defaults.testTasks ++ Seq(forkOptions := Defaults.forkOptionsTask.value))
        )
        .settings(
            scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Yresolve-term-conflict:package"),

            logLevel := Level.Info,

            fork := true, // always fork

            run / javaOptions := webapiJavaRunOptions,

            reStart / javaOptions ++= resolvedJavaAgents.value map { resolved =>
                "-javaagent:" + resolved.artifact.absolutePath + resolved.agent.arguments
            }, // allows sbt-javaagent to work with sbt-revolver
            reStart / javaOptions ++= webapiJavaRunOptions,

            javaAgents += Dependencies.Compile.aspectJWeaver,

            Test / parallelExecution := false,
            Test / javaOptions ++= Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            // Test / javaOptions ++= Seq("-Dakka.log-config-on-start=on"), // prints out akka config
            // Test / javaOptions ++= Seq("-Dconfig.trace=loads"), // prints out config locations
            Test / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

            IntegrationTest / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            IntegrationTest / testOptions += Tests.Argument("-oDF"), // show full stack traces and test case durations

            Gatling / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            Gatling / testOptions := Seq(),
            GatlingIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            GatlingIt / testOptions := Seq(),

            GDBSE / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,
            GDBSEIt / javaOptions := Seq("-Dconfig.resource=graphdb-se.conf") ++ webapiJavaTestOptions,

            GDBFree / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ webapiJavaTestOptions,
            GDBFreeIt / javaOptions := Seq("-Dconfig.resource=graphdb-free.conf") ++ webapiJavaTestOptions,

            FusekiTest / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,
            FusekiIt / javaOptions := Seq("-Dconfig.resource=fuseki.conf") ++ webapiJavaTestOptions,

            EmbeddedJenaTDBTest / javaOptions := Seq("-Dconfig.resource=jenatdb.conf") ++ webapiJavaTestOptions,

            // disable publishing the jar produced by `sbt test:package` and `sbt it:package`
            Test / packageBin / publishArtifact := false,
            IntegrationTest / packageBin / publishArtifact := false
        )
        .settings(
            // prepare for publishing

            // Skip packageDoc task on stage
            Compile / packageDoc / mappings := Seq(),

            Universal / mappings ++= {
                // copy the scripts folder
                directory("webapi/scripts") ++
                  // add knora-ontologies
                  directory("knora-ontologies") ++
                  // add test-data directory
                  directory("webapi/_test_data") ++
                  // copy the configuration files to config directory
                  contentOf("webapi/configs").toMap.mapValues("config/" + _) ++
                  // copy configuration files to config directory
                  contentOf("webapi/src/main/resources").toMap.mapValues("config/" + _)
            },

            // add 'config' directory to the classpath of the start script,
            Universal / scriptClasspath := Seq("../config/") ++ scriptClasspath.value,

            // need this here, so that the Manifest inside the jars has the correct main class set.
            Compile / mainClass := Some("org.knora.webapi.Main"),
            Compile / run / mainClass := Some("org.knora.webapi.Main"),

            // add dockerCommands used to create the image
            // docker:stage, docker:publishLocal, docker:publish, docker:clean

            dockerRepository := Some("dhlabbasel"),

            maintainer := "ivan.subotic@unibas.ch",

            Docker / dockerExposedPorts ++= Seq(3333, 10001),
            Docker / dockerCommands := Seq(
                Cmd("FROM", "openjdk:10-jre-slim-sid"),
                Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
                // install wget
                Cmd("RUN", "apt-get -qq update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*"),
                // install yourkit profiler
                Cmd("RUN", "wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2018.04-docker.zip -P /tmp/ && unzip /tmp/YourKit-JavaProfiler-2018.04-docker.zip -d /usr/local && rm /tmp/YourKit-JavaProfiler-2018.04-docker.zip"),

                Cmd("ADD", "opt/docker", "/webapi"),
                Cmd("WORKDIR", "/webapi"),

                Cmd("EXPOSE", "3333"),
                Cmd("EXPOSE", "10001"),

                ExecCmd("ENTRYPOINT", "bin/webapi", "-J-agentpath:/usr/local/YourKit-JavaProfiler-2018.04/bin/linux-x86-64/libyjpagent.so=port=10001,listen=all"),
            )

        )
        .settings(
            buildInfoKeys ++= Seq[BuildInfoKey](
                name,
                version,
                "akkaHttp" -> Dependencies.akkaHttpVersion.value
            ),
            buildInfoPackage := "org.knora.webapi"
        )

lazy val webapiJavaRunOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G",
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500"
    "-Dcom.sun.management.jmxremote",
    // "-Dcom.sun.management.jmxremote.port=1617",
    "-Dcom.sun.management.jmxremote.authenticate=false",
    "-Dcom.sun.management.jmxremote.ssl=false",
    //"-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib"
)

lazy val webapiJavaTestOptions = Seq(
    // "-showversion",
    "-Xms1G",
    "-Xmx1G"
    // "-verbose:gc",
    //"-XX:+UseG1GC",
    //"-XX:MaxGCPauseMillis=500",
    //"-XX:MaxMetaspaceSize=4096m"
)

// packaging for runing normal tests
lazy val webapiTestPackage = project
  // we put the results  in a build folder
  .in(file("webapi/build/test"))
  .enablePlugins(JavaAppPackaging)
  .settings(
      Test / mainClass := Some("org.scalatest.tools.Runner"),
      // adds the test jar to mappings
      mappings in Universal += {
          // generates the test package
          val testjar = (packageBin in Test).value
          // maps this file to your lib folder in your output package
          testjar -> s"lib/${testjar.getName}"
      },
      scriptClasspath += (packageBin in Test).value.getName
  )
  .dependsOn(webapi)

// packaging for runing IT tests
lazy val webapiITPackage = project
  // we put the results in a build folder
  .in(file("webapi/build/it"))
  .enablePlugins(JavaAppPackaging)
  .configs(
      IntegrationTest
  )
  .settings(
      inConfig(IntegrationTest)(Defaults.testSettings),
  )
  .settings(
      IntegrationTest / mainClass := Some("org.scalatest.tools.Runner"),
      // adds the test jar to mappings
      mappings in Universal += {
          // generates the test package
          val testjar = (packageBin in IntegrationTest).value
          // maps this file to your lib folder in your output package
          testjar -> s"lib/${testjar.getName}"
      },
      scriptClasspath += (packageBin in Test).value.getName
  )
  .dependsOn(webapi)

def knoraModule(name: String): Project =
    Project(id = name, base = file(name))
            .settings(buildSettings)
