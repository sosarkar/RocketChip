// See LICENSE for license details.

package rocketchip

import Chisel._
import Chisel.AdvTester._
import htif._
import junctions.{MemReqCmd, MemData, MemResp}
import scala.collection.mutable.{Queue => ScalaQueue}

// Memory
case class TestMemReq(addr: Int, tag: BigInt, rw: Boolean) {
  override def toString = "[Mem Req] %s addr: %x, tag: %x".format(if (rw) "write" else "read", addr, tag)
}
case class TestMemData(data: BigInt) {
  override def toString = "[Mem Data] data: %x".format(data)
}
case class TestMemResp(data: BigInt, tag: BigInt) {
  override def toString = "[Mem Data] data: %x, tag: %x".format(data, tag)
}

abstract class SimMem(word_size: Int = 16, depth: Int = 1 << 20, verbose: Boolean = false) extends Processable {
  require(word_size % 4 == 0, "word_size should be divisible by 4")
  implicit def toBigInt(x: UInt) = x.litValue()
  private val addrMask = (1 << log2Up(depth))-1
  protected val off = log2Up(word_size)
  private val mem = Array.fill(depth){BigInt(0)}
  private def int(b: Byte) = (BigInt((b >>> 1) & 0x7f) << 1) | b & 0x1
  private def parseNibble(hex: Int) = if (hex >= 'a') hex - 'a' + 10 else hex - '0'

  def read(addr: Int) = {
    val data = mem(addr & addrMask)
    if (verbose) println("MEM[%x] => %x".format(addr & addrMask, data))
    data
  }

  def write(addr: Int, data: BigInt) {
    if (verbose) println("MEM[%x] <= %x".format(addr & addrMask, data))
    mem(addr & addrMask) = data
  }

  def loadMem(filename: String) {
    val lines = io.Source.fromFile(filename).getLines
    for ((line, i) <- lines.zipWithIndex) {
      val base = (i * line.length) / 2
      assert(base % word_size == 0)
      var offset = 0
      var data = BigInt(0)
      for (k <- (line.length - 2) to 0 by -2) {
        val shift = 8 * (offset % word_size)
        val byte = ((parseNibble(line(k)) << 4) | parseNibble(line(k+1))).toByte
        data |= int(byte) << shift
        if ((offset % word_size) == word_size - 1) {
          mem((base+offset)>>off) = data
          data = BigInt(0)
        }
        offset += 1
      }
    }
  }
}

class FastMem(cmdQ: ScalaQueue[TestMemReq], dataQ: ScalaQueue[TestMemData], respQ: ScalaQueue[TestMemResp],
    data_beats: Int = 1, word_size: Int = 16, depth: Int = 1 << 20) extends SimMem(word_size, depth) {
  private val line_size = data_beats*word_size
  private var store_inflight = false
  private var store_addr = 0
  private var store_count = 0
  def process {
    if (!dataQ.isEmpty && store_inflight) {
      val addr = store_addr + store_count*word_size
      val data = dataQ.dequeue.data
      write(addr >> off, data)
      store_count = (store_count + 1) % (data_beats)
      if (store_count == 0) {
        store_inflight = false
        cmdQ.dequeue
      }
    } else if (!cmdQ.isEmpty && cmdQ.front.rw && !store_inflight) {
      store_inflight = true
      store_addr = cmdQ.front.addr*line_size
    } else if (!cmdQ.isEmpty && !cmdQ.front.rw && !store_inflight) {
      val cmd  = cmdQ.dequeue
      val base = cmd.addr*line_size 
      (0 until data_beats) foreach {i => 
        val addr = base + i*word_size
        val resp = new TestMemResp(read(addr >> off), cmd.tag)
        respQ enqueue resp
      }
    }
  }
}

trait RocketTests extends AdvTests {
  System.loadLibrary("htif")
  class HTIFHandler(top: Top, htif: TesterHTIF) extends Processable {
    implicit def bigIntToBoolean(b: BigInt) = if (b == 0) false else true
    private val htif_bytes = top.io.host.in.bits.needWidth/8
    private var htif_in_valid = false
    private val htif_in_bits  = Array.fill(htif_bytes)(0.toByte)
    private val htif_out_bits = Array.fill(htif_bytes)(0.toByte)
    def process {
      import java.nio.ByteBuffer
      if (peek(top.io.host.clk_edge)) {
        if (peek(top.io.host.in.ready) || !htif_in_valid) {
          htif_in_valid = htif.recv_nonblocking(htif_in_bits, htif_bytes)
        }
        reg_poke(top.io.host.in.valid, int(htif_in_valid))
        reg_poke(top.io.host.in.bits,  int(ByteBuffer.wrap(htif_in_bits.reverse).getShort))
        if (peek(top.io.host.out.valid)) {
          val out_bits = peek(top.io.host.out.bits)
          (0 until htif_out_bits.size) foreach (htif_out_bits(_) = 0)
          out_bits.toByteArray.reverse.slice(0, htif_bytes).zipWithIndex foreach {
            case (bit, i) => htif_out_bits(i) = bit }
          htif.send(htif_out_bits, htif_bytes)
        }
        reg_poke(top.io.host.out.ready, 1)
      }
    }
  }

  abstract class Tests
  case object ISATests extends Tests
  case object Benchmarks extends Tests
  case object LoadMem extends Tests

  def parseOpts(args: Array[String]) = {
    var tests: Tests = Benchmarks
    var maxcycles = 1000000
    var loadmem = ""
    var verbose = false
    args foreach {
      case "+verbose" => verbose = true
      case "+isa"     => tests = ISATests
      case "+bmarks"  => tests = Benchmarks
      case arg if arg.slice(0, 9) == "+loadmem=" =>
        tests = LoadMem
        loadmem = arg.substring(9)
      case arg if arg.slice(0, 12) == "+max-cycles=" =>
        maxcycles = arg.substring(12).toInt
      case _ => // skip
    }
    (tests, maxcycles, verbose, loadmem)
  }

  def run(c: Top, htif: TesterHTIF, maxcycles: Int) = {
    reset(5)
    wire_poke(c.io.host.in.valid, 0)
    wire_poke(c.io.host.out.ready, 0)
    val startTime = System.nanoTime
    val ok = eventually(htif.done, maxcycles)
    val endTime = System.nanoTime
    val simTime = (endTime - startTime) / 1000000000.0
    val simSpeed = cycles / simTime
    val reason = if (cycles < maxcycles) "tohost = " + htif.exit_code else "timeout"
    println("*** %s *** (%s) after %d simulation cycles".format(
            if (ok) "PASSED" else "FAILED", reason, cycles))
    println("Time elapsed = %.1f s, Simulation Speed = %.2f Hz".format(simTime, simSpeed))
    ok
  }      
}

class RocketChipTester(c: Module, args: Array[String]) extends AdvTester(c) with RocketTests { 
  c match { 
    case top: Top =>
      val cmdHandler = new DecoupledSink(top.io.mem.req_cmd,
        (cmd: MemReqCmd) => new TestMemReq(peek(cmd.addr).toInt, peek(cmd.tag), peek(cmd.rw) != 0))
      val dataHandler = new DecoupledSink(top.io.mem.req_data,
        (data: MemData) => new TestMemData(peek(data.data)))
      val respHandler = new DecoupledSource(top.io.mem.resp,
        (resp: MemResp, in: TestMemResp) => {reg_poke(resp.data, in.data) ; reg_poke(resp.tag, in.tag)})
      val mem = new FastMem(cmdHandler.outputs, dataHandler.outputs, respHandler.inputs, 
                            top.mifDataBeats, top.io.mem.resp.bits.data.needWidth/8)
      val (tests, maxcycles, verbose, loadmem) = parseOpts(args)
      val suites = tests match {
        case ISATests   => TestGeneration.asmSuites.values
        case Benchmarks => TestGeneration.bmarkSuites.values
        case _ => Seq()
      }
      preprocessors += mem
      cmdHandler.max_count = 1
      cmdHandler.process()
      dataHandler.process()
      respHandler.process()
      
      def start(loadmem: String) {
        val htif = new TesterHTIF(0, Array[String]())
        val htifHandler = new HTIFHandler(top, htif) 
        preprocessors += htifHandler
        mem.loadMem(loadmem)
        cycles = 0
        ok &= run(top, htif, maxcycles)
        preprocessors -= htifHandler
      }

      if (tests == LoadMem) {
        println(s"LOADMEM: ${loadmem}")
        start(loadmem)
      } else for (suite <- suites ; test  <- suite.names) {
        val dir  = suite.dir stripPrefix "$(base_dir)/"
        val name = suite match {
          case t: AssemblyTestSuite  => s"${t.toolsPrefix}-${t.envName}-${test}"
          case t: BenchmarkTestSuite => s"${test}.riscv"
        }
        println(s"Test: ${name}")
        start(s"${dir}/${name}.hex")
        val dump = createOutputFile(s"${name}.out")
        dump write newTestOutputString
        dump.close
      }
    case _ => // Not Possible...
  }
}
