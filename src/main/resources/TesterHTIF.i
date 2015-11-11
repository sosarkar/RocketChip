%module htif
%{
#include "TesterHTIF.h"
%}
%include "various.i"
%apply char* BYTE {char* buf}
%apply char** STRING_ARRAY {char **argv}

class TesterHTIF {
public:
  TesterHTIF(int argc, char** argv);
  ~TesterHTIF();
  bool done();
  int exit_code();
  
  void send(char* buf, unsigned size);
  void recv(char* buf, size_t size);
  bool recv_nonblocking(char* buf, unsigned size);

private:
  htif_pthread_t* htif;
};
