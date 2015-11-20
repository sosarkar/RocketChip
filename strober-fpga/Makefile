default: driver 

base_dir      = $(abspath ..)
fpga_dir      = $(abspath .)
result_dir    = $(fpga_dir)/results
generated_dir = $(fpga_dir)/generated-src

BACKEND ?= fpga
CONFIG ?= RocketNastiConfig

include $(base_dir)/Makefrag

# Generate bitstream
board     ?= zc706
const     := $(MODEL)-const
fpga      := $(MODEL)-fpga
memgen    := $(base_dir)/fsim/fpga_mem_gen
board_dir := $(fpga_dir)/fpga-zynq/$(board)
bitstream := fpga-images-$(board)/boot.bin

$(generated_dir)/$(MODEL)-const.h:
	cd $(base_dir) && $(SBT) "project $(PROJECT)" "run NASTIShim $(CHISEL_ARGS) --configDump --noInlineMem"
	if [ -a $(generated_dir)/$(MODEL)NASTIShim.$(CONFIG).conf ]; then \
          $(memgen) $(generated_dir)/$(MODEL)NASTIShim.$(CONFIG).conf >> $(generated_dir)/$(MODEL)NASTIShim.$(CONFIG).v; \
        fi
	mkdir -p $(result_dir)
	cd $(generated_dir) ; cp $(MODEL).map $(MODEL).chain $(result_dir)
	cd $(generated_dir) ; mv $(MODEL)NASTIShim.$(CONFIG).v $(MODEL)NASTIShim.v

$(MODEL)-const: $(generated_dir)/$(MODEL)-const.h
	
$(MODEL)-fpga: $(generated_dir)/$(MODEL)-const.h
	cd $(board_dir); make clean; time make $(bitstream) DESIGN=$(MODEL); cp $(bitstream) $(result_dir)
	cp $(board_dir)/fpga-images-$(board)/boot_image/rocketchip_wrapper.bit $(result_dir)/strober_wrapper.bit 

# Compile driver
csrc_dir    := $(base_dir)/csrc
fesvr_dir   := $(base_dir)/riscv-tools/riscv-fesvr
driver_dir  := $(base_dir)/strober/csrc
driver_src  := $(wildcard $(driver_dir)/*.cc)
driver_obj  := $(addprefix $(generated_dir)/, $(notdir $(patsubst %.cc, %.o, $(driver_src))))
driver_host := arm-xilinx-linux-gnueabi
CXX         := $(driver_host)-g++ 
CXXFLAGS    := $(CXXFLAGS) -std=c++11 -Wall -I$(driver_dir) -include$(generated_dir)/$(MODEL)-const.h
LDFLAGS     := $(LDFLAGS) -Wl,-rpath,/usr/local/lib -L$(generated_dir) -lfesvr -lpthread

$(generated_dir)/libfesvr.so: $(wildcard $(fesvr_dir)/fesvr/*.cc) $(wildcard $(fesvr_dir)/fesvr/*.h)
	mkdir -p $(generated_dir) $(result_dir)
	cd $(generated_dir) && $(fesvr_dir)/configure --host=$(driver_host) && make

$(result_dir)/libfesvr.so: $(generated_dir)/libfesvr.so
	cp $(generated_dir)/libfesvr.so $@

$(driver_obj): $(generated_dir)/%.o: $(driver_dir)/%.cc $(MODEL)-const 
	$(CXX) $(CXXFLAGS) -c -o $@ $< 

$(generated_dir)/strober.o: $(csrc_dir)/strober.cc $(result_dir)/libfesvr.so $(MODEL)-const
	$(CXX) $(CXXFLAGS) -c -I$(csrc_dir) -I$(RISCV)/include -o $@ $<

$(MODEL)-zynq: $(driver_obj) $(generated_dir)/strober.o 
	$(CXX) $(LDFLAGS) -o$(result_dir)/$(notdir $@) $^

driver: $(MODEL)-zynq

clean:
	rm -rf $(generated_dir) $(result_dir)

.PHONY: $(MODEL)-const $(MODEL)-fpga
