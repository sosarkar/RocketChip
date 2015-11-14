default: simv

base_dir = $(abspath ../../)
vlsi_dir = $(base_dir)/vlsi
sim_dir  = .

CONFIG ?= DefaultFPGAConfig

include $(vlsi_dir)/Makefrag
include $(base_dir)/Makefrag
include $(base_dir)/vsim/Makefrag-verilog
include $(sim_dir)/Makefrag

simv: simv-$(CONFIG)

clean:
	rm -rf *.vpd simv*
