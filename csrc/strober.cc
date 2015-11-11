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
    uint64_t start_time = timestamp();
    do {
      assert(cycles() % get_trace_len() == 0);
      size_t stepped = 0;
      bool host_in_valid = false, host_in_ready, host_out_valid;
      do {
        if ((host_in_ready = peek_port(host_in_ready_id)) || !host_in_valid) {
          uint32_t host_in_bits;
          if ((host_in_valid = htif->recv_nonblocking(&host_in_bits, 2/*16/8*/))) {
            poke_port(host_in_bits_id,  host_in_bits);
            poke_port(host_in_valid_id, host_in_valid);
            step(1);
            stepped++;
          }
        }
        if ((host_out_valid = peek_port(host_out_valid_id))) {
          uint32_t htif_out_bits = peek_port(host_out_bits_id);
          htif->send(&htif_out_bits, 2/*16/8*/);
          poke_port(host_out_ready_id, true);
          step(1);
          stepped++;
        }
      } while ((host_in_ready && host_in_valid) || host_out_valid);
      poke_port(host_in_valid_id, false);
      poke_port(host_out_ready_id, false);
      step(get_trace_len()-stepped);
    } while (!htif->done() && cycles() <= max_cycles);
    uint64_t end_time = timestamp();
    double sim_time = (double) (end_time - start_time) / 1000000.0;
    double sim_speed = (double) cycles() / sim_time / 1000000.0;
    fprintf(stdout, "time elapsed: %.1f s, simulation speed = %.2f MHz\n", sim_time, sim_speed);
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
  return Top.run();
}
