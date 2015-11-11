#include <fesvr/htif_pthread.h>
#include "simif_zynq.h"

class Top_t: simif_zynq_t {
public:
  Top_t(std::vector<std::string> args): simif_zynq_t(args, "Top", false), htif(new htif_pthread_t(args)) {
    max_cycles = -1;
    for (auto &arg: args) {
      if (arg.find("+max-cycles=") == 0) {
        max_cycles = atoi(arg.c_str()+12);
      }
    }
  }
  ~Top_t() {
    delete htif;
  }
  
  int run(size_t trace_len = TRACE_MAX_LEN) {
    set_trace_len(trace_len);
    set_mem_cycles(100);
    size_t host_in_bits_id   = get_in_id("Top.io_host_in_bits");
    size_t host_in_valid_id  = get_in_id("Top.io_host_in_valid");
    size_t host_in_ready_id  = get_out_id("Top.io_host_in_ready");
    size_t host_out_bits_id  = get_out_id("Top.io_host_out_bits");
    size_t host_out_valid_id = get_out_id("Top.io_host_out_valid");
    size_t host_out_ready_id = get_in_id("Top.io_host_out_ready");
    do {
      step(1);
      static bool htif_in_valid = false;
      static uint32_t htif_in_bits;
      if (peek_port(host_in_ready_id) || !htif_in_valid) {
        htif_in_valid = htif->recv_nonblocking(&htif_in_bits, 2/*16/8*/);
      }
      poke_port(host_in_bits_id,  htif_in_bits);
      poke_port(host_in_valid_id, htif_in_valid);
      if (peek_port(host_out_valid_id)) {
        uint32_t htif_out_bits = peek_port(host_out_bits_id);
        htif->send(&htif_out_bits, 2/*16/8*/);
      }
      poke_port(host_out_ready_id, true);
    } while (!htif->done() && cycles() <= max_cycles);

    int exitcode = htif->exit_code();
    if (exitcode) {
      fprintf(stdout, "*** FAILED *** (code = %d) after %llu cycles\n", htif->exit_code(), cycles());
    } else if (cycles() > max_cycles) {
      fprintf(stdout, "*** FAILED *** (timeout) after %llu cycles\n", cycles());
    } else {
      fprintf(stdout, "*** PASSED *** after %llu cycles\n", cycles());
    }
    return exitcode;
  }
  
private:
  htif_pthread_t* htif;
  uint64_t max_cycles;
};

int main(int argc, char** argv) {
  std::vector<std::string> args(argv + 1, argv + argc);
  Top_t Top(args);
  return Top.run(128);
}
