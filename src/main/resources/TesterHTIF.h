#include "htif_emulator.h"

class TesterHTIF {
public:
  TesterHTIF(int argc, char** argv);
  ~TesterHTIF();
  void stop();
  bool done();
  int exit_code();
  
  void send(char* buf, unsigned size);
  void recv(char* buf, unsigned size);
  bool recv_nonblocking(char* buf, unsigned size);

private:
  htif_emulator_t* htif;
};
