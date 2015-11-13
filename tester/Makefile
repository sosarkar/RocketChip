base_dir      = $(abspath ..)
tester_dir    = $(abspath .)
log_dir       = $(tester_dir)/logs
generated_dir = $(tester_dir)/generated-src
isa_dir       = $(base_dir)/riscv-tools/riscv-tests/isa
bmark_dir     = $(base_dir)/riscv-tools/riscv-tests/benchmarks

CONFIG      ?= DefaultFPGAConfig
#CONFIG      ?= DefaultFPGASmallConfig
SIMCONFIG   ?= RocketSimConfig
NASTICONFIG ?= RocketNastiConfig

include $(base_dir)/Makefrag

COMPILE_ARGS := --W0W --targetDir $(generated_dir) --genHarness --compile 
TEST_ARGS    := --W0W --targetDir $(generated_dir) --backend null --test

include $(tester_dir)/Makefrag
include $(tester_dir)/Makefrag-sim
include $(tester_dir)/Makefrag-nasti
include $(tester_dir)/Makefrag-repl

clean:
	rm -rf $(log_dir) $(generated_dir)
