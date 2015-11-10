base_dir      = $(abspath ..)
tester_dir    = $(abspath .)
log_dir       = $(tester_dir)/logs
generated_dir = $(tester_dir)/generated-src
isa_dir       = $(base_dir)/riscv-tools/riscv-tests/isa
bmark_dir     = $(base_dir)/riscv-tools/riscv-tests/benchmarks

CONFIG ?= ExampleSmallConfig

include $(base_dir)/Makefrag

COMMON_ARGS  := --W0W --configInstance $(PROJECT).$(CONFIG) --targetDir $(generated_dir) 
COMPILE_ARGS := $(COMMON_ARGS) --genHarness --compile 
TEST_ARGS    := $(COMMON_ARGS) --backend null --test

include $(tester_dir)/Makefrag
include $(tester_dir)/Makefrag-sim
include $(tester_dir)/Makefrag-nasti
include $(tester_dir)/Makefrag-repl

clean:
	rm -rf $(generated_dir)
