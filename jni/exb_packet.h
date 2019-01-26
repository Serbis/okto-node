#ifndef INCLUDE_EXB_PACKET_H_
#define INCLUDE_EXB_PACKET_H_

#include <stdint.h>

#define EXB_PREAMBLE_SIZE 8
#define EXB_HEADER_SIZE 7
#define EXB_PREAMBLE 0x3BAAAAAAAAAAAAAA
#define EXB_PREAMBLE_R 0xAAAAAAAAAAAAAA3B

#define EXB_TYPE_CMD 0
#define EXB_TYPE_RESPONSE 1
#define EXB_TYPE_ERR 2

typedef struct ExbPacket {
	uint64_t preamble;
	uint32_t tid;
	uint8_t type;
	uint16_t length;
	void *body;
} ExbPacket;

uint8_t* ExbPacket_toBinary(ExbPacket *packet, uint16_t *size);
void ExbPacket_parsePacketHeader(ExbPacket *packet, uint8_t *buffer, uint16_t size);

#endif
