/**
 * patcher — tiny root memory patcher
 * Usage: patcher <pid> <hex_address> <hex_bytes>
 * Example: patcher 12345 6FD2079ABC 1F2003D51F2003D5
 */
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>

static int hex_to_bytes(const char* hex, uint8_t* out, int maxlen) {
    int n = 0;
    while (*hex && *(hex+1) && n < maxlen) {
        char tmp[3] = {hex[0], hex[1], 0};
        out[n++] = (uint8_t)strtol(tmp, NULL, 16);
        hex += 2;
    }
    return n;
}

int main(int argc, char** argv) {
    if (argc < 4) {
        fprintf(stderr, "usage: patcher <pid> <hex_addr> <hex_bytes>\n");
        return 1;
    }

    pid_t  pid  = (pid_t)atoi(argv[1]);
    off64_t addr = (off64_t)strtoll(argv[2], NULL, 16);

    uint8_t bytes[512];
    int     n = hex_to_bytes(argv[3], bytes, sizeof(bytes));
    if (n <= 0) { fprintf(stderr, "bad hex bytes\n"); return 1; }

    char mempath[64];
    snprintf(mempath, sizeof(mempath), "/proc/%d/mem", pid);

    int fd = open(mempath, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "open %s: %s\n", mempath, strerror(errno));
        return 1;
    }

    ssize_t written = pwrite64(fd, bytes, n, addr);
    close(fd);

    if (written != n) {
        fprintf(stderr, "pwrite64: wrote %zd of %d bytes at 0x%llx: %s\n",
                written, n, (unsigned long long)addr, strerror(errno));
        return 1;
    }
    printf("ok: %d bytes at 0x%llx\n", n, (unsigned long long)addr);
    return 0;
}
