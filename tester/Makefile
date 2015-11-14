defulat: compile

base_dir      = $(abspath ..)
tester_dir    = $(abspath .)
log_dir       = $(tester_dir)/logs
generated_dir = $(tester_dir)/generated-src
isa_dir       = $(base_dir)/riscv-tools/riscv-tests/isa
bmark_dir     = $(base_dir)/riscv-tools/riscv-tests/benchmarks
vcs_sim_rtl_dir    = $(tester_dir)/vcs-sim-rtl
vcs_sim_gl_syn_dir = $(tester_dir)/vcs-sim-gl-syn
vcs_sim_gl_par_dir = $(tester_dir)/vcs-sim-gl-par

#CONFIG      ?= DefaultFPGAConfig
CONFIG      ?= DefaultFPGASmallConfig
SIMCONFIG   ?= RocketSimConfig
NASTICONFIG ?= RocketNastiConfig

include $(base_dir)/Makefrag
ifneq ($(MAKECMDGOALS),clean)
-include $(generated_dir)/$(MODEL).$(CONFIG).d
endif

COMPILE_ARGS := --W0W --targetDir $(generated_dir) --genHarness --compile 
TEST_ARGS    := --W0W --targetDir $(generated_dir) --backend null --test
isa_hex = $(addprefix $(isa_dir)/, $(addsuffix .hex, $(rv64ui-v-asm-tests)  $(rv64ua-v-asm-tests) \
	$(rv64ui-p-asm-tests) $(rv64ui-pt-asm-tests) $(rv64um-pt-asm-tests) $(rv64uf-v-asm-tests) \
	$(rv64uf-p-asm-tests) $(rv64si-p-asm-tests)  $(rv64um-v-asm-tests)  $(rv64mi-p-asm-tests) \
	$(rv64ua-pt-asm-tests) $(rv64uf-pt-asm-tests)))
bmark_hex = $(addprefix $(bmark_dir)/, $(addsuffix .hex, $(basic-bmark-tests)))

include $(tester_dir)/Makefrag
include $(tester_dir)/Makefrag-vcs
include $(tester_dir)/Makefrag-sim
include $(tester_dir)/Makefrag-nasti
include $(tester_dir)/Makefrag-repl

compile: $(generated_dir)/$(MODEL).$(CONFIG).d

clean:
	rm -rf $(log_dir) $(generated_dir)
