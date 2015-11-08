base_dir      = $(abspath ..)
log_dir       = $(abspath ./logs)
isa_dir       = $(base_dir)/riscv-tools/riscv-tests/isa
bmark_dir     = $(base_dir)/riscv-tools/riscv-tests/benchmarks
generated_dir = $(abspath ./generated-src)

CONFIG ?= DefaultCPPConfig

include $(base_dir)/Makefrag
-include $(generated_dir)/$(MODEL).$(CONFIG).d

COMMON_ARGS  := --W0W --configInstance $(PROJECT).$(CONFIG) --targetDir $(generated_dir) 
COMPILE_ARGS := $(COMMON_ARGS) --genHarness --compile 
TEST_ARGS    := $(COMMON_ARGS) --backend null --test

$(MODEL)-cpp-compile: 
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run $(MODEL) $(COMPILE_ARGS) --compileInitializationUnoptimized --noIoDebug"

$(MODEL)-cpp-compile-debug:
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run $(MODEL) $(COMPILE_ARGS) --debug --vcd"

$(MODEL)-v-compile: 
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run $(MODEL) $(COMPILE_ARGS) --v"

$(MODEL)-isa-tests:
	mkdir -p $(log_dir)
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run $(MODEL) $(TEST_ARGS) +isa +max-cycles=$(timeout_cycles) --testCommand $(generated_dir)/$(MODEL).$(CONFIG)" \
	| tee $(log_dir)/$@.log

$(MODEL)-bmarks-tests:
	mkdir -p $(log_dir)
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run $(MODEL) $(TEST_ARGS) +bmarks +max-cycles=$(timeout_cycles) --testCommand $(generated_dir)/$(MODEL).$(CONFIG)" \
	| tee $(log_dir)/$@.log

$(addprefix $(MODEL)-, $(basic-bmark-tests)): $(MODEL)-%: $(bmark_dir)/%.hex
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run $(MODEL) $(TEST_ARGS) +loadmem=$< +max-cycles=$(timeout_cycles) --testCommand $(generated_dir)/$(MODEL).$(CONFIG) +vpdfile=$(generated_dir)/$*.vpd" \
	| tee $(log_dir)/$@.log

clean:
	rm -rf $(generated_dir)
