import sbt._
import Keys._
import complete._
import complete.DefaultParsers._

object BuildSettings extends Build {

  override lazy val settings = super.settings ++ Seq(
    organization := "berkeley",
    version      := "1.2",
    scalaVersion := "2.11.6",
    parallelExecution in Global := false,
    traceLevel   := 15,
    scalacOptions ++= Seq("-deprecation","-unchecked"),
    libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  )

  lazy val chisel    = project
  lazy val hardfloat = project.dependsOn(chisel)
  lazy val junctions = project.dependsOn(chisel)
  lazy val uncore    = project.dependsOn(junctions)
  lazy val rocket    = project.dependsOn(hardfloat,uncore)
  lazy val zscale    = project.dependsOn(rocket)
  lazy val boom      = project.dependsOn(rocket)
  lazy val strober   = project.dependsOn(junctions)
  lazy val rocketchip = (project in file(".")).settings(chipSettings).dependsOn(zscale,boom,strober)

  lazy val addons = settingKey[Seq[String]]("list of addons used for this build")
  lazy val htif = settingKey[Unit]("compile TesterHTIF")
  lazy val make = inputKey[Unit]("trigger backend-specific makefile command")
  val setMake = NotSpace ~ ( Space ~> NotSpace )
  
  val chipSettings = Seq(
    addons := {
      val a = sys.env.getOrElse("ROCKETCHIP_ADDONS", "")
      println(s"Using addons: $a")
      a.split(",")
    },
    htif := {
      import scala.util.Properties.envOrElse
      val htif_mod  = "TesterHTIF"
      val src_dir   = "src/main/resources"
      val java_dir  = "src/main/java"
      val fesvr_dir = "riscv-tools/riscv-fesvr/fesvr"
      val CXX       = envOrElse("CXX", "g++")
      val CXXFLAGS  = envOrElse("CXXFLAGS", "") 
      val RISCV     = envOrElse("RISCV", "")
      val JAVA_HOME = envOrElse("JAVA_HOME", "")
      val htif_srcs  = List(htif_mod, s"${htif_mod}_wrap")
      val fesvr_srcs = List("htif", "htif_pthread", "context", "memif", "syscall", "device", "packet")
      println("compile TesterHTIF:")
      List("swig", "-c++", "-java", "-includeall", "-package", "htif", "-outdir", java_dir, 
           "-o", s"${src_dir}/${htif_mod}_wrap.cc", s"${src_dir}/${htif_mod}.i").mkString(" ").!
      htif_srcs foreach (src => List(CXX, CXXFLAGS, "-c", "-fPIC", "-std=c++11", 
        s"-I${src_dir}", "-Icsrc", s"-I${RISCV}/include", s"-I${JAVA_HOME}/include",
        "-o", s"${src_dir}/${src}.o", s"${src_dir}/${src}.cc").mkString(" ").!)
      // assume riscv-fesvr is compiled
      (List(CXX, "-shared", "-o", s"${src_dir}/libhtif.so") ++
       (htif_srcs map (src => s"${src_dir}/${src}.o")) ++ 
       (fesvr_srcs map (src => s"${fesvr_dir}/${src}.o"))).mkString(" ").!
    },
    unmanagedSourceDirectories in Compile ++= addons.value.map(baseDirectory.value / _ / "src/main/scala"),
    mainClass in (Compile, run) := Some("rocketchip.TestGenerator"),
    make := {
      val jobs = java.lang.Runtime.getRuntime.availableProcessors
      val (makeDir, target) = setMake.parsed
      (run in Compile).evaluated
      s"make -C $makeDir  -j $jobs $target" !
    }
  )
}
