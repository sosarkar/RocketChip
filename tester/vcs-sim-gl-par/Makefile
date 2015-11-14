default: simv

base_dir   = $(abspath ../..)
tester_dir = $(abspath ..)
sim_dir    = $(abspath .)
vlsi_dir   = $(base_dir)/vlsi
icc_dir    = $(vlsi_dir)/icc-par
result_dir = $(icc_dir)/current-icc/results
report_dir = $(icc_dir)/current-icc/reports

CONFIG ?= DefaultFPGAConfig

include $(vlsi_dir)/Makefrag
include $(base_dir)/Makefrag
include $(base_dir)/vsim/Makefrag-verilog
include $(sim_dir)/Makefrag

simv: simv-$(CONFIG)

clean:
	rm -rf *.vcd *.saif *.vpd simv* sdfAnnotateInfo *.match
