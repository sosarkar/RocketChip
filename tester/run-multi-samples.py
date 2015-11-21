#!/usr/bin/env python

import sys
import os.path
import re
import csv
import threading
from subprocess import call

modules = list()
sample_pwr = dict()

class ThreadClass(threading.Thread):
  def __init__(self, num, job):
    threading.Thread.__init__(self)
    self.num = num
    self.job = job
    self.modules = list()
    self.sample_pwr = dict()
    return

  def run(self):
    sample = os.path.abspath("generated-src/Top_" + str(self.num) + ".sample")
    pt_dir = os.path.abspath("pt-pwr-" + str(self.job))
    if os.path.isfile(sample):
      print "[JOB #" + str(self.job) + "] runs gate-level simulation (pt_dir = " + pt_dir + ")"
      call(["make", "Top-replay-pwr", "SAMPLE=" + sample, "pt_dir=" + pt_dir])
      print "[JOB #" + str(self.job) + "] finishes gate-level simulation"
      self.read_power_rpt(pt_dir, "Top_" + str(self.num))

  def read_power_rpt(self, pt_dir, prefix):
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
            self.modules.append(module)
            self.sample_pwr[module] = total_pwr

if __name__ == '__main__':
  job_num = 6 #int(sys.argv[1]) if sys.argv > 1 else 6

  """ split samples """
  samples = list()
  sample_size = 0
  print "split samples"
  with open("generated-src/Top.sample") as f:
    for line in f:
      if line[0:8] == "0 cycle:":
        samples.append("")
        print "read sample #" + str(sample_size)
        sample_size += 1
      samples[-1] += line
  for i, sample in enumerate(samples[0:-1]):
    with open("generated-src/Top_" + str(i) + ".sample", "w") as f:
      f.write(sample)
  print "split done"
  
  """ launch replays """
  first = True
  for k in range((sample_size+job_num)/job_num):
    threads = list()
    for i in range(job_num):
      t = ThreadClass(k*job_num+i, i)
      print "launch job #" + str(i)
      t.start()
      threads.append(t)

    for i, t in enumerate(threads): 
      t.join()
    
    for t in threads:
      for mod in t.modules:
        if not (mod in sample_pwr):
          modules.append(mod)
          sample_pwr[mod] = list()
        sample_pwr[mod].append(t.sample_pwr[mod])

  """ dump power """
  with open("Top-pwr.csv", "w") as csvfile:
     writer = csv.writer(csvfile)
     for m in modules:
       writer.writerow([m] + sample_pwr[m])
