#include "TesterHTIF.h"

TesterHTIF::TesterHTIF(int argc, char** argv):
  htif(new htif_pthread_t(std::vector<std::string>(argv, argv+argc)))
{
}

TesterHTIF::~TesterHTIF() {
  delete htif;
}

bool TesterHTIF::done() {
  return htif->done();
}

int TesterHTIF::exit_code() {
  return htif->exit_code();
}

void TesterHTIF::send(char* buf, unsigned size) {
  htif->send((const void*)buf, size);
}

void TesterHTIF::recv(char* buf, unsigned size) {
  htif->recv((void*)buf, size);
}

bool TesterHTIF::recv_nonblocking(char* buf, unsigned size) {
  return htif->recv_nonblocking((void*)buf, size);
}
