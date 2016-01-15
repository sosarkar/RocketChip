// See LICENSE for license details.

package rocketchip

import Chisel._
import cde.{Parameters, Field}
import junctions._
import uncore._
import rocket._
import rocket.Util._

/** Top-level parameters of RocketChip, values set in e.g. PublicConfigs.scala */

/** Number of tiles */
case object NTiles extends Field[Int]
/** Number of memory channels */
case object NMemoryChannels extends Field[Int]
/** Number of banks per memory channel */
case object NBanksPerMemoryChannel extends Field[Int]
/** Least significant bit of address used for bank partitioning */
case object BankIdLSB extends Field[Int]
/** Number of outstanding memory requests */
case object NOutstandingMemReqsPerChannel extends Field[Int]
/** Whether to use the slow backup memory port [VLSI] */
case object UseBackupMemoryPort extends Field[Boolean]
/** Function for building some kind of coherence manager agent */
case object BuildL2CoherenceManager extends Field[(Int, Parameters) => CoherenceAgent]
/** Function for building some kind of tile connected to a reset signal */
case object BuildTiles extends Field[Seq[(Bool, Parameters) => Tile]]
/** Start address of the "io" region in the memory map */
case object ExternalIOStart extends Field[BigInt]
/** Enable DMA engine */
case object UseDma extends Field[Boolean]

case object UseStreamLoopback extends Field[Boolean]
case object StreamLoopbackSize extends Field[Int]
case object StreamLoopbackWidth extends Field[Int]

/** Utility trait for quick access to some relevant parameters */
trait HasTopLevelParameters {
  implicit val p: Parameters
  lazy val useDma = p(UseDma)
  lazy val nTiles = p(NTiles)
  lazy val nCachedTilePorts = p(TLKey("L1toL2")).nCachingClients
  lazy val nUncachedTilePorts =
    p(TLKey("L1toL2")).nCachelessClients - (if (useDma) 2 else 1)
  lazy val htifW = p(HtifKey).width
  lazy val csrAddrBits = 12
  lazy val nMemChannels = p(NMemoryChannels)
  lazy val nBanksPerMemChannel = p(NBanksPerMemoryChannel)
  lazy val nBanks = nMemChannels*nBanksPerMemChannel
  lazy val lsb = p(BankIdLSB)
  lazy val nMemReqs = p(NOutstandingMemReqsPerChannel)
  lazy val mifAddrBits = p(MIFAddrBits)
  lazy val mifDataBeats = p(MIFDataBeats)
  lazy val xLen = p(XLen)
  lazy val nSCR =  p(HtifKey).nSCR
  lazy val scrAddrBits = log2Up(nSCR)
  lazy val scrDataBits = 64
  lazy val scrDataBytes = scrDataBits / 8
  //require(lsb + log2Up(nBanks) < mifAddrBits)
}

class MemBackupCtrlIO extends Bundle {
  val en = Bool(INPUT)
  val in_valid = Bool(INPUT)
  val out_ready = Bool(INPUT)
  val out_valid = Bool(OUTPUT)
}

/** Top-level io for the chip */
class BasicTopIO(implicit val p: Parameters) extends ParameterizedBundle()(p)
    with HasTopLevelParameters {
  val host = new HostIO(htifW)
  val mem_backup_ctrl = new MemBackupCtrlIO
}

class TopIO(implicit p: Parameters) extends BasicTopIO()(p) {
  val mem = Vec(nMemChannels, new NastiIO)
}

object TopUtils {
  // Connect two Nasti interfaces with queues in-between
  def connectNasti(outer: NastiIO, inner: NastiIO)(implicit p: Parameters) {
    val mifDataBeats = p(MIFDataBeats)
    outer.ar <> Queue(inner.ar)
    outer.aw <> Queue(inner.aw)
    outer.w  <> Queue(inner.w, mifDataBeats)
    inner.r  <> Queue(outer.r, mifDataBeats)
    inner.b  <> Queue(outer.b)
  }
}

/** Top-level module for the chip */
//TODO: Remove this wrapper once multichannel DRAM controller is provided
class Top(topParams: Parameters) extends Module with HasTopLevelParameters {
  implicit val p = topParams
  val io = new TopIO

  // Build an Uncore and a set of Tiles
  val innerTLParams = p.alterPartial({case TLId => "L1toL2" })
  val uncore = Module(new Uncore()(innerTLParams))
  val tileList = uncore.io.htif zip p(BuildTiles) map { case(hl, bt) => bt(hl.reset, p) }

  // Connect each tile to the HTIF
  uncore.io.htif.zip(tileList).zipWithIndex.foreach {
    case ((hl, tile), i) =>
      tile.io.host.id := UInt(i)
      tile.io.host.reset := Reg(next=Reg(next=hl.reset))
      tile.io.host.csr.req <> Queue(hl.csr.req)
      hl.csr.resp <> Queue(tile.io.host.csr.resp)
      hl.debug_stats_csr := tile.io.host.debug_stats_csr
  }

  // Connect the uncore to the tile memory ports, HostIO and MemIO
  uncore.io.tiles_cached <> tileList.map(_.io.cached).flatten
  uncore.io.tiles_uncached <> tileList.map(_.io.uncached).flatten
  io.host <> uncore.io.host
  if (p(UseBackupMemoryPort)) { io.mem_backup_ctrl <> uncore.io.mem_backup_ctrl }

  io.mem.zip(uncore.io.mem).foreach { case (outer, inner) =>
    TopUtils.connectNasti(outer, inner)
    // Memory cache type should be normal non-cacheable bufferable
    outer.ar.bits.cache := UInt("b0011")
    outer.aw.bits.cache := UInt("b0011")
  }

  // tie off the mmio port
  val errslave = Module(new NastiErrorSlave)
  errslave.io <> uncore.io.mmio
}

/** Wrapper around everything that isn't a Tile.
  *
  * Usually this is clocked and/or place-and-routed separately from the Tiles.
  * Contains the Host-Target InterFace module (HTIF).
  */
class Uncore(implicit val p: Parameters) extends Module
    with HasTopLevelParameters {
  val io = new Bundle {
    val host = new HostIO(htifW)
    val mem = Vec(nMemChannels, new NastiIO)
    val tiles_cached = Vec(nCachedTilePorts, new ClientTileLinkIO).flip
    val tiles_uncached = Vec(nUncachedTilePorts, new ClientUncachedTileLinkIO).flip
    val htif = Vec(nTiles, new HtifIO).flip
    val mem_backup_ctrl = new MemBackupCtrlIO
    val mmio = new NastiIO
  }

  val htif = Module(new Htif(CSRs.mreset)) // One HTIF module per chip
  val outmemsys = Module(new OuterMemorySystem) // NoC, LLC and SerDes
  outmemsys.io.incoherent := htif.io.cpu.map(_.reset)
  outmemsys.io.htif_uncached <> htif.io.mem
  outmemsys.io.tiles_uncached <> io.tiles_uncached
  outmemsys.io.tiles_cached <> io.tiles_cached

  for (i <- 0 until nTiles) {
    io.htif(i).reset := htif.io.cpu(i).reset
    io.htif(i).id := htif.io.cpu(i).id
    htif.io.cpu(i).debug_stats_csr <> io.htif(i).debug_stats_csr

    val csr_arb = Module(new SmiArbiter(2, xLen, csrAddrBits))
    csr_arb.io.in(0) <> htif.io.cpu(i).csr
    csr_arb.io.in(1) <> outmemsys.io.csr(i)
    io.htif(i).csr <> csr_arb.io.out
  }

  // Arbitrate SCR access between MMIO and HTIF
  val scrFile = Module(new SCRFile)
  val scrArb = Module(new SmiArbiter(2, scrDataBits, scrAddrBits))
  scrArb.io.in(0) <> htif.io.scr
  scrArb.io.in(1) <> outmemsys.io.scr
  scrFile.io.smi <> scrArb.io.out
  // scrFile.io.scr <> (... your SCR connections ...)

  val deviceTree = Module(new NastiROM(p(DeviceTree).toSeq))
  deviceTree.io <> outmemsys.io.deviceTree

  // Wire the htif to the memory port(s) and host interface
  io.host.debug_stats_csr := htif.io.host.debug_stats_csr
  io.mem <> outmemsys.io.mem
  io.mmio <> outmemsys.io.mmio
  if(p(UseBackupMemoryPort)) {
    outmemsys.io.mem_backup_en := io.mem_backup_ctrl.en
    VLSIUtils.padOutHTIFWithDividedClock(htif.io.host, scrFile.io.scr,
      outmemsys.io.mem_backup, io.mem_backup_ctrl, io.host, htifW)
  } else {
    htif.io.host.out <> io.host.out
    htif.io.host.in <> io.host.in
  }
}

/** The whole outer memory hierarchy, including a NoC, some kind of coherence
  * manager agent, and a converter from TileLink to MemIO.
  */ 
class OuterMemorySystem(implicit val p: Parameters) extends Module with HasTopLevelParameters {
  val io = new Bundle {
    val tiles_cached = Vec(nCachedTilePorts, new ClientTileLinkIO).flip
    val tiles_uncached = Vec(nUncachedTilePorts, new ClientUncachedTileLinkIO).flip
    val htif_uncached = (new ClientUncachedTileLinkIO).flip
    val incoherent = Vec(nTiles, Bool()).asInput
    val mem = Vec(nMemChannels, new NastiIO)
    val mem_backup = new MemSerializedIO(htifW)
    val mem_backup_en = Bool(INPUT)
    val csr = Vec(nTiles, new SmiIO(xLen, csrAddrBits))
    val scr = new SmiIO(xLen, scrAddrBits)
    val mmio = new NastiIO
    val deviceTree = new NastiIO
  }

  val dmaOpt = if (p(UseDma)) Some(Module(new DmaEngine)) else None
  val mmioBase = p(MMIOBase)

  // Create a simple L1toL2 NoC between the tiles+htif and the banks of outer memory
  // Cached ports are first in client list, making sharerToClientId just an indentity function
  // addrToBank is sed to hash physical addresses (of cache blocks) to banks (and thereby memory channels)
  val ordered_clients = (io.tiles_cached ++
    (io.tiles_uncached ++ dmaOpt.map(_.io.inner) :+ io.htif_uncached)
      .map(TileLinkIOWrapper(_))) 
  def sharerToClientId(sharerId: UInt) = sharerId
  def addrToBank(addr: Bits): UInt = {
    Mux(addr.toUInt < UInt(mmioBase >> log2Up(p(CacheBlockBytes))),
      if (nBanks > 1) addr(lsb + log2Up(nBanks) - 1, lsb) else UInt(0),
      UInt(nBanks))
  }
  val preBuffering = TileLinkDepths(2,2,2,2,2)
  val postBuffering = TileLinkDepths(0,0,1,0,0) //TODO: had EOS24 crit path on inner.release
  val l1tol2net = Module(new RocketChipTileLinkCrossbar(addrToBank, sharerToClientId, preBuffering, postBuffering))

  // Create point(s) of coherence serialization
  val managerEndpoints = List.tabulate(nBanks){id => p(BuildL2CoherenceManager)(id, p)}
  managerEndpoints.foreach { _.incoherent := io.incoherent }

  val mmioManager = Module(new MMIOTileLinkManager()(p.alterPartial({
    case TLId => "L1toL2"
    case InnerTLId => "L1toL2"
    case OuterTLId => "L2toMC"
  })))

  // Wire the tiles and htif to the TileLink client ports of the L1toL2 network,
  // and coherence manager(s) to the other side
  l1tol2net.io.clients <> ordered_clients
  l1tol2net.io.managers <> managerEndpoints.map(_.innerTL) :+ mmioManager.io.inner

  // Create a converter between TileLinkIO and MemIO for each channel
  val outerTLParams = p.alterPartial({ case TLId => "L2toMC" })
  val outermostTLParams = p.alterPartial({case TLId => "Outermost"})
  val backendBuffering = TileLinkDepths(0,0,0,0,0)

  val addrMap = p(GlobalAddrMap)
  val addrHashMap = new AddrHashMap(addrMap, mmioBase)
  val nMasters = (if (dmaOpt.isEmpty) 2 else 3)
  val nSlaves = addrHashMap.nEntries

  println("Generated Address Map")
  for ((name, base, size, _) <- addrHashMap.sortedEntries) {
    println(f"\t$name%s $base%x - ${base + size - 1}%x")
  }

  val mmio_ic = Module(new NastiRecursiveInterconnect(nMasters, nSlaves, addrMap, mmioBase))
  val mem_ic = Module(new NastiMemoryInterconnect(nBanksPerMemChannel, nMemChannels))

  for ((bank, i) <- managerEndpoints.zipWithIndex) {
    val unwrap = Module(new ClientTileLinkIOUnwrapper()(outerTLParams))
    val narrow = Module(new TileLinkIONarrower("L2toMC", "Outermost"))
    val conv = Module(new NastiIOTileLinkIOConverter()(outermostTLParams))
    unwrap.io.in <> ClientTileLinkEnqueuer(bank.outerTL, backendBuffering)(outerTLParams)
    narrow.io.in <> unwrap.io.out
    conv.io.tl <> narrow.io.out
    TopUtils.connectNasti(mem_ic.io.masters(i), conv.io.nasti)
  }

  val mmio_narrow = Module(new TileLinkIONarrower("L2toMC", "Outermost"))
  val mmio_conv = Module(new NastiIOTileLinkIOConverter()(outermostTLParams))
  mmio_narrow.io.in <> mmioManager.io.outer
  mmio_conv.io.tl <> mmio_narrow.io.out
  TopUtils.connectNasti(mmio_ic.io.masters(0), mmio_conv.io.nasti)

  val rtc = Module(new RTC(CSRs.mtime))
  mmio_ic.io.masters(1) <> rtc.io

  dmaOpt.foreach { dma =>
    mmio_ic.io.masters(2) <> dma.io.outer
    dma.io.ctrl <> mmio_ic.io.slaves(addrHashMap("devices:dma").port)
  }

  for (i <- 0 until nTiles) {
    val csrName = s"conf:csr$i"
    val csrPort = addrHashMap(csrName).port
    val conv = Module(new SmiIONastiIOConverter(xLen, csrAddrBits))
    conv.io.nasti <> mmio_ic.io.slaves(csrPort)
    io.csr(i) <> conv.io.smi
  }

  val scr_conv = Module(new SmiIONastiIOConverter(scrDataBits, scrAddrBits))
  scr_conv.io.nasti <> mmio_ic.io.slaves(addrHashMap("conf:scr").port)
  io.scr <> scr_conv.io.smi

  if (p(UseStreamLoopback)) {
    val lo_width = p(StreamLoopbackWidth)
    val lo_size = p(StreamLoopbackSize)
    val lo_conv = Module(new NastiIOStreamIOConverter(lo_width))
    lo_conv.io.nasti <> mmio_ic.io.slaves(addrHashMap("devices:loopback").port)
    lo_conv.io.stream.in <> Queue(lo_conv.io.stream.out, lo_size)
  }

  io.mmio <> mmio_ic.io.slaves(addrHashMap("io").port)
  io.deviceTree <> mmio_ic.io.slaves(addrHashMap("conf:devicetree").port)

  val mem_channels = mem_ic.io.slaves
  // Create a SerDes for backup memory port
  if(p(UseBackupMemoryPort)) {
    VLSIUtils.doOuterMemorySystemSerdes(
      mem_channels, io.mem, io.mem_backup, io.mem_backup_en,
      nMemChannels, htifW, p(CacheBlockOffsetBits))
  } else { io.mem <> mem_channels }
}
