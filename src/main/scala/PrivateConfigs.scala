package rocketchip

import Chisel._
import uncore._
import rocket._
import BOOM._
import DefaultTestSuites._
import strober._
import junctions._

class WithAllBooms extends ChiselConfig(
  (pname,site,here) => pname match { 
    case BuildTiles => {
      TestGeneration.addSuites(rv64i.map(_("p")))
      TestGeneration.addSuites((if(site(UseVM)) List("pt","v") else List("pt")).flatMap(env => rv64u.map(_(env))))
      TestGeneration.addSuites(if(site(NTiles) > 1) List(mtBmarks, bmarks) else List(bmarks))
      List.fill(site(NTiles)){ (r:Bool) => Module(new BOOMTile(resetSignal = r), {case TLId => "L1ToL2"}) }
    }
  }
)

class WithBoomAndRocketAlternating extends ChiselConfig(
  (pname,site,here) => pname match { 
    case BuildTiles => {
      TestGeneration.addSuites(rv64i.map(_("p")))
      TestGeneration.addSuites((if(site(UseVM)) List("pt","v") else List("pt")).flatMap(env => rv64u.map(_(env))))
      TestGeneration.addSuites(if(site(NTiles) > 1) List(mtBmarks, bmarks) else List(bmarks))
      (0 until site(NTiles)).map { i =>
        (r:Bool) => Module({
          if(i % 2 != 0) new RocketTile(resetSignal = r)
          else           new BOOMTile(resetSignal = r)
        }, {case TLId => "L1ToL2"})}}})

class SmallBOOMConfig  extends ChiselConfig(new WithSmallBOOMs  ++ new WithAllBooms ++ new DefaultBOOMConfig ++ new DefaultConfig)
class MediumBOOMConfig extends ChiselConfig(new WithMediumBOOMs ++ new WithAllBooms ++ new DefaultBOOMConfig ++ new DefaultL2Config)
class MegaBOOMConfig   extends ChiselConfig(new WithMegaBOOMs   ++ new WithAllBooms ++ new DefaultBOOMConfig ++ new DefaultL2Config)

class BOOMVLSIConfig extends ChiselConfig(new WithAllBooms ++ new DefaultBOOMConfig ++ new DefaultVLSIConfig ++ new WithNoBoomCounters) 
class BOOMFPGAConfig extends ChiselConfig(new WithAllBooms ++ new DefaultBOOMConfig ++ new DefaultFPGAConfig)
class BOOMCPPConfig extends  ChiselConfig(new WithAllBooms ++ new DefaultBOOMConfig ++ new DefaultCPPConfig)

class HeterogenousBoomConfig extends ChiselConfig(new WithBoomAndRocketAlternating ++ new BOOMFPGAConfig)

// Strober
class SimConfig extends ChiselConfig(
  (pname,site,here) => pname match {
    case SampleNum    => 30
    case TraceMaxLen  => 1024
    case DaisyWidth   => 32
    case ChannelLen   => 16
    case ChannelWidth => 32
  }
)

class NastiConfig extends ChiselConfig(
  topDefinitions = (pname,site,here) => pname match {
    case NASTIAddrBits => site(NASTIName) match {
      case "Master" => 32
      case "Slave"  => 32
    }
    case NASTIDataBits => site(NASTIName) match {
      case "Master" => 32
      case "Slave"  => 64
    }
    case NASTIIdBits => site(NASTIName) match {
      case "Master" => 12
      case "Slave"  => 6
    }
    case NASTIAddrSizeBits => 10

    case LineSize        => here(CacheBlockBytes)
    case MemAddrSizeBits => 28
    case MemMaxCycles    => 256
  }
)

// Strober
class RocketSimConfig extends ChiselConfig(new ExampleSmallConfig ++ new SimConfig)
class RocketNastiConfig extends ChiselConfig(new NastiConfig ++ new ExampleSmallConfig ++ new SimConfig)
class BOOMSimConfig extends ChiselConfig(new SmallBOOMConfig ++ new SimConfig)
class BOOMNastiConfig extends ChiselConfig(new NastiConfig ++ new SmallBOOMConfig ++ new SimConfig)
