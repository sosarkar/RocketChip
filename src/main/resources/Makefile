default: all

htif      := TesterHTIF
wrapper   := $(htif)_wrap
java_dir  := $(BASE_DIR)/src/main/java
rsrc_dir  := $(BASE_DIR)/src/main/resources
gen_dir   := $(rsrc_dir)/generated
htif_obj  := $(addprefix $(gen_dir)/, $(addsuffix .o, $(htif) $(wrapper)))
fesvr_obj := $(addprefix $(gen_dir)/, $(addsuffix .o, htif htif_pthread context memif syscall device packet))
fesvr_dir := $(BASE_DIR)/riscv-tools/riscv-fesvr

SWIG      ?= swig
SWIGFLAGS := -c++ -java -includeall -package htif -outdir $(java_dir)
CXX       ?= g++
CXXFLAGS  := $(CXXFLAGS) -fPIC -I. -I$(JAVA_HOME)/include -I$(RISCV)/include -std=c++11

$(rsrc_dir)/$(wrapper).cc: $(rsrc_dir)/$(htif).i
	$(SWIG) $(SWIGFLAGS) -o $@ $<

$(htif_obj): $(gen_dir)/%.o: $(rsrc_dir)/%.cc
	mkdir -p $(gen_dir)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(gen_dir)/libfesvr.so: $(wildcard $(fesvr_dir)/fesvr/*.cc) $(wildcard $(fesvr_dir)/fesvr/*.h)
	mkdir -p $(gen_dir)
	cd $(gen_dir) && $(fesvr_dir)/configure --prefix=$(RISCV) && make

$(fesvr_obj): $(gen_dir)/libfesvr.so

$(rsrc_dir)/libhtif.so: $(htif_obj) $(fesvr_obj)
	$(CXX) $(LDFLAGS) -shared -o $@ $^

all: $(rsrc_dir)/libhtif.so
