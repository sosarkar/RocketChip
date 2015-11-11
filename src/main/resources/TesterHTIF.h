#include <fesvr/htif_pthread.h>

class TesterHTIF {
public:
  TesterHTIF(int argc, char** argv);
  ~TesterHTIF();
  bool done();
  int exit_code();
  
  void send(char* buf, unsigned size);
  void recv(char* buf, unsigned size);
  bool recv_nonblocking(char* buf, unsigned size);

private:
  htif_pthread_t* htif;
};
