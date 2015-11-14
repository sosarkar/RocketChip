default: simv

base_dir   = $(abspath ../../)
tester_dir = $(abspath ../)
sim_dir    = $(abspath .)
vlsi_dir   = $(base_dir)/vlsi
dc_dir     = $(vlsi_dir)/dc-syn
result_dir = $(dc_dir)/current-dc/results
report_dir = $(dc_dir)/current-dc/reports

CONFIG ?= DefaultFPGAConfig

include $(vlsi_dir)/Makefrag
include $(base_dir)/Makefrag
include $(base_dir)/vsim/Makefrag-verilog
include $(sim_dir)/Makefrag

simv: simv-$(CONFIG)

clean:
	rm -rf *.vpd simv* *.match
