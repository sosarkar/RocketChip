#!/usr/bin/env python

import sys
import os.path
import re
import csv
import threading
from subprocess import Popen, call
import time

modules = list()
sample_pwr = dict()

def read_power_rpt(pt_dir, prefix, first):
  pwr_regex = re.compile(r"""
    \s*([\w_]*\s*(?:\([\w_]*\))?)\s*                 # Module name
    ([-\+]?[0-9]*\.?[0-9]+(?:[eE][-\+]?[0-9]+)?)\s+  # Int Power
    ([-\+]?[0-9]*\.?[0-9]+(?:[eE][-\+]?[0-9]+)?)\s+  # Switch Power
    ([-\+]?[0-9]*\.?[0-9]+(?:[eE][-\+]?[0-9]+)?)\s+  # Leakage Power
    ([-\+]?[0-9]*\.?[0-9]+(?:[eE][-\+]?[0-9]+)?)\s+  # Total Power
    ([0-9]+\.[0-9]+)                                 # Percent
    """, re.VERBOSE)
  path = pt_dir + "/current-pt/reports/" + prefix + ".power.avg.max.report"
  find = False
  for line in open(path):
    if not find:
      tokens = line.split()
      find = len(tokens) > 0 and tokens[0] == "Hierarchy"
    else:
      pwr_matched = pwr_regex.search(line)
      if pwr_matched:
        module     = pwr_matched.group(1)
        int_pwr    = pwr_matched.group(2)
        switch_pwr = pwr_matched.group(3)
        leak_pwr   = pwr_matched.group(4)
        total_pwr  = pwr_matched.group(5)
        percent    = pwr_matched.group(6)
        # print module, int_pwr, switch_pwr, leak_pwr, total_pwr, percent
        if module.find("clk_gate") < 0:
          if first:
            modules.append(module)
            sample_pwr[module] = list()
          sample_pwr[module].append(total_pwr)

if __name__ == '__main__':
  prefix = str(sys.argv[1]) if (len(sys.argv) > 1) else "Top"
  job_num = int(sys.argv[2]) if (len(sys.argv) > 2) else 6 

  """ split samples """
  samples = list()
  sample_size = 0
  print "split samples"
  with open("generated-src/" + prefix + ".sample") as f:
    for line in f:
      if line[0:8] == "0 cycle:":
        samples.append("")
        print "read sample #" + str(sample_size)
        sample_size += 1
      samples[-1] += line
  for i, sample in enumerate(samples[0:-1]):
    with open("generated-src/" + prefix  + "_" + str(i) + ".sample", "w") as f:
      f.write(sample)
  print "split done"
 
  """ clean up """
  for i in range(job_num):
    #call(["make", "-C", "pt-pwr-" + str(i), "clean"])
    call(["rm", os.path.abspath("vcs-sim-gl-par-" + str(i)) + "/*-pipe.vcd"])
 
  """ launch replays """
  if not os.path.exists("logs"):
    os.mkdirs("logs")
  for k in range((sample_size+job_num-1)/job_num+1):
    ps = list()
    logs = list()
    startTime = time.clock()
    for job in range(job_num):
      num = k*job_num + job
      sample  = os.path.abspath("generated-src/" + prefix + "_" + str(num) + ".sample")
      vcs_dir = os.path.abspath("vcs-sim-gl-par-" + str(job))
      pt_dir  = os.path.abspath("pt-pwr-" + str(job))
      if os.path.exists(sample):
        log = open("logs/" + prefix + "_" + str(num) + "_pwr.log", "w")
        print "[JOB #" + str(job) + "] runs gate-level simulation (pt_dir = " + pt_dir + ")"
        p = Popen(["make", "Top-replay-pwr", "SAMPLE=" + sample, "vcs_sim_gl_par_dir=" + vcs_dir, "pt_dir=" + pt_dir],
                 stdout=log, stderr=log) 
        ps.append(p)
        logs.append(log)
        time.sleep(20)

    while any(p.poll() == None for p in ps):
      pass
    
    for job in range(job_num):
      print "[JOB #" + str(job) + "] finishes gate-level simulation"
      num = k*job_num + job
      sample  = os.path.abspath("generated-src/" + prefix + "_" + str(num) + ".sample")
      if os.path.exists(sample):
        logs[job].close()
        pt_dir  = os.path.abspath("pt-pwr-" + str(job))
        read_power_rpt(pt_dir, prefix + "_" + str(num), num == 0)

    print "Loop " + str(k) + " done in " + str(time.clock() - startTime) + " secs" 

  """ dump power """
  with open(prefix + "-pwr.csv", "w") as csvfile:
     writer = csv.writer(csvfile)
     for m in modules:
       writer.writerow([m] + sample_pwr[m])
