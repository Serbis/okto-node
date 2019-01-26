#include <stdlib.h>
#include <string.h>
#include "exb_packet.h"

uint8_t* ExbPacket_toBinary(ExbPacket *packet, uint16_t *size) {
		uint8_t *bin = (uint8_t*) malloc((size_t) (29 + packet->length - 1));

	    *(bin + 0) = (EXB_PREAMBLE & 0xff00000000000000) >> 56; //PREAMBLE
	    *(bin + 1) = (EXB_PREAMBLE & 0x00ff000000000000) >> 48;
	    *(bin + 2) = (EXB_PREAMBLE & 0x0000ff0000000000) >> 40;
	    *(bin + 3) = (EXB_PREAMBLE & 0x000000ff00000000) >> 32;
	    *(bin + 4) = (EXB_PREAMBLE & 0x00000000ff000000) >> 24;
	    *(bin + 5) = (EXB_PREAMBLE & 0x0000000000ff0000) >> 16;
	    *(bin + 6) = (EXB_PREAMBLE & 0x000000000000ff00) >> 8;
	    *(bin + 7) = (EXB_PREAMBLE & 0x00000000000000ff) >> 0;


	    *(bin + 8) = (packet->tid & 0xff000000) >> 24; //TID
	    *(bin + 9) = (packet->tid & 0x00ff0000) >> 16;
	    *(bin + 10) = (packet->tid & 0x0000ff00) >> 8;
	    *(bin + 11) = packet->tid & 0x000000ff;

	    *(bin + 12) = packet->type; //TYPE

	    *(bin + 13) = (packet->length & 0xff00) >> 8; //LENGTH
	    *(bin + 14) = packet->length & 0x00ff;

	    memcpy(bin + 15, packet->body, packet->length);

	    *size = 15 + packet->length;

	    return bin;
}

void ExbPacket_parsePacketHeader(ExbPacket *packet, uint8_t *buffer, uint16_t size) {
	 	packet->preamble = EXB_PREAMBLE; //PREAMBLE
	    packet->tid = *(buffer + 3) << 0 | *(buffer + 2) << 8 | *(buffer + 1) << 16 | *(buffer + 0) << 25; //TID
	    packet->type = *(buffer + 4); //TYPE
	    packet->length = *(buffer + 6) << 0 | *(buffer + 5) << 8; //LENGTH*/
}
